package com.mall.admin.service;

import com.mall.admin.config.AdminProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;
import java.time.Duration;

/**
 * 图形验证码。
 * <p>
 * 前端登录表单把 captcha 设成了必填（Login.tsx 的表单校验），所以这个能力不能省略。
 * <p>
 * 刻意不引第三方验证码库：需求只是"生成一张有几个字符、带点干扰的图"，
 * 用 JDK 自带的 Graphics2D 二十行就够，引一个库反而多一个要跟安全更新的依赖。
 *
 * <h3>【2026-09-09】从进程内存改成 Redis</h3>
 * 原实现用 {@code ConcurrentHashMap} 存答案，类注释里写着"当前单副本，
 * 要扩副本必须先换 Redis"。<b>而它其实早就不是单副本了</b>：
 * mall-admin 从 2026-09-01 起 {@code replicaCount=2}，一直到 2026-09-09。
 *
 * <p>那八天里后台登录大约有一半概率失败。查证过失败是必然的，不是偶发：
 * <ul>
 *   <li>{@code kubectl get svc mall-admin -o jsonpath='{.spec.sessionAffinity}'} = {@code None}</li>
 *   <li>Ingress 上没有任何 sticky/affinity 注解</li>
 *   <li>验证码走网关的 {@code admin_captcha_route} -> {@code lb://mall-admin}，轮询</li>
 * </ul>
 * 取验证码图和提交登录是<b>两个独立请求</b>，各自独立负载均衡，
 * 所以答案写在 A 实例、校验发生在 B 实例是常态。
 * 症状是"验证码错误"—— 没有人会往副本数上想，这正是原注释担心的那种 bug。
 *
 * <p>放 Redis 之后 mall-admin 可以安全扩副本，这也是给 HPA 扫清的前置条件。
 *
 * <h3>为什么用 GETDEL 而不是 GET + DEL</h3>
 * 验证码<b>必须一次性</b>，否则同一个 uuid 能被反复用来撞用户名密码，
 * 等于验证码形同虚设。原实现用 {@code map.remove(uuid)}，天然是原子的取-并-删。
 * Redis 侧的等价物是 {@code GETDEL}（Redis 6.2+，本项目跑 redis:7-alpine）：
 * 拆成 GET 再 DEL 会留一个窗口，两个并发请求可能都拿到同一个答案。
 *
 * <h3>Redis 挂了会怎样：登录失败，这是有意的</h3>
 * 验证码是安全控制，拿不到答案时<b>只能拒绝</b>。降级成"放行"等于在
 * 依赖故障时把暴力破解的门打开，比登不上去糟得多。
 */
@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final int LENGTH = 4;
    /** 去掉了容易看错的 0/O/1/I/l 等字符 */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private static final String KEY_PREFIX = "mall:admin:captcha:";
    /**
     * uuid 由客户端生成，所以它是<b>不可信输入</b>，必须限形。
     * <p>
     * 不限的话，攻击者可以用任意长的字符串当 key 反复请求验证码，在 Redis 里
     * 塞进大量条目（原来的 Map 实现同样有这个问题，而且更糟 —— 那是堆内存，
     * 只在下一次 create 时才顺手清理过期项）。限成 uuid 的形状之后，
     * key 空间和长度都是有界的。
     */
    private static final int MAX_UUID_LENGTH = 64;

    private final SecureRandom random = new SecureRandom();
    private final StringRedisTemplate redis;
    private final Duration expire;

    public CaptchaService(AdminProperties properties, StringRedisTemplate redis) {
        this.redis = redis;
        this.expire = Duration.ofSeconds(properties.captcha().expireSeconds());
    }

    /** 生成一张图，并把答案按 uuid 存进 Redis，带 TTL。 */
    public BufferedImage create(String uuid) {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        if (validUuid(uuid)) {
            // TTL 由 Redis 负责，不需要像原来那样在每次 create 时顺手扫一遍过期项。
            redis.opsForValue().set(key(uuid), code.toString(), expire);
        } else {
            // 图照样返回（不给探测者额外信息），但答案不存 —— 于是这张图必然校验不过。
            log.warn("验证码 uuid 不合法，本次不存答案: length={}", uuid == null ? -1 : uuid.length());
        }
        return render(code.toString());
    }

    /**
     * 校验验证码。
     * <p>
     * 用 {@code GETDEL} 原子地取出并删除：无论成功失败这条记录都不再存在。
     * 验证码必须一次性，否则同一个 uuid 可以被反复用来撞用户名密码。
     * <p>
     * 过期不需要单独判断 —— Redis 的 TTL 到了 key 自己就没了，取回来是 null。
     */
    public boolean verify(String uuid, String input) {
        if (!validUuid(uuid) || input == null) {
            return false;
        }
        String expected;
        try {
            expected = redis.opsForValue().getAndDelete(key(uuid));
        } catch (Exception e) {
            // 【失败关闭】拿不到答案就拒绝。降级成放行等于在 Redis 故障时
            // 把暴力破解的门打开。这里必须留日志，否则"登录全都失败"会无从查起。
            log.error("校验验证码时读 Redis 失败，按校验不通过处理", e);
            return false;
        }
        if (expected == null) {
            return false;
        }
        return expected.equalsIgnoreCase(input.trim());
    }

    private String key(String uuid) {
        return KEY_PREFIX + uuid;
    }

    /**
     * uuid 限形：只允许 uuid 里会出现的字符，且长度有界。
     * <p>
     * 不用严格的 uuid 正则，是因为前端在非安全上下文下走的是自己拼的 v4
     * （见 admin/src/utils/uuid.ts），格式虽然一致但没必要把两边绑死；
     * 这里要挡住的是超长字符串和奇怪字符，不是校验它是不是标准 uuid。
     */
    private static boolean validUuid(String uuid) {
        if (uuid == null || uuid.isEmpty() || uuid.length() > MAX_UUID_LENGTH) {
            return false;
        }
        for (int i = 0; i < uuid.length(); i++) {
            char c = uuid.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F') || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private BufferedImage render(String code) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0xF5, 0xF7, 0xFA));
            g.fillRect(0, 0, WIDTH, HEIGHT);
            // 干扰线：只加少量，目的是让最朴素的 OCR 失效，而不是让人也看不清
            for (int i = 0; i < 5; i++) {
                g.setColor(randomColor(160, 220));
                g.drawLine(random.nextInt(WIDTH), random.nextInt(HEIGHT),
                        random.nextInt(WIDTH), random.nextInt(HEIGHT));
            }
            int step = WIDTH / (LENGTH + 1);
            for (int i = 0; i < code.length(); i++) {
                g.setColor(randomColor(20, 110));
                g.setFont(new Font("SansSerif", Font.BOLD, 26 + random.nextInt(6)));
                // 每个字符略微旋转，进一步降低可 OCR 性
                double angle = (random.nextDouble() - 0.5) * 0.5;
                g.rotate(angle, step * (i + 1), HEIGHT / 2.0);
                g.drawString(String.valueOf(code.charAt(i)), step * (i + 1) - 8, HEIGHT - 10);
                g.rotate(-angle, step * (i + 1), HEIGHT / 2.0);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private Color randomColor(int min, int max) {
        int span = max - min;
        return new Color(min + random.nextInt(span), min + random.nextInt(span), min + random.nextInt(span));
    }
}
