package com.mall.common.web;

import com.mall.common.utils.R;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 钉住 {@link UnhandledExceptionAdvice} 的三个决定。
 *
 * <p><b>这个测试不覆盖「它有没有被装上」</b> —— 那取决于自动配置的条件，
 * 要靠上下文启动的集成测试或对真集群验。这里保证的是「装上之后行为是对的」。
 */
class UnhandledExceptionAdviceTest {

    private final UnhandledExceptionAdvice advice = new UnhandledExceptionAdvice();

    private static MockHttpServletRequest get(String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setRequestURI(uri);
        return req;
    }

    @Test
    @DisplayName("真正没人处理的异常 -> 500")
    void unhandledBecomes500() {
        // 用 SQLException 是刻意的：当初那 2063 个无日志的 500，根因就是
        // Hikari 拿不到连接，异常链最终是一个 SQLException。
        ResponseEntity<R> res = advice.handle(
                new RuntimeException("boom", new SQLException("connection is not available")),
                get("/product/attr/spec/list"));

        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getCode()).isEqualTo(500);
    }

    @Test
    @DisplayName("【负控】有确定 HTTP 语义的异常不能被变成 500")
    void errorResponseKeepsItsStatus() {
        // 这是这个 advice 最容易写成倒退的地方：它声明的是 Exception，
        // 而 Spring MVC 那些「找不到路径 / 方法不允许 / 请求体解析失败」
        // 也都是 Exception 的子类，且解析器排在 DefaultHandlerExceptionResolver 之前。
        // 无差别接住的话，一个 404 会变成 500。
        //
        // 如果有人把类里那个 instanceof ErrorResponse 分支删掉，这条会挂。
        ResponseStatusException notFound =
                new ResponseStatusException(HttpStatus.NOT_FOUND, "no such thing");

        ResponseEntity<R> res = advice.handle(notFound, get("/product/does-not-exist"));

        assertThat(res.getStatusCode().value())
                .as("404 必须还是 404，不能变成 500")
                .isEqualTo(404);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("【负控】5xx 的 ErrorResponse 仍然是 5xx")
    void serverSideErrorResponseKeepsStatus() {
        // 上一条只证明了 4xx 分支。这条确认 ErrorResponse 那个分支
        // 不是简单地"一律当成客户端错误"。
        ResponseEntity<R> res = advice.handle(
                new ErrorResponseException(HttpStatus.BAD_GATEWAY),
                get("/order/submit"));

        assertThat(res.getStatusCode().value()).isEqualTo(502);
    }

    @Test
    @DisplayName("响应体里不能出现异常细节")
    void doesNotLeakExceptionDetails() {
        // 异常消息里放了几样真实泄漏时最要命的东西：表名、内网主机、连接串。
        String secretish =
                "Table 'mall_wms.wms_ware_order_task_detail' doesn't exist; "
                        + "jdbc:mysql://mysql-0.mysql:3306/mall_wms user=root";

        ResponseEntity<R> res = advice.handle(new IllegalStateException(secretish), get("/ware/sku/list"));

        String body = String.valueOf(res.getBody());
        assertThat(body).doesNotContain("wms_ware_order_task_detail");
        assertThat(body).doesNotContain("jdbc:mysql");
        assertThat(body).doesNotContain("mysql-0.mysql");
        assertThat(body).doesNotContain("root");
    }

    @Test
    @DisplayName("traceId 取不到时是空串，不是 null")
    void traceIdIsEmptyStringWhenAbsent() {
        // 测试里没有 tracing，MDC 是空的。
        // 关心这个是因为 null 和 "" 在 JSON 里对调用方是两种东西，
        // 而这里想表达的是「没有」，不是「有一个空值」。
        ResponseEntity<R> res = advice.handle(new RuntimeException("x"), get("/anything"));

        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().get("traceId")).isEqualTo("");
    }
}
