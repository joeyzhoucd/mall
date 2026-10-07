package com.mall.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RS256 与「按 alg 严格分流」（2026-10-07）。和 AdminTokenVerifierTest 一样，绝大多数用例是「必须拒绝」，
 * 配正向用例防止实现退化成一律拒绝。重点是算法混淆：用公钥字节当 HMAC 密钥签一个 alg=HS256 的令牌。
 */
class AdminTokenVerifierRs256Test {

    static final KeyPair KEYS = generate();
    private static final KeyPair OTHER = generate();
    private static final String LEGACY_SECRET = "legacy-hs256-secret-at-least-32-bytes-long!!";

    static KeyPair generate() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** 单行 base64 的 X.509 DER —— 部署时 JWT_PUBLIC_KEY 就是这个形状。 */
    static String publicKeyB64(KeyPair kp) {
        return Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
    }

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String payload(long uid, String sub, long exp) {
        return b64(("{\"sub\":\"" + sub + "\",\"uid\":" + uid + ",\"exp\":" + exp + "}").getBytes(StandardCharsets.UTF_8));
    }

    private static long soon() {
        return Instant.now().getEpochSecond() + 3600;
    }

    /** 包内可见：AdminTokenInterceptorTest 复用。 */
    static String rs256(PrivateKey key, long uid, String sub, long exp) throws Exception {
        String input = b64("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8)) + "." + payload(uid, sub, exp);
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(key);
        s.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + b64(s.sign());
    }

    private static String hs256(byte[] secret, long uid, String sub, long exp) throws Exception {
        String input = b64("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8)) + "." + payload(uid, sub, exp);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return input + "." + b64(mac.doFinal(input.getBytes(StandardCharsets.US_ASCII)));
    }

    private final AdminTokenVerifier rsaOnly = new AdminTokenVerifier(publicKeyB64(KEYS), null);
    private final AdminTokenVerifier transition = new AdminTokenVerifier(publicKeyB64(KEYS), LEGACY_SECRET);

    @Test
    @DisplayName("正向：RS256 令牌通过，身份解析正确")
    void acceptsValidRs256() throws Exception {
        AdminTokenVerifier.Identity id = rsaOnly.verify(rs256(KEYS.getPrivate(), 42L, "admin", soon()));
        assertThat(id).isNotNull();
        assertThat(id.userId()).isEqualTo(42L);
        assertThat(id.username()).isEqualTo("admin");
    }

    @Test
    @DisplayName("PEM 格式的公钥同样可用")
    void acceptsPemPublicKey() throws Exception {
        String pem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(KEYS.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----\n";
        assertThat(new AdminTokenVerifier(pem, null).verify(rs256(KEYS.getPrivate(), 1L, "a", soon()))).isNotNull();
    }

    @Test
    @DisplayName("别的私钥签的 RS256 令牌被拒")
    void rejectsRs256FromOtherKey() throws Exception {
        assertThat(rsaOnly.verify(rs256(OTHER.getPrivate(), 42L, "admin", soon()))).isNull();
    }

    @Test
    @DisplayName("算法混淆：alg=HS256、拿公钥字节当 HMAC 密钥签名，必须被拒（只配公钥 / 过渡期都要拒）")
    void rejectsAlgorithmConfusion() throws Exception {
        for (byte[] keyMaterial : new byte[][]{
                KEYS.getPublic().getEncoded(),
                publicKeyB64(KEYS).getBytes(StandardCharsets.UTF_8)}) {
            String forged = hs256(keyMaterial, 1L, "attacker", soon());
            assertThat(rsaOnly.verify(forged)).isNull();
            assertThat(transition.verify(forged)).isNull();
        }
    }

    @Test
    @DisplayName("alg=none / 缺 alg / 不支持的 alg 被拒")
    void rejectsNoneAndUnknownAlg() throws Exception {
        String body = payload(1L, "x", soon());
        for (String header : new String[]{"{\"alg\":\"none\"}", "{\"typ\":\"JWT\"}", "{\"alg\":\"RS512\"}"}) {
            String t = b64(header.getBytes(StandardCharsets.UTF_8)) + "." + body + ".";
            assertThat(rsaOnly.verify(t)).isNull();
            assertThat(transition.verify(t + "c2ln")).isNull();
        }
    }

    @Test
    @DisplayName("HS256：只配公钥时拒绝；过渡期配了旧密钥时接受")
    void hs256OnlyDuringTransition() throws Exception {
        String legacy = hs256(LEGACY_SECRET.getBytes(StandardCharsets.UTF_8), 7L, "old", soon());
        assertThat(rsaOnly.verify(legacy)).isNull();
        assertThat(transition.verify(legacy)).isNotNull();
    }

    @Test
    @DisplayName("过期的 RS256 令牌被拒")
    void rejectsExpiredRs256() throws Exception {
        assertThat(rsaOnly.verify(rs256(KEYS.getPrivate(), 1L, "a", Instant.now().getEpochSecond() - 1))).isNull();
    }

    @Test
    @DisplayName("没有任何密钥、或公钥格式不对：构造即失败")
    void constructionFailsWithoutUsableKey() {
        assertThatThrownBy(() -> new AdminTokenVerifier(null, null)).hasMessageContaining("JWT_PUBLIC_KEY");
        assertThatThrownBy(() -> new AdminTokenVerifier(" ", "")).hasMessageContaining("JWT_PUBLIC_KEY");
        assertThatThrownBy(() -> new AdminTokenVerifier("not-a-key", null)).hasMessageContaining("RSA");
        assertThatThrownBy(() -> new AdminTokenVerifier(publicKeyB64(KEYS), "short")).hasMessageContaining("32");
    }
}
