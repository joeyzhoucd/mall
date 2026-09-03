package com.mall.gateway.filter;

import com.mall.common.security.AdminTokenVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * 管理端鉴权：{@code /api/**} 必须带一个 mall-admin 签发的有效令牌。
 *
 * <h3>这个过滤器补的是一个已验证的漏洞（2026-09-03）</h3>
 * 在它之前，{@code mall-coupon} / {@code mall-product} / {@code mall-ware} /
 * {@code mall-member} 这些服务<b>完全没有鉴权</b> —— 它们各自只有一个
 * 「往 ThreadLocal 塞用户上下文然后无条件 {@code return true}」的拦截器，
 * 而 renren 生成器给每张表都产出了 {@code /list} {@code /save} {@code /update}
 * {@code /delete}。网关把 {@code /api/**} 路由到它们，ingress 又把网关暴露在域名上。
 * <p>
 * 实测（本地集群）：
 * <pre>
 * GET  /api/coupon/coupon/list                          无凭证 → 200 + 数据
 * POST /api/coupon/seckillpromotion/update              无凭证 → 500（打进了控制器，不是 401）
 * GET  /api/member/memberreceiveaddress/8000001/list    无凭证 → 200 + 该会员完整 PII
 * </pre>
 * 最后一条尤其严重：memberId 在路径里，换个数字就能遍历所有会员的姓名/电话/住址。
 *
 * <h3>为什么放在网关，而不是每个服务各自加</h3>
 * 业务服务都是 ClusterIP，从集群外只能经网关进来（这一点此前验证过：后端的
 * actuator 从外部打不到，因为网关的端点映射会先截住）。所以网关是唯一的入口，
 * 在这里做是一处生效、不会漏掉某个服务。
 * <p>
 * 前提是 {@code /api/**} 必须是<b>纯管理端流量</b>。原来不是 —— 前台的
 * {@code seckill.html} 会调 {@code /api/member/memberreceiveaddress/*}。
 * 那两处已经改走 {@code coupon/seckill/address/mine}（身份从会话取），
 * 所以现在 {@code /api/} 一个前缀对应一个信任域，可以整体上闸。
 *
 * <h3>刻意返回 HTTP 401 而不是「200 + code:401」</h3>
 * renren 那套前端约定是看响应体里的 {@code code}，所以历史做法是 HTTP 200 带
 * {@code code:401}。这里不跟：<b>用 200 表示鉴权失败会让它在监控里完全隐形</b> ——
 * {@code http_server_requests} 里全是 200，错误率指标看不出任何异常。
 * 401 是 4xx，不会污染那条 5xx 错误率告警，同时鉴权失败的量变得可观测。
 * 响应体里<b>同时</b>带 {@code code:401}，前端拦截器已配套改成两种都认。
 */
@Component
public class AdminAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AdminAuthFilter.class);

    /** 后台前端在请求头里带的就是这个名字（见 httpRequest.js 的请求拦截器）。 */
    static final String TOKEN_HEADER = "token";

    /** 只管这个前缀。其它路径（前台各站点、秒杀、结算）不受影响。 */
    static final String GUARDED_PREFIX = "/api/";

    /**
     * 豁免路径。<b>只有这两条</b>，而且都是「拿到令牌之前必须能访问」的：
     * 登录接口本身，和登录页要显示的验证码图片。
     */
    static final Set<String> EXEMPT_PATHS = Set.of("/api/sys/login", "/api/captcha.jpg");

    /**
     * 网关自己会往下游加的身份头。<b>必须先剥掉客户端传来的同名头</b> ——
     * 否则任何人都能伪造 {@code X-Admin-Id} 来冒充管理员，等于把刚补上的门又拆了。
     */
    static final String ADMIN_ID_HEADER = "X-Admin-Id";
    static final String ADMIN_NAME_HEADER = "X-Admin-Name";

    private final AdminTokenVerifier verifier;

    public AdminAuthFilter(
            // 属性名和 mall-admin 的 JwtService 用的是同一个，指向同一个 JWT_SECRET。
            // 【默认值必须和 mall-admin 里的那一串逐字一致】—— 两边不一致的表现是
            // 「登录成功了，但之后每个请求都 401」，而那看起来像令牌坏了，很难指向配置。
            @Value("${mall.admin.jwt.secret:local-dev-only-do-not-use-in-any-real-environment}")
            String secret) {
        // 密钥缺失/过短时直接启动失败，而不是「先跑起来再说」。
        // 网关起不来是立刻能发现并回滚的；而一个静默放行的鉴权不会有人发现。
        // 这和 mall-admin 的 JwtService 是同一个选择。
        this.verifier = new AdminTokenVerifier(secret);
    }

    @Override
    public int getOrder() {
        // 排在很前面：鉴权失败的请求不该消耗限流令牌，也不该被转发出去。
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (!path.startsWith(GUARDED_PREFIX)) {
            return chain.filter(exchange);
        }
        // CORS 预检不带自定义头，拦掉它会让浏览器把真正的请求也判为失败。
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return chain.filter(exchange);
        }
        if (EXEMPT_PATHS.contains(path)) {
            return chain.filter(exchange);
        }

        AdminTokenVerifier.Identity identity = verifier.verify(firstHeader(request, TOKEN_HEADER));
        if (identity == null) {
            log.info("管理端鉴权失败: {} {}", request.getMethod(), path);
            return reject(exchange);
        }

        ServerHttpRequest mutated = request.mutate()
                .headers(h -> {
                    h.remove(ADMIN_ID_HEADER);
                    h.remove(ADMIN_NAME_HEADER);
                    h.set(ADMIN_ID_HEADER, String.valueOf(identity.userId()));
                    if (identity.username() != null) {
                        h.set(ADMIN_NAME_HEADER, identity.username());
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private static String firstHeader(ServerHttpRequest request, String name) {
        List<String> values = request.getHeaders().get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        // 响应体同时给 code:401，兼容 renren 那套「看 body.code」的前端约定。
        byte[] body = "{\"code\":401,\"msg\":\"未登录或令牌已失效\"}".getBytes(StandardCharsets.UTF_8);
        exchange.getResponse().getHeaders().setContentLength(body.length);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /** 供测试断言用，避免测试里再抄一遍常量。 */
    static HttpHeaders identityHeaders(AdminTokenVerifier.Identity identity) {
        HttpHeaders h = new HttpHeaders();
        h.set(ADMIN_ID_HEADER, String.valueOf(identity.userId()));
        if (identity.username() != null) {
            h.set(ADMIN_NAME_HEADER, identity.username());
        }
        return h;
    }
}
