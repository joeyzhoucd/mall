package com.mall.common.security;

import com.mall.common.annotation.InternalApi;
import com.mall.common.annotation.PublicApi;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 默认拒绝的判定与两种模式。测试 controller 放在 com.mall.* 包下（本类所在包），
 * 框架类型用 java.lang.Object 的方法模拟「非 com.mall.* 的处理方法」。
 */
class AdminTokenInterceptorTest {

    /** 服务端只拿 RS256 公钥（单行 base64 X.509，和部署时 JWT_PUBLIC_KEY 同形状）。 */
    private static final String PUBLIC_KEY = AdminTokenVerifierRs256Test.publicKeyB64(AdminTokenVerifierRs256Test.KEYS);

    static class AdminController {
        public void list() { }
        @PublicApi public void page() { }
        @InternalApi public void feign() { }
    }

    @PublicApi
    static class StorefrontController {
        public void any() { }
    }

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private static HandlerMethod handler(Object bean, String name) throws NoSuchMethodException {
        return new HandlerMethod(bean, bean.getClass().getMethod(name));
    }

    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/product/brand/list");
        req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/product/brand/list");
        if (token != null) {
            req.addHeader(AdminTokenInterceptor.HEADER, token);
        }
        return req;
    }

    private static String validToken() throws Exception {
        return AdminTokenVerifierRs256Test.rs256(AdminTokenVerifierRs256Test.KEYS.getPrivate(), 1L, "admin", Instant.now().getEpochSecond() + 600);
    }

    private double count(String outcome) {
        var c = registry.find(AdminTokenInterceptor.METRIC).tag("outcome", outcome).counter();
        return c == null ? 0 : c.count();
    }

    @Test
    void unannotatedBusinessHandlerRequiresAdminAndAnnotatedOnesDoNot() throws Exception {
        assertThat(AdminTokenInterceptor.requiresAdmin(handler(new AdminController(), "list"))).isTrue();
        assertThat(AdminTokenInterceptor.requiresAdmin(handler(new AdminController(), "page"))).isFalse();
        assertThat(AdminTokenInterceptor.requiresAdmin(handler(new AdminController(), "feign"))).isFalse();
        assertThat(AdminTokenInterceptor.requiresAdmin(handler(new StorefrontController(), "any"))).isFalse();
    }

    @Test
    void frameworkHandlersOutsideComMallAreNeverChecked() throws Exception {
        // 模拟 actuator / /error / springdoc：处理方法的 bean 类型不在 com.mall.* 下
        assertThat(AdminTokenInterceptor.requiresAdmin(handler(new StringBuilder(), "toString"))).isFalse();
    }

    @Test
    void reportModeCountsButLetsThrough() throws Exception {
        var it = new AdminTokenInterceptor(PUBLIC_KEY, false, registry);
        HandlerMethod h = handler(new AdminController(), "list");

        assertThat(it.preHandle(request(validToken()), new MockHttpServletResponse(), h)).isTrue();
        assertThat(it.preHandle(request(null), new MockHttpServletResponse(), h)).isTrue();
        assertThat(it.preHandle(request("not-a-jwt"), new MockHttpServletResponse(), h)).isTrue();

        assertThat(count("ok")).isEqualTo(1);
        assertThat(count("missing")).isEqualTo(1);
        assertThat(count("invalid")).isEqualTo(1);
    }

    @Test
    void enforceModeRejectsMissingAndForgedWith401() throws Exception {
        var it = new AdminTokenInterceptor(PUBLIC_KEY, true, registry);
        HandlerMethod h = handler(new AdminController(), "list");

        MockHttpServletResponse missing = new MockHttpServletResponse();
        assertThat(it.preHandle(request(null), missing, h)).isFalse();
        assertThat(missing.getStatus()).isEqualTo(401);

        String forged = AdminTokenVerifierRs256Test.rs256(AdminTokenVerifierRs256Test.generate().getPrivate(), 1L, "admin",
                Instant.now().getEpochSecond() + 600);
        MockHttpServletResponse bad = new MockHttpServletResponse();
        assertThat(it.preHandle(request(forged), bad, h)).isFalse();
        assertThat(bad.getStatus()).isEqualTo(401);

        assertThat(it.preHandle(request(validToken()), new MockHttpServletResponse(), h)).isTrue();
    }

    @Test
    void publicAndInternalHandlersAreUntouchedEvenWhenEnforcing() throws Exception {
        var it = new AdminTokenInterceptor(PUBLIC_KEY, true, registry);
        assertThat(it.preHandle(request(null), new MockHttpServletResponse(), handler(new AdminController(), "page"))).isTrue();
        assertThat(it.preHandle(request(null), new MockHttpServletResponse(), handler(new StorefrontController(), "any"))).isTrue();
        assertThat(it.preHandle(request(null), new MockHttpServletResponse(), handler(new AdminController(), "feign"))).isTrue();
        assertThat(registry.find(AdminTokenInterceptor.METRIC).counters()).isEmpty();
    }

    @Test
    void reportModeWithoutSecretMarksUnconfigured() throws Exception {
        var it = new AdminTokenInterceptor("", false, registry);
        assertThat(it.preHandle(request(validToken()), new MockHttpServletResponse(), handler(new AdminController(), "list"))).isTrue();
        assertThat(count("unconfigured")).isEqualTo(1);
    }

    @Test
    void enforceWithoutSecretOrWithUnresolvedPlaceholderFailsAtStartup() {
        assertThatThrownBy(() -> new AdminTokenInterceptor("", true, registry)).hasMessageContaining("JWT_PUBLIC_KEY");
        assertThatThrownBy(() -> new AdminTokenInterceptor("${JWT_PUBLIC_KEY}", true, registry)).hasMessageContaining("JWT_PUBLIC_KEY");
    }

    @Test
    void serviceSideNeverAcceptsHs256EvenIfValidForTheOldSecret() throws Exception {
        // 服务端只配公钥：一个用旧 HS256 密钥签得完全合法的令牌也必须是 invalid
        var it = new AdminTokenInterceptor(PUBLIC_KEY, true, registry);
        String hs = AdminTokenVerifierTest.token("legacy-hs256-secret-at-least-32-bytes-long!!", 1L, "admin",
                Instant.now().getEpochSecond() + 600);
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(it.preHandle(request(hs), res, handler(new AdminController(), "list"))).isFalse();
        assertThat(res.getStatus()).isEqualTo(401);
    }
}
