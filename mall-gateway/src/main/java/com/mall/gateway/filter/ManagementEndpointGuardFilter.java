package com.mall.gateway.filter;

import com.mall.gateway.security.FrontendSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

/**
 * 把 {@code /actuator/**} 挡在网关之外，只放行集群内直连 pod 的访问。
 *
 * <h3>要修的漏洞（实测）</h3>
 * 2026-09-16 实测：{@code http://mall.com/actuator/health}、
 * {@code /actuator/prometheus}、{@code /actuator} 从<b>公网无鉴权</b>就能打到，
 * 全部返回 200。health 里带着 Consul leader 的内网 IP 和 config server 的文件路径，
 * prometheus 则是把全部指标直接送出去。{@code item/seckill/admin.mall.com} 同样。
 *
 * <h3>【为什么必须是 WebFilter，不能是 GlobalFilter】这一条是踩出来的</h3>
 * 第一版把这个判断写进了 {@code DefaultFrontendSecurityService.check()}，
 * 而那个是被 {@link FrontendSecurityFilter}（一个 {@code GlobalFilter}）调用的。
 * <b>部署上去之后毫无效果，外部访问依然 200。</b>
 * <p>
 * 原因：{@code GlobalFilter} 只对「被 {@code RoutePredicateHandlerMapping}
 * 接管的请求」生效，而 actuator 自己的 handler mapping <b>优先级更高</b> ——
 * {@code /actuator/**} 根本不会走到路由那一步，网关是<b>用自己的 actuator
 * 直接把它答掉的</b>。
 * <p>
 * 证据（而不是推断）：{@code http://mall.com/actuator/prometheus} 的响应里有
 * <b>25 条 {@code spring_cloud_gateway_*} 指标、0 条 {@code tomcat_sessions_*}、
 * 0 条 {@code hikaricp_*}</b>。网关是 WebFlux 栈、后端是 servlet 栈，
 * 这三个数字说明答话的就是网关本身。
 * <p>
 * {@code WebFilter} 处在 {@code FilteringWebHandler} 这一层，在
 * {@code DispatcherHandler} 做 handler mapping <b>之前</b>执行，
 * 所以它能看到包括 actuator 在内的<b>每一个</b>请求。
 *
 * <h3>【为什么不用另外两种办法】</h3>
 * <ul>
 *   <li><b>ingress 上拦</b>：要开 {@code allow-snippet-annotations}，
 *       而它在 ingress-nginx 1.11 里是<b>为 CVE 刻意关掉</b>的默认值 ——
 *       为一件小事开一个更大的口子。</li>
 *   <li><b>挪到独立管理端口</b>（Spring 的标准答案）：Consul 的健康检查走
 *       注册端口，actuator 一挪<b>全部实例变 critical</b>、Feign 整体失效。
 *       {@code mall-common-default.properties} 里有前人留的同一条警告。</li>
 * </ul>
 *
 * <h3>判据：从域名进来的一律挡，直连 pod 的一律放</h3>
 * Prometheus 是直连 pod 抓指标的（实测 Host 头是 {@code 192.168.99.194:88}），
 * 所以 <b>Host 是裸 IP 或 localhost 时放行</b>。
 * <p>
 * <b>挡过头比挡不住更隐蔽</b>：Prometheus 抓不到指标是<b>静默</b>的 ——
 * 面板变空、告警不响，看起来像「系统很安静」。
 */
@Component
public class ManagementEndpointGuardFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ManagementEndpointGuardFilter.class);

    /** IPv4 字面量。裸 IP = 集群内直连 pod 的访问方式。 */
    private static final Pattern IPV4 =
            Pattern.compile("^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");

    private final FrontendSecurityProperties properties;

    public ManagementEndpointGuardFilter(FrontendSecurityProperties properties) {
        this.properties = properties;
    }

    /**
     * 要在所有东西之前跑。这是一道拒绝规则，不该依赖别的过滤器是否先放行 ——
     * 尤其不能落在 {@code FrontendSecurityFilter} 后面：那一个是
     * {@code GlobalFilter}，对 actuator 请求压根不会被调用。
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (!blocks(path, request)) {
            return chain.filter(exchange);
        }
        // 返回 404 而不是 403：403 等于承认「这儿确实有个端点」。
        log.debug("拦下外部 actuator 访问: {} Host={}", path, request.getHeaders().getFirst(HttpHeaders.HOST));
        exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
        return exchange.getResponse().setComplete();
    }

    private boolean blocks(String path, ServerHttpRequest request) {
        // 【开关独立于 properties.isEnabled()】把整套前台风控关掉是运维动作，
        // 不该顺带把 actuator 重新暴露到公网上。两件事的开关要分开。
        if (!properties.getAccess().isBlockManagementEndpoints()) {
            return false;
        }
        if (!"/actuator".equals(path) && !path.startsWith("/actuator/")) {
            return false;
        }
        return !isDirectPodAddress(hostWithoutPort(request));
    }

    /** 取 Host 头并去掉端口。IPv6 形如 {@code [::1]:88}，要先把方括号那段整体取出来。 */
    static String hostWithoutPort(ServerHttpRequest request) {
        String host = request.getHeaders().getFirst(HttpHeaders.HOST);
        if (!StringUtils.hasText(host)) {
            // 没有 Host 头（HTTP/1.0 或构造出来的请求）——按「不是直连 pod」处理，挡掉。
            // 判断不了来源的时候宁可挡，不可放。
            return "";
        }
        host = host.trim();
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            return end > 0 ? host.substring(0, end + 1) : host;
        }
        int colon = host.indexOf(':');
        return colon >= 0 ? host.substring(0, colon) : host;
    }

    /** 裸 IP 或 localhost = 集群内直连 pod，放行。域名一律视为外部。 */
    static boolean isDirectPodAddress(String host) {
        if (!StringUtils.hasText(host)) {
            return false;
        }
        if ("localhost".equalsIgnoreCase(host)) {
            return true;
        }
        if (host.startsWith("[") && host.endsWith("]")) {   // IPv6 字面量
            return true;
        }
        return IPV4.matcher(host).matches();
    }
}
