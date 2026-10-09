package com.mall.common.security;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * 校验 mall-admin 签发的管理端令牌（RS256 JWT）。网关和各服务用它，只需要<b>公钥</b>。
 *
 * <h3>为什么自己实现，而不是引 spring-security-oauth2-jose</h3>
 * 需要用它的地方是 <b>mall-gateway</b>，而给网关加 Spring Security 的依赖会连带引入
 * Spring Security 的自动配置 —— 那有把网关自身意外锁死的风险（一个鉴权改动导致
 * 整站 404 或 401 的代价远大于省下这几十行）。RS256 验签只需要 JDK 自带的
 * {@code Signature} 和 {@code Base64}，没有任何 I/O，在 WebFlux 的事件循环上跑也安全。
 * <p>
 * mall-admin 那一侧用的是 Nimbus（{@code JwtService}），签发和校验分属两套实现。
 * 这是刻意的：签发方需要完整的 JWT 能力，校验方只需要「验签 + 看过期」两件事，
 * 而且校验方少一个依赖就少一个需要盯安全更新的东西。
 * mall-admin 的 {@code AdminTokenCrossCheckTest} 拿真实签发的令牌做交叉校验，防两边漂移。
 *
 * <h3>为什么是 RS256（2026-10-07 起；HS256 已于 2026-10-09 移除）</h3>
 * HS256 的签名和验签是同一把密钥。服务端「默认拒绝」要求每个服务都能验签，
 * 把 HS256 密钥发给每个服务，就等于每个服务都能签发可从公网经网关使用的管理端令牌。
 * RS256 下私钥只在 mall-admin，公钥不是秘密、泄露也签不出令牌。
 * 过渡期（旧 HS256 令牌在 12 小时内陆续过期）结束后，HS256 的校验路径、JWT_SECRET
 * 和它在仓库里的公开默认值一起删除。
 *
 * <h3>只认 RS256，防算法混淆</h3>
 * 经典攻击是把头改成 alg=HS256、拿公钥字节当 HMAC 密钥签名 —— 校验方如果按头里的 alg
 * 选算法就会被骗。这里只有 RS256 一条路径，头里的 alg 不是 RS256（none、HS256、缺失……）一律拒绝。
 *
 * <h3>刻意只校验两件事</h3>
 * 签名和过期。<b>不校验 issuer / audience</b>，因为 mall-admin 签发时也没设置它们 ——
 * 校验一个签发方根本不写的字段，只会得到「永远失败」或者「永远通过」，
 * 两种都不是安全收益。将来 mall-admin 加上了，这里再一起加。
 * <p>
 * 也<b>不做黑名单</b>：mall-admin 的 JWT 是无状态的，登出无法立即失效，
 * 这个限制在它那边已经写明。网关这一层不该单独发明一套相反的语义。
 */
public final class AdminTokenVerifier {

    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final PublicKey rsaKey;

    /**
     * @param rsaPublicKey RS256 公钥：PEM（带不带 BEGIN/END 行都行）或单行 base64 的 X.509 DER。
     *                     缺失或格式不对时启动即失败 —— 一个什么都验不了的校验器不该能被构造出来，
     *                     否则表现会是「所有管理端请求都 401」，而那看起来像登录坏了。
     */
    public AdminTokenVerifier(String rsaPublicKey) {
        if (rsaPublicKey == null || rsaPublicKey.isBlank() || rsaPublicKey.contains("${")) {
            throw new IllegalStateException("管理端令牌校验器没有公钥：检查 JWT_PUBLIC_KEY 是否注入。");
        }
        this.rsaKey = parseRsaPublicKey(rsaPublicKey);
    }

    /** PEM 或单行 base64 的 X.509 SubjectPublicKeyInfo → RSA 公钥。格式不对启动即失败。 */
    static PublicKey parseRsaPublicKey(String text) {
        String b64 = text.replaceAll("-----(BEGIN|END) PUBLIC KEY-----", "").replaceAll("\\s+", "");
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64)));
        } catch (Exception e) {
            throw new IllegalStateException("JWT_PUBLIC_KEY 不是有效的 RSA 公钥（期望 PEM 或 base64 的 X.509 DER）", e);
        }
    }

    /**
     * 校验令牌。
     *
     * @return 校验通过则返回其中的身份信息；<b>任何一种失败都返回 null</b>，不抛异常、
     *         也不区分失败原因。区分「签名错」和「过期了」对调用方没有用，
     *         而对攻击者是免费的信息。
     */
    public Identity verify(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return null;
        }
        try {
            String alg = readString(new String(URL_DECODER.decode(parts[0]), StandardCharsets.UTF_8), "alg");
            if (!"RS256".equals(alg)) {
                return null; // alg=none / HS256 / 缺失 / 任何其他值
            }
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(rsaKey);
            sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!sig.verify(URL_DECODER.decode(parts[2]))) {
                return null;
            }
            String payload = new String(URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
            Long exp = readNumber(payload, "exp");
            if (exp == null || Instant.now().getEpochSecond() >= exp) {
                return null;
            }
            Long userId = readNumber(payload, "uid");
            String username = readString(payload, "sub");
            if (userId == null) {
                return null;
            }
            return new Identity(userId, username);
        } catch (Exception e) {
            // 畸形 base64、畸形 JSON 等一律当校验失败。
            // 这里刻意吞掉异常：一个构造过的令牌不该让网关抛栈。
            return null;
        }
    }

    /**
     * 从 JWT payload 里取一个数字字段。
     * <p>
     * 刻意手写而不是引 JSON 库：payload 是自己服务签发的、结构固定的几个字段，
     * 而这段代码跑在网关的每一个管理端请求上。更重要的是<b>签名已经先验过了</b> ——
     * 走到这里的内容一定是自己签的，不是攻击者能控制的任意 JSON，
     * 所以不需要一个通用解析器的健壮性。解析不出来就返回 null，调用方按校验失败处理。
     */
    static Long readNumber(String json, String field) {
        String raw = rawValue(json, field);
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String readString(String json, String field) {
        String raw = rawValue(json, field);
        if (raw == null) {
            return null;
        }
        raw = raw.trim();
        if (raw.length() >= 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    private static String rawValue(String json, String field) {
        String needle = "\"" + field + "\"";
        int k = json.indexOf(needle);
        if (k < 0) {
            return null;
        }
        int colon = json.indexOf(':', k + needle.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length()) {
            return null;
        }
        int end;
        if (json.charAt(i) == '"') {
            end = json.indexOf('"', i + 1);
            if (end < 0) {
                return null;
            }
            end++;
        } else {
            end = i;
            while (end < json.length() && ",}] \t\r\n".indexOf(json.charAt(end)) < 0) {
                end++;
            }
        }
        return json.substring(i, end);
    }

    /** 令牌里的身份。 */
    public record Identity(Long userId, String username) { }
}
