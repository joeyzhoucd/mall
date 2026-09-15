package com.mall.common.web;

import com.mall.common.utils.R;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.beans.TypeMismatchException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.ResponseStatus;
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

    /**
     * <b>这一条对应一个真实的倒退（2026-09-15 发现并修）。</b>
     *
     * <p>{@code http://mall.com/promotion.html} 返回 500。成因：mall-product 的
     * {@code ItemController} 映射了 {@code /{skuId}.html} 而参数是 {@code Long}，
     * 非数字路径段抛 {@code MethodArgumentTypeMismatchException} ——
     * 它 {@code extends TypeMismatchException}（一个 {@code BeansException}），
     * <b>没有实现 ErrorResponse</b>，于是被这个 advice 当成未处理异常变成了 500。
     * <p>
     * Spring 本来会给 400（{@code DefaultHandlerExceptionResolver} 的判定），
     * 但 {@code ExceptionHandlerExceptionResolver} 排在它前面，根本轮不到。
     * <p>
     * 后果不只是状态码难看：500 会进服务端错误率、会触发告警，
     * 而爬虫和失效链接会源源不断打这类地址。
     */
    @Test
    @DisplayName("【负控】参数类型不匹配是 400，不能变成 500")
    void typeMismatchStays400() {
        // /promotion.html 打到 @GetMapping("/{skuId}.html") 上，skuId 是 Long
        TypeMismatchException e = new TypeMismatchException("promotion", Long.class);

        ResponseEntity<R> res = advice.handle(e, get("/promotion.html"));

        assertThat(res.getStatusCode().value())
                .as("参数类型不匹配是调用方的问题，Spring 判 400；变成 500 会污染错误率并触发告警")
                .isEqualTo(400);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getCode()).isEqualTo(400);
    }

    /**
     * 业务自定义异常常用 {@code @ResponseStatus} 声明语义，
     * 它同样不是 {@code ErrorResponse}，不认的话也会被变成 500。
     */
    @Test
    @DisplayName("【负控】异常类上的 @ResponseStatus 要被尊重")
    void responseStatusAnnotationIsHonoured() {
        ResponseEntity<R> res = advice.handle(new ConflictException(), get("/order/submit"));

        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getCode()).isEqualTo(409);
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    static class ConflictException extends RuntimeException {
    }

    /**
     * 正控：上面两条放宽了判定，但不能宽到把真正的故障也放过。
     * 没有这一条的话，「一律按 400 返回」也能让上面两条通过。
     */
    @Test
    @DisplayName("正控：没有 HTTP 语义的异常仍然必须是 500")
    void plainExceptionStill500() {
        ResponseEntity<R> res = advice.handle(
                new RuntimeException("boom", new SQLException("pool exhausted")), get("/product/list"));

        assertThat(res.getStatusCode().value()).isEqualTo(500);
    }

    /**
     * 4xx 不能说「服务暂时不可用，请稍后重试」——那句话对客户端错误是<b>假的</b>。
     *
     * <p>2026-09-15 上线后实测到的：{@code mall.com/promotion.html} 返回
     * {@code {"code":400,"msg":"服务暂时不可用，请稍后重试"}}。
     * 状态码是对的，话是错的：服务好好的，是请求本身不对，重试多少次都一样。
     * <p>
     * 这不只是措辞：它会把人往错的方向引（去查服务端有没有挂），
     * 也会诱导客户端写出「4xx 也自动重试」的逻辑。
     */
    @Test
    @DisplayName("4xx 的文案不能说「服务暂时不可用」——重试解决不了客户端错误")
    void clientErrorsDoNotClaimServiceIsDown() {
        ResponseEntity<R> res = advice.handle(
                new TypeMismatchException("promotion", Long.class), get("/promotion.html"));

        String msg = String.valueOf(res.getBody().get("msg"));
        assertThat(msg)
                .as("400 说「服务暂时不可用，请稍后重试」是假的，会把排查引向服务端")
                .doesNotContain("服务暂时不可用");
        assertThat(msg).isNotBlank();
    }

    @Test
    @DisplayName("正控：5xx 仍然说「服务暂时不可用」")
    void serverErrorsStillSayServiceUnavailable() {
        // 没有这一条的话，「两边都改成同一句新文案」也能让上面那条通过。
        ResponseEntity<R> res = advice.handle(new RuntimeException("boom"), get("/product/list"));

        assertThat(String.valueOf(res.getBody().get("msg"))).contains("服务暂时不可用");
    }

    @Test
    @DisplayName("4xx 的文案同样不能泄漏异常细节")
    void clientErrorMessageDoesNotLeakEither() {
        // 换文案时最容易顺手把 e.getMessage() 拼进去 —— 那正是类注释第 ③ 条禁止的。
        // 这里用一个消息里带表名和连接串的 4xx 异常来盯住它。
        ResponseEntity<R> res = advice.handle(
                new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Table 'mall_wms.wms_ware_order_task_detail'; jdbc:mysql://mysql-0.mysql:3306"),
                get("/ware/lock"));

        String body = String.valueOf(res.getBody());
        assertThat(body).doesNotContain("wms_ware_order_task_detail");
        assertThat(body).doesNotContain("jdbc:mysql");
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
