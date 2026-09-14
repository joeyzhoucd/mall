package com.mall.common.web;

import com.mall.common.utils.R;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 兜底的未处理异常处理器：<b>让 500 至少在日志里留下一行</b>。
 *
 * <h3>为什么需要它：500 可以完全没有日志</h3>
 * 2026-09-08 压测时实测到 <b>2063 个 HTTP 500</b>，而
 * {@code hikaricp_connections_timeout_total} 恰好是 <b>2064</b>（1:1），
 * 也就是说每一个 500 都是连接池拿不到连接。
 * 但<b>日志里一行都没有</b>。
 *
 * <p>原因不是日志级别配错了，是 Spring Boot 本来就不记录未处理的控制器异常：
 * 异常从控制器抛出 → DispatcherServlet 找不到能处理它的 resolver →
 * 转发到 {@code /error} → BasicErrorController 渲染响应体。
 * 整条路径上没有人打 ERROR 日志，而 Tomcat 的访问日志阀门只看得到
 * 最终那个 200/500 状态码，看不到异常。
 *
 * <p>结果是最糟的一种失败形态：<b>线上在大面积报错，而可观测性系统一片安静</b>。
 * 当时是靠「500 的数量和一个指标 1:1 对得上」反推出根因的，
 * 那属于运气好 —— 指标恰好存在、数量恰好相等。
 *
 * <h3>为什么放在 mall-common 而不是各模块各写一份</h3>
 * 2026-09-13 数过：11 个有 controller 的模块里，<b>8 个一个处理器都没有</b>；
 * 而已有的 3 个（product / order / payment）只处理参数校验异常
 * （{@code BindException} / {@code ConstraintViolationException}），
 * <b>没有一个兜住 {@code Exception}</b>。也就是说 Hikari 超时那类异常
 * 在任何一个模块里都不会被记录。各写一份必然继续漏。
 *
 * <h3>三个容易把它写成「倒退」的地方</h3>
 *
 * <p><b>① 不能把 404/405 变成 500。</b>
 * 这个 advice 声明的是 {@code Exception}，而 Spring MVC 自己那些
 * 「有确定 HTTP 语义」的异常（找不到路径、方法不允许、请求体解析失败……）
 * 也是 {@code Exception} 的子类。ExceptionHandlerExceptionResolver 排在
 * DefaultHandlerExceptionResolver <b>前面</b>，所以如果这里无差别接住，
 * 一个 404 会变成 500 —— 那是实打实的功能倒退。
 * 处理方式是先认 {@link ErrorResponse}：Spring 把这类异常统一实现成了它，
 * 带着自己该有的状态码。认出来就按它的状态码返回，并且<b>不打 ERROR</b>
 * （客户端错误不是故障，打 ERROR 只会把真正的故障淹掉）。
 *
 * <p><b>② 不能抢在模块自己的处理器前面。</b>
 * {@code @ControllerAdvice} 不写 {@code @Order} 时默认就是
 * {@code LOWEST_PRECEDENCE}，和这里一样 —— <b>并列时顺序不确定</b>，
 * 而解析器是「按顺序找第一个有可用方法的 advice」，并列就可能让这里
 * 抢走本该由模块处理的校验异常。所以给那三个已有的处理器显式加了
 * {@code @Order(0)}，让顺序变成确定的。
 *
 * <p><b>③ 不能把异常信息回给调用方。</b>
 * 异常消息里可能有 SQL、表名、内网主机名、连接串。
 * 响应体只给一句固定的话加一个 {@code traceId}，
 * 真正的细节留在日志里 —— 要查就拿 traceId 去日志系统查。
 *
 * <h3>traceId 从 MDC 取，不依赖 tracing 的 API</h3>
 * 日志格式里已经有 {@code traceId=%X{traceId}}，说明链路追踪把它放进了 MDC。
 * 直接读 MDC 而不是注入 {@code Tracer}，是为了不让 mall-common 对
 * micrometer-tracing 产生硬依赖 —— 那个依赖在这个项目里是
 * {@code <optional>true</optional>} 的，硬引会让没装它的服务起不来。
 * 取不到就是空字符串，不影响其它部分。
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class UnhandledExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(UnhandledExceptionAdvice.class);

    /**
     * 回给调用方的固定文案。<b>刻意不含任何异常细节</b>，见类注释第 ③ 条。
     */
    private static final String CLIENT_MESSAGE = "服务暂时不可用，请稍后重试";

    @ExceptionHandler(Exception.class)
    public ResponseEntity<R> handle(Exception e, HttpServletRequest request) {
        String where = request.getMethod() + " " + request.getRequestURI();
        String traceId = traceId();

        // ---- ① 有确定 HTTP 语义的异常：按它自己的状态码返回，不当成故障 ----
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            // 客户端错误用 WARN 且【不带栈】：4xx 是调用方的问题，
            // 带栈只会让日志里全是噪音，把真正的 5xx 淹掉。
            if (status.is4xxClientError()) {
                log.warn("请求被拒绝 {} status={} type={} traceId={}",
                        where, status.value(), e.getClass().getSimpleName(), traceId);
            } else {
                log.error("请求失败 {} status={} traceId={}", where, status.value(), traceId, e);
            }
            return ResponseEntity.status(status)
                    .body(R.error(status.value(), CLIENT_MESSAGE).put("traceId", traceId));
        }

        // ---- ② 真正没人处理的异常：这才是当初那 2063 个 500 ----
        // 【整条链路上唯一会打出它的地方】所以这里必须带栈。
        log.error("未处理的异常 {} traceId={}", where, traceId, e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.error(HttpStatus.INTERNAL_SERVER_ERROR.value(), CLIENT_MESSAGE)
                        .put("traceId", traceId));
    }

    /**
     * MDC 里取不到 traceId 是正常情况（没装 tracing、或者不在请求线程里），
     * 这时返回空串而不是 null —— 放进 JSON 里 null 和 "" 对调用方是两种东西，
     * 而这里想表达的是「没有」，不是「有一个空值」。
     */
    private static String traceId() {
        String id = MDC.get("traceId");
        return id == null ? "" : id;
    }
}
