package com.mall.common.security;

import com.mall.common.annotation.InternalApi;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalTokenInterceptorTest {

    static class Controller {
        @InternalApi
        public void internal() {
        }

        public void open() {
        }
    }

    @InternalApi
    static class InternalController {
        public void any() {
        }
    }

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private static HandlerMethod handler(Object bean, String name) throws NoSuchMethodException {
        return new HandlerMethod(bean, bean.getClass().getMethod(name));
    }

    private static MockHttpServletRequest request(String token) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/x/1");
        req.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/x/{id}");
        if (token != null) {
            req.addHeader(InternalTokenInterceptor.HEADER, token);
        }
        return req;
    }

    private double count(String outcome) {
        var c = registry.find(InternalTokenInterceptor.METRIC).tag("outcome", outcome).tag("uri", "/x/{id}").counter();
        return c == null ? 0 : c.count();
    }

    @Test
    void reportModeLetsEverythingThroughButCountsOutcome() throws Exception {
        var it = new InternalTokenInterceptor("secret", false, registry);
        HandlerMethod h = handler(new Controller(), "internal");

        assertThat(it.preHandle(request("secret"), new MockHttpServletResponse(), h)).isTrue();
        assertThat(it.preHandle(request(null), new MockHttpServletResponse(), h)).isTrue();
        assertThat(it.preHandle(request("wrong"), new MockHttpServletResponse(), h)).isTrue();

        assertThat(count("ok")).isEqualTo(1);
        assertThat(count("missing")).isEqualTo(1);
        assertThat(count("invalid")).isEqualTo(1);
    }

    @Test
    void enforceModeRejectsMissingAndWrongWith401() throws Exception {
        var it = new InternalTokenInterceptor("secret", true, registry);
        HandlerMethod h = handler(new Controller(), "internal");

        MockHttpServletResponse missing = new MockHttpServletResponse();
        assertThat(it.preHandle(request(null), missing, h)).isFalse();
        assertThat(missing.getStatus()).isEqualTo(401);

        MockHttpServletResponse wrong = new MockHttpServletResponse();
        assertThat(it.preHandle(request("secreT"), wrong, h)).isFalse();
        assertThat(wrong.getStatus()).isEqualTo(401);

        assertThat(it.preHandle(request("secret"), new MockHttpServletResponse(), h)).isTrue();
    }

    @Test
    void classLevelAnnotationCounts() throws Exception {
        var it = new InternalTokenInterceptor("secret", true, registry);
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(it.preHandle(request(null), res, handler(new InternalController(), "any"))).isFalse();
        assertThat(res.getStatus()).isEqualTo(401);
    }

    @Test
    void unannotatedEndpointIsUntouched() throws Exception {
        var it = new InternalTokenInterceptor("secret", true, registry);
        assertThat(it.preHandle(request(null), new MockHttpServletResponse(), handler(new Controller(), "open"))).isTrue();
        assertThat(registry.find(InternalTokenInterceptor.METRIC).counters()).isEmpty();
    }

    @Test
    void reportModeWithoutTokenMarksUnconfigured() throws Exception {
        var it = new InternalTokenInterceptor("", false, registry);
        assertThat(it.preHandle(request("anything"), new MockHttpServletResponse(), handler(new Controller(), "internal"))).isTrue();
        assertThat(count("unconfigured")).isEqualTo(1);
    }

    @Test
    void enforceWithoutTokenFailsAtStartup() {
        assertThatThrownBy(() -> new InternalTokenInterceptor(" ", true, registry))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("INTERNAL_TOKEN");
    }
}
