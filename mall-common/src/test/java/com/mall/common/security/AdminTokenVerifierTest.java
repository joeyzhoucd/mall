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
 * {@link AdminTokenVerifier} 的行为约束（RS256；HS256 已于 2026-10-09 移除）。
 *
 * <h3>为什么这些用例都是「必须拒绝」</h3>
 * 这个类是网关和各服务上的那道门。它的失效方式只有一种值得担心：<b>该拒绝的放过去了</b>。
 * 反过来「该通过的拒绝了」会立刻表现为所有人登不上后台，五分钟内就有人喊。
 * 所以下面绝大多数用例在构造各种「看起来像但不是」的令牌，
 * 并配一条正向用例防止实现退化成「一律拒绝」（那样所有拒绝用例都会假通过）。
 *
 * <p>HS256 的造令牌方法保留在测试里（{@link #hs256}）：它现在是<b>攻击者的工具</b>，
 * 用来证明任何 HS256 令牌 —— 包括拿公钥字节当 HMAC 密钥的算法混淆 —— 都过不了。
 */
class AdminTokenVerifierTest {

    static final KeyPair KEYS = generate();
    private static final KeyPair OTHER = generate();

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

    private final AdminTokenVerifier verifier = new AdminTokenVerifier(publicKeyB64(KEYS));

    // ------------------------------------------------------------------ 造令牌

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String b64(String s) {
        return b64(s.getBytes(StandardCharsets.UTF_8));
    }

    /** 按 mall-admin 的 claim 形状造 payload：sub=用户名，uid=用户 id。 */
    private static String payload(long uid, String sub, long exp) {
        return b64("{\"sub\":\"" + sub + "\",\"uid\":" + uid + ",\"exp\":" + exp + "}");
    }

    static String rs256Raw(PrivateKey key, String headerJson, String payloadB64) throws Exception {
        String input = b64(headerJson) + "." + payloadB64;
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(key);
        s.update(input.getBytes(StandardCharsets.US_ASCII));
        return input + "." + b64(s.sign());
    }

    /** 包内可见：AdminTokenInterceptorTest 复用。 */
    static String rs256(PrivateKey key, long uid, String sub, long exp) throws Exception {
        return rs256Raw(key, "{\"alg\":\"RS256\",\"typ\":\"JWT\"}", payload(uid, sub, exp));
    }

    /** 攻击者的工具：任意 HMAC 密钥签一个 alg=HS256 的令牌。包内可见，AdminTokenInterceptorTest 复用。 */
    static String hs256(byte[] secret, long uid, String sub, long exp) throws Exception {
        String input = b64("{\"alg\":\"HS256\"}") + "." + payload(uid, sub, exp);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return input + "." + b64(mac.doFinal(input.getBytes(StandardCharsets.US_ASCII)));
    }

    private static long soon() {
        return Instant.now().getEpochSecond() + 3600;
    }

    // ------------------------------------------------------------------ 正向

    @Test
    @DisplayName("正确签名 + 未过期 → 通过，并取出 uid 和用户名")
    void acceptsValidToken() throws Exception {
        AdminTokenVerifier.Identity id = verifier.verify(rs256(KEYS.getPrivate(), 42L, "admin", soon()));
        assertThat(id).isNotNull();
        assertThat(id.userId()).isEqualTo(42L);
        assertThat(id.username()).isEqualTo("admin");
    }

    @Test
    @DisplayName("PEM 格式的公钥同样可用")
    void acceptsPemPublicKey() throws Exception {
        String pem = "-----BEGIN PUBLIC KEY-----\n" + Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(KEYS.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----\n";
        assertThat(new AdminTokenVerifier(pem).verify(rs256(KEYS.getPrivate(), 1L, "a", soon()))).isNotNull();
    }

    // ------------------------------------------------------------------ 必须拒绝

    @Test
    @DisplayName("换一把私钥签的必须拒绝（伪造的核心场景）")
    void rejectsOtherKey() throws Exception {
        assertThat(verifier.verify(rs256(OTHER.getPrivate(), 42L, "admin", soon()))).isNull();
    }

    @Test
    @DisplayName("篡改 payload（改 uid 提权）必须拒绝")
    void rejectsTamperedPayload() throws Exception {
        String good = rs256(KEYS.getPrivate(), 42L, "admin", soon());
        String[] p = good.split("\\.");
        String tampered = p[0] + "." + payload(1L, "admin", soon()) + "." + p[2];
        assertThat(verifier.verify(tampered)).isNull();
    }

    @Test
    @DisplayName("已过期必须拒绝")
    void rejectsExpired() throws Exception {
        assertThat(verifier.verify(rs256(KEYS.getPrivate(), 42L, "admin", Instant.now().getEpochSecond() - 1))).isNull();
    }

    @Test
    @DisplayName("没有 exp 必须拒绝（不能把「没写过期」当成永不过期）")
    void rejectsMissingExp() throws Exception {
        String t = rs256Raw(KEYS.getPrivate(), "{\"alg\":\"RS256\"}", b64("{\"sub\":\"admin\",\"uid\":42}"));
        assertThat(verifier.verify(t)).isNull();
    }

    @Test
    @DisplayName("没有 uid 必须拒绝（拿不到身份就等于没鉴权）")
    void rejectsMissingUid() throws Exception {
        String t = rs256Raw(KEYS.getPrivate(), "{\"alg\":\"RS256\"}", b64("{\"sub\":\"admin\",\"exp\":" + soon() + "}"));
        assertThat(verifier.verify(t)).isNull();
    }

    @Test
    @DisplayName("畸形输入一律拒绝且不抛异常")
    void rejectsMalformed() {
        for (String t : new String[]{null, "", "   ", "a", "a.b", "a.b.c.d", "!!!.@@@.###", "e30.e30.e30"}) {
            assertThat(verifier.verify(t)).as("input=%s", t).isNull();
        }
    }

    @Test
    @DisplayName("算法混淆：alg=HS256、拿公钥字节当 HMAC 密钥签名，必须拒绝")
    void rejectsAlgorithmConfusion() throws Exception {
        for (byte[] keyMaterial : new byte[][]{
                KEYS.getPublic().getEncoded(),
                publicKeyB64(KEYS).getBytes(StandardCharsets.UTF_8)}) {
            assertThat(verifier.verify(hs256(keyMaterial, 1L, "attacker", soon()))).isNull();
        }
    }

    @Test
    @DisplayName("任何 HS256 令牌都拒绝（HS256 已移除，旧密钥签的也一样）")
    void rejectsAnyHs256() throws Exception {
        String legacy = hs256("legacy-hs256-secret-at-least-32-bytes-long!!".getBytes(StandardCharsets.UTF_8), 7L, "old", soon());
        assertThat(verifier.verify(legacy)).isNull();
    }

    @Test
    @DisplayName("alg=none / 缺 alg / 不支持的 alg 必须拒绝（含空签名）")
    void rejectsNoneAndUnknownAlg() throws Exception {
        String body = payload(1L, "x", soon());
        for (String header : new String[]{"{\"alg\":\"none\"}", "{\"typ\":\"JWT\"}", "{\"alg\":\"RS512\"}", "{\"alg\":\"rs256\"}"}) {
            assertThat(verifier.verify(b64(header) + "." + body + ".")).isNull();
            assertThat(verifier.verify(b64(header) + "." + body + ".c2ln")).isNull();
        }
        // 签名是真的 RS256，但头里写的不是 RS256：也不能放过
        assertThat(verifier.verify(rs256Raw(KEYS.getPrivate(), "{\"alg\":\"none\"}", body))).isNull();
    }

    // ------------------------------------------------------------------ 构造

    @Test
    @DisplayName("没有公钥、未解析的占位符、或格式不对：构造即失败")
    void constructionFailsWithoutUsableKey() {
        assertThatThrownBy(() -> new AdminTokenVerifier(null)).hasMessageContaining("JWT_PUBLIC_KEY");
        assertThatThrownBy(() -> new AdminTokenVerifier(" ")).hasMessageContaining("JWT_PUBLIC_KEY");
        assertThatThrownBy(() -> new AdminTokenVerifier("${JWT_PUBLIC_KEY}")).hasMessageContaining("JWT_PUBLIC_KEY");
        assertThatThrownBy(() -> new AdminTokenVerifier("not-a-key")).hasMessageContaining("RSA");
    }

    // ------------------------------------------------------------------ 解析细节

    @Test
    @DisplayName("payload 里字段顺序和空格不影响解析")
    void parsesRegardlessOfFormatting() throws Exception {
        String t = rs256Raw(KEYS.getPrivate(), "{\"alg\":\"RS256\"}",
                b64("{ \"exp\" : " + soon() + " , \"uid\" : 7 , \"sub\" : \"bob\" }"));
        AdminTokenVerifier.Identity id = verifier.verify(t);
        assertThat(id).isNotNull();
        assertThat(id.userId()).isEqualTo(7L);
        assertThat(id.username()).isEqualTo("bob");
    }
}
