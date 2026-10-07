package com.mall.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

/**
 * 校验 mall-admin 签发的管理端令牌（HS256 JWT）。
 *
 * <h3>为什么自己实现，而不是引 spring-security-oauth2-jose</h3>
 * 需要用它的地方是 <b>mall-gateway</b>，而给网关加 Spring Security 的依赖会连带引入
 * Spring Security 的自动配置 —— 那有把网关自身意外锁死的风险（一个鉴权改动导致
 * 整站 404 或 401 的代价远大于省下这几十行）。HS256 的校验只需要 JDK 自带的
 * {@code Mac} 和 {@code Base64}，没有任何 I/O，在 WebFlux 的事件循环上跑也安全。
 * <p>
 * mall-admin 那一侧用的是 Nimbus（{@code JwtService}），签发和校验分属两套实现。
 * 这是刻意的：签发方需要完整的 JWT 能力，校验方只需要「验签 + 看过期」两件事，
 * 而且校验方少一个依赖就少一个需要盯安全更新的东西。
 * {@code AdminTokenVerifierTest} 里有一条测试直接拿 mall-admin 的签发格式做交叉校验。
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

    /** HS256 要求密钥至少 256 位 = 32 字节。和 mall-admin 的 JwtService 保持同一条约束。 */
    public static final int MIN_SECRET_BYTES = 32;

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    /*
     * 【RS256，2026-10-07】HS256 的签名和验签是同一把密钥：把它发给每个要验签的服务，
     * 等于每个服务都能签发可从公网经网关使用的管理端令牌。改成 RS256 后只有 mall-admin 持有私钥，
     * 网关和各服务只拿公钥（不是秘密，泄露也签不出令牌）。HS256 只在过渡期保留，
     * 只有网关和 mall-admin 会配置它（让切换前签发的令牌在过期前仍可用），收尾时删除。
     *
     * 【按 alg 严格分流，防算法混淆】经典攻击：把头改成 alg=HS256，用【公钥字节】当 HMAC 密钥签名。
     * 如果校验方按 alg 选算法、又拿同一份公钥材料去验 HMAC，伪造的令牌就过了。
     * 这里 RS256 只用 RSA 公钥验；HS256 只用单独配置的 HMAC 密钥验（没配就拒）；
     * 公钥永远不会被当作 HMAC 密钥。alg=none 或任何其他值一律拒绝。
     */
    private final SecretKeySpec hmacKey;
    private final PublicKey rsaKey;

    /** 只认 HS256（旧行为，保留给现有调用方和测试）。 */
    public AdminTokenVerifier(String secret) {
        this(null, requireSecret(secret));
    }

    /**
     * @param rsaPublicKey RS256 公钥：PEM（带不带 BEGIN/END 行都行）或单行 base64 的 X.509 DER。可为空。
     * @param hmacSecret   过渡期的 HS256 密钥，可为空；非空时长度必须 &gt;= 32 字节。
     *                     两者都为空时启动即失败 —— 一个什么都验不了的校验器不该能被构造出来。
     */
    public AdminTokenVerifier(String rsaPublicKey, String hmacSecret) {
        boolean hasRsa = rsaPublicKey != null && !rsaPublicKey.isBlank();
        boolean hasHmac = hmacSecret != null && !hmacSecret.isBlank();
        if (!hasRsa && !hasHmac) {
            throw new IllegalStateException("管理端令牌校验器没有任何密钥：检查 JWT_PUBLIC_KEY（以及过渡期的 JWT_SECRET）是否注入。");
        }
        this.rsaKey = hasRsa ? parseRsaPublicKey(rsaPublicKey) : null;
        this.hmacKey = hasHmac ? new SecretKeySpec(requireSecret(hmacSecret).getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM) : null;
    }

    private static String requireSecret(String secret) {
        byte[] keyBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            // 启动即失败。密钥缺失或太短是配置错误，越早报越好 ——
            // 否则表现会是「所有管理端请求都 401」，而那看起来像登录坏了。
            throw new IllegalStateException(
                    "管理端令牌密钥至少需要 " + MIN_SECRET_BYTES + " 字节（HS256 的要求），当前只有 "
                            + keyBytes.length + " 字节。检查 JWT_SECRET 环境变量是否注入。");
        }
        return secret;
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
            byte[] signingInput = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
            byte[] actual = URL_DECODER.decode(parts[2]);
            String alg = readString(new String(URL_DECODER.decode(parts[0]), StandardCharsets.UTF_8), "alg");
            if ("RS256".equals(alg)) {
                if (rsaKey == null) {
                    return null;
                }
                Signature sig = Signature.getInstance("SHA256withRSA");
                sig.initVerify(rsaKey);
                sig.update(signingInput);
                if (!sig.verify(actual)) {
                    return null;
                }
            } else if ("HS256".equals(alg)) {
                if (hmacKey == null) {
                    return null;
                }
                // 定长比较：用 equals 逐字节短路比较会泄露「前几个字节对了」，
                // 理论上可以被用来逐字节猜签名。MessageDigest.isEqual 是定长的。
                if (!MessageDigest.isEqual(mac(signingInput), actual)) {
                    return null;
                }
            } else {
                return null; // alg=none、缺失、或任何没打算支持的算法
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

    private byte[] mac(byte[] data) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(hmacKey);
        return mac.doFinal(data);
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
