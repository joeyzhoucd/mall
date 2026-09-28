package com.mall.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 挡住只给服务间 Feign 用的 {@code /<service>/internal/**} 接口，外网一律 404。
 *
 * <h3>为什么需要</h3>
 * 网关路由按前缀转发：order_route 是 {@code Path=/order/**}，后台的 admin_order_route 是
 * {@code Path=/api/order/**} 并改写成 {@code /order/**}。不拦的话，mall-order 的
 * {@code /order/internal/reco/complements/{spuId}}（「搭配购买」）从公网直接可读 ——
 * 等于把商家的搭配数据整份送出去。这是已知缺陷第 5 条（内部端点公网可达）的一个新实例；
 * 那一条的系统性修法（内部标记头 + 服务端校验）另做，这里先把新加的这类路径挡住。
 *
 * <h3>和 ManagementEndpointGuardFilter 的区别：不看 Host</h3>
 * actuator 要放行「直连 pod IP」，因为 Prometheus 这么抓指标；
 * 内部接口的调用方是服务间 Feign 直连，<b>从来不经过网关</b>，所以经网关来的一律挡。
 *
 * <h3>先规范化再比较</h3>
 * 路径变形都会让朴素的 startsWith 漏掉，而后端最终仍会匹配到那个接口：
 * 连续斜杠、{@code .}/{@code ..}、{@code ;jsessionid=...} 这类路径参数（Tomcat 会剥掉）、
 * 编码过的 {@code %2F}（{@link java.net.URI#getPath()} 已解码）、大小写。
 */
@Component
public class InternalPathGuardFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(InternalPathGuardFilter.class);

    /** 新增内部接口时加到这里（小写） */
    static final List<String> BLOCKED_PREFIXES = List.of("/order/internal", "/api/order/internal");

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (!blocked(path)) {
            return chain.filter(exchange);
        }
        log.debug("拦下外部对内部接口的访问: {}", path);
        // 404 而不是 403：不承认这里有东西
        exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
        return exchange.getResponse().setComplete();
    }

    static boolean blocked(String rawPath) {
        String p = normalize(rawPath);
        for (String prefix : BLOCKED_PREFIXES) {
            if (p.equals(prefix) || p.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    /** 小写、剥路径参数、按段解析 . 与 ..、合并空段 */
    static String normalize(String path) {
        if (path == null) {
            return "/";
        }
        List<String> out = new ArrayList<>();
        for (String seg : path.split("/")) {
            int semi = seg.indexOf(';');
            if (semi >= 0) {
                seg = seg.substring(0, semi);
            }
            if (seg.isEmpty() || seg.equals(".")) {
                continue;
            }
            if (seg.equals("..")) {
                if (!out.isEmpty()) {
                    out.remove(out.size() - 1);
                }
                continue;
            }
            out.add(seg.toLowerCase(Locale.ROOT));
        }
        return "/" + String.join("/", out);
    }
}
