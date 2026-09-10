package com.mall.admin.service;

import com.mall.admin.config.AdminProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证码的安全属性守护测试。
 *
 * <h3>这里测的三条都是「坏了也不会报错」的东西</h3>
 * <ol>
 *   <li><b>一次性</b> —— 如果校验时没把答案删掉，同一个 uuid 可以被反复用来
 *       撞用户名密码，验证码就形同虚设。而功能上<b>完全正常</b>：
 *       用户输对了就能登录，没有任何异常。</li>
 *   <li><b>Redis 故障时失败关闭</b> —— 如果降级成"放行"，那么 Redis 一抖
 *       登录就变成无验证码，正是攻击者最想要的状态。同样不会报错。</li>
 *   <li><b>uuid 限形</b> —— uuid 是客户端生成的、不可信。不限的话可以用任意长的
 *       字符串当 key 往 Redis 里塞条目。</li>
 * </ol>
 *
 * <h3>为什么用 mock 而不是真 Redis</h3>
 * 要测的是<b>这个类调了什么</b>（GETDEL 而不是 GET+DEL、异常时返回 false），
 * 不是 Redis 本身的行为。用 mock 能精确制造"Redis 抛异常"这种真容器上很难触发的情况。
 * Redis 真连得上由 {@code MallAdminApplicationTests} 在 CI 里验。
 */
class CaptchaServiceTest {

    private final AdminProperties properties = new AdminProperties(
            new AdminProperties.Jwt("0123456789012345678901234567890123456789", 43200),
            new AdminProperties.Captcha(300));

    private record Fixture(CaptchaService service, StringRedisTemplate redis, ValueOperations<String, String> ops) {
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        return new Fixture(new CaptchaService(properties, redis), redis, ops);
    }

    /**
     * 一次性：必须用 GETDEL，不能是 GET。
     *
     * <p>用 {@code getAndDelete} 而不是 {@code get} 还有一层并发上的理由：
     * 拆成 GET 再 DEL 会留一个窗口，两个并发请求可能都拿到同一个答案。
     */
    @Test
    @DisplayName("校验必须用 GETDEL 原子取删 —— 只 GET 不删等于验证码可以无限重试")
    void verifyMustConsumeTheCode() {
        Fixture f = fixture();
        when(f.ops().getAndDelete(anyString())).thenReturn("AB3D");

        assertThat(f.service().verify("11111111-2222-4333-8444-555555555555", "ab3d"))
                .as("大小写不敏感，输对就该通过")
                .isTrue();

        verify(f.ops()).getAndDelete("mall:admin:captcha:11111111-2222-4333-8444-555555555555");
        verify(f.ops(), never()).get(anyString());
    }

    @Test
    @DisplayName("答案不对时返回 false，而且答案同样已被删掉")
    void wrongInputStillConsumes() {
        Fixture f = fixture();
        when(f.ops().getAndDelete(anyString())).thenReturn("AB3D");

        assertThat(f.service().verify("11111111-2222-4333-8444-555555555555", "ZZZZ")).isFalse();
        // getAndDelete 本身就删了 —— 这条断言确认我们没有走"先看对不对再决定删不删"的路子，
        // 那种写法会让攻击者可以对同一张图无限次试。
        verify(f.ops()).getAndDelete(anyString());
    }

    @Test
    @DisplayName("key 不存在（过期或没生成过）返回 false")
    void missingCodeFails() {
        Fixture f = fixture();
        when(f.ops().getAndDelete(anyString())).thenReturn(null);
        assertThat(f.service().verify("11111111-2222-4333-8444-555555555555", "AB3D")).isFalse();
    }

    /**
     * 【失败关闭】Redis 挂了必须拒绝，不能放行。
     *
     * <p>验证码是安全控制，拿不到答案时唯一正确的行为是拒绝。
     * 降级成放行等于在依赖故障时把暴力破解的门打开 —— 比登不上去糟得多。
     */
    @Test
    @DisplayName("Redis 抛异常时必须返回 false —— 绝不能降级成放行")
    void redisFailureIsFailClosed() {
        Fixture f = fixture();
        when(f.ops().getAndDelete(anyString()))
                .thenThrow(new org.springframework.dao.QueryTimeoutException("redis 超时"));

        assertThat(f.service().verify("11111111-2222-4333-8444-555555555555", "AB3D"))
                .as("Redis 故障时放行等于把验证码这道防线关掉")
                .isFalse();
    }

    @Test
    @DisplayName("uuid 不可信：超长、含奇怪字符、空，一律拒绝且不碰 Redis")
    void rejectsMalformedUuid() {
        Fixture f = fixture();
        String tooLong = "a".repeat(65);
        for (String bad : new String[]{null, "", tooLong, "../../etc/passwd", "abc def", "zzzz"}) {
            assertThat(f.service().verify(bad, "AB3D"))
                    .as("不合法的 uuid: %s", bad == null ? "null" : bad)
                    .isFalse();
        }
        verify(f.ops(), never()).getAndDelete(anyString());
    }

    @Test
    @DisplayName("生成时写入带 TTL 的 key；uuid 不合法则不写")
    void createStoresWithTtl() {
        Fixture f = fixture();
        f.service().create("11111111-2222-4333-8444-555555555555");
        verify(f.ops()).set(
                eq("mall:admin:captcha:11111111-2222-4333-8444-555555555555"),
                anyString(),
                eq(Duration.ofSeconds(300)));

        Fixture f2 = fixture();
        // 图照样返回（不给探测者额外信息），但答案不存 —— 这张图必然校验不过
        assertThat(f2.service().create("../../evil")).isNotNull();
        verify(f2.ops(), never()).set(anyString(), anyString(), any(Duration.class));
    }
}
