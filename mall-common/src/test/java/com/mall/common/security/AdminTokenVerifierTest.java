package com.mall.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AdminTokenVerifier} 的行为约束。
 *
 * <h3>为什么这些用例都是「必须拒绝」</h3>
 * 这个类是网关上唯一的那道门。它的失效方式只有一种值得担心：<b>该拒绝的放过去了</b>。
 * 反过来「该通过的拒绝了」会立刻表现为所有人登不上后台，五分钟内就有人喊。
 * 所以下面绝大多数用例在构造各种「看起来像但不是」的令牌，
 * 并配一条正向用例防止实现退化成「一律拒绝」（那样所有拒绝用例都会假通过）。
 */
class AdminTokenVerifierTest {

    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long!!";
    private final AdminTokenVerifier verifier = new AdminTokenVerifier(SECRET);

    // ------------------------------------------------------------------ 造令牌

    private static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String sign(String signingInput, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return b64(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
    }

    /** 按 mall-admin 的 claim 形状造一个令牌：sub=用户名，uid=用户 id。 */
    private static String token(String secret, long uid, String sub, long expEpochSeconds) throws Exception {
        String header = b64("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64(("{\"sub\":\"" + sub + "\",\"uid\":" + uid
                + ",\"exp\":" + expEpochSeconds + "}").getBytes(StandardCharsets.UTF_8));
        String input = header + "." + payload;
        return input + "." + sign(input, secret);
    }

    private static long soon() {
        return Instant.now().getEpochSecond() + 3600;
    }

    // ------------------------------------------------------------------ 正向

    @Test
    @DisplayName("正确签名 + 未过期 → 通过，并取出 uid 和用户名")
    void acceptsValidToken() throws Exception {
        AdminTokenVerifier.Identity id = verifier.verify(token(SECRET, 42L, "admin", soon()));
        assertThat(id).isNotNull();
        assertThat(id.userId()).isEqualTo(42L);
        assertThat(id.username()).isEqualTo("admin");
    }

    // ------------------------------------------------------------------ 必须拒绝

    @Test
    @DisplayName("换一把密钥签的必须拒绝（伪造的核心场景）")
    void rejectsWrongSecret() throws Exception {
        String forged = token("another-secret-also-long-enough-to-pass-32b!", 42L, "admin", soon());
        assertThat(verifier.verify(forged)).isNull();
    }

    @Test
    @DisplayName("篡改 payload（改 uid 提权）必须拒绝")
    void rejectsTamperedPayload() throws Exception {
        String good = token(SECRET, 42L, "admin", soon());
        String[] parts = good.split("\\.");
        // 把 uid 改成 1（通常是超管），签名保持原样
        String evilPayload = b64(("{\"sub\":\"admin\",\"uid\":1,\"exp\":" + soon() + "}")
                .getBytes(StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + evilPayload + "." + parts[2];
        assertThat(verifier.verify(tampered)).isNull();
    }

    @Test
    @DisplayName("已过期必须拒绝")
    void rejectsExpired() throws Exception {
        assertThat(verifier.verify(token(SECRET, 42L, "admin", Instant.now().getEpochSecond() - 1)))
                .isNull();
    }

    @Test
    @DisplayName("没有 exp 必须拒绝（不能把「没写过期」当成永不过期）")
    void rejectsMissingExp() throws Exception {
        String header = b64("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64("{\"sub\":\"admin\",\"uid\":42}".getBytes(StandardCharsets.UTF_8));
        String input = header + "." + payload;
        assertThat(verifier.verify(input + "." + sign(input, SECRET))).isNull();
    }

    @Test
    @DisplayName("没有 uid 必须拒绝（拿不到身份就等于没鉴权）")
    void rejectsMissingUid() throws Exception {
        String header = b64("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64(("{\"sub\":\"admin\",\"exp\":" + soon() + "}").getBytes(StandardCharsets.UTF_8));
        String input = header + "." + payload;
        assertThat(verifier.verify(input + "." + sign(input, SECRET))).isNull();
    }

    @Test
    @DisplayName("畸形输入一律拒绝且不抛异常")
    void rejectsMalformed() {
        String[] bad = {
                null, "", "   ", "not-a-jwt", "a.b", "a.b.c.d",
                "a.b.c",                       // 三段但不是 base64
                "!!!.???.***",
                "eyJhbGciOiJIUzI1NiJ9..",      // 空 payload 和签名
        };
        for (String t : bad) {
            assertThat(verifier.verify(t)).as("输入：%s", t).isNull();
        }
    }

    @Test
    @DisplayName("空签名（alg:none 那类攻击）必须拒绝")
    void rejectsEmptySignature() {
        String header = b64("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64(("{\"sub\":\"admin\",\"uid\":1,\"exp\":" + soon() + "}")
                .getBytes(StandardCharsets.UTF_8));
        assertThat(verifier.verify(header + "." + payload + ".")).isNull();
    }

    // ------------------------------------------------------------------ 构造期约束

    @Test
    @DisplayName("密钥过短必须启动即失败，而不是先跑起来")
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new AdminTokenVerifier("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32")
                .hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new AdminTokenVerifier(null))
                .isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------------ 解析细节

    @Test
    @DisplayName("payload 里字段顺序和空格不影响解析")
    void parsesRegardlessOfFormatting() throws Exception {
        String header = b64("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64(("{ \"exp\" : " + soon() + " , \"uid\" : 7 , \"sub\" : \"bob\" }")
                .getBytes(StandardCharsets.UTF_8));
        String input = header + "." + payload;
        AdminTokenVerifier.Identity id = verifier.verify(input + "." + sign(input, SECRET));
        assertThat(id).isNotNull();
        assertThat(id.userId()).isEqualTo(7L);
        assertThat(id.username()).isEqualTo("bob");
    }
}
