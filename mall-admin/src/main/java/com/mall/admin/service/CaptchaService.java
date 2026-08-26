package com.mall.admin.service;

import com.mall.admin.config.AdminProperties;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图形验证码。
 * <p>
 * 前端登录表单把 captcha 设成了必填（login.vue 的表单校验），所以这个能力不能省略。
 * <p>
 * 刻意不引第三方验证码库：需求只是"生成一张有几个字符、带点干扰的图"，
 * 用 JDK 自带的 Graphics2D 二十行就够，引一个库反而多一个要跟安全更新的依赖。
 *
 * <h3>已知限制：验证码存在【单实例内存】里</h3>
 * 旧实现把验证码存在 sys_captcha 表里，天然支持多副本。这里为了少一次数据库往返
 * 改成了进程内 Map，代价是 <b>replicaCount &gt; 1 时会出现"在 A 实例取的码到 B 实例校验不到"</b>，
 * 表现为登录时随机报验证码错误。当前部署是单副本，所以先这样；
 * 要扩副本必须先把这里换成 Redis（项目里已经有 Redis，改动很小）。
 * 把这个限制写在这里而不是留给以后的人去发现 —— 那种 bug 的现象（时好时坏的登录失败）
 * 极难定位到验证码存储上。
 */
@Service
public class CaptchaService {

    private static final int WIDTH = 120;
    private static final int HEIGHT = 40;
    private static final int LENGTH = 4;
    /** 去掉了容易看错的 0/O/1/I/l 等字符 */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final long expireSeconds;

    public CaptchaService(AdminProperties properties) {
        this.expireSeconds = properties.captcha().expireSeconds();
    }

    /** 生成一张图，并把答案按 uuid 记下来。 */
    public BufferedImage create(String uuid) {
        purgeExpired();
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        store.put(uuid, new Entry(code.toString(), Instant.now().plusSeconds(expireSeconds)));
        return render(code.toString());
    }

    /**
     * 校验验证码。
     * <p>
     * 无论成功失败都把这条记录删掉：验证码必须一次性，否则同一个 uuid 可以被反复用来
     * 撞用户名密码，等于验证码形同虚设。
     */
    public boolean verify(String uuid, String input) {
        if (uuid == null || input == null) {
            return false;
        }
        Entry entry = store.remove(uuid);
        if (entry == null || entry.expireAt().isBefore(Instant.now())) {
            return false;
        }
        return entry.code().equalsIgnoreCase(input.trim());
    }

    /**
     * 清理过期项。没有用定时任务，而是在每次生成时顺手清一遍：
     * 生成频率天然等于登录页访问频率，足够及时，也省一个后台线程。
     */
    private void purgeExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> e.getValue().expireAt().isBefore(now));
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

    private record Entry(String code, Instant expireAt) {
    }
}
