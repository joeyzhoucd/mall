package com.mall.gateway;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.webflux.autoconfigure.WebFluxProperties;
import org.springframework.cloud.gateway.handler.predicate.HostRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 前台路由白名单（已知缺陷 #5，2026-10-05）。
 *
 * <p>原来前台路由按 Host 把整个服务转出去，而各服务自己不鉴权，于是后台 CRUD 和 Feign 内部接口
 * 全都能匿名打到（线上只读探测：item.mall.com 上 /product/skuinfo/delete 405、seckill.mall.com 上
 * /coupon/seckill/scheduler/activate 405、search.mall.com 上 /search/product/down 405……）。
 * 现在每条前台路由都是 Host + 明确的 Path。
 *
 * <p>这里读 application.yml 的路由，用 Spring Cloud Gateway <b>自己的</b> Host / Path / Method
 * 断言工厂构造谓词，按配置顺序取第一个匹配 —— 和网关运行时的匹配语义一致（包括
 * {@code /{skuId:[0-9]+}.html} 这种正则段、Host 的 {@code **.mall.com}）。
 * 第一张表：页面实际用到的请求必须命中预期路由；第二张表：清点出的高危接口必须一条都不命中（= 404）。
 */
class GatewayWhitelistRoutesTest {

    private static final String PREFIX = "spring.cloud.gateway.server.webflux.routes";

    record Route(String id, Predicate<ServerWebExchange> predicate) {
    }

    private static List<Route> routes;

    @BeforeAll
    static void loadRoutes() throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        PathRoutePredicateFactory pathFactory = new PathRoutePredicateFactory(new WebFluxProperties());
        HostRoutePredicateFactory hostFactory = new HostRoutePredicateFactory();
        MethodRoutePredicateFactory methodFactory = new MethodRoutePredicateFactory();
        routes = new ArrayList<>();
        for (PropertySource<?> source : sources) {
            for (int i = 0; source.getProperty(PREFIX + "[" + i + "].id") != null; i++) {
                String id = String.valueOf(source.getProperty(PREFIX + "[" + i + "].id"));
                Predicate<ServerWebExchange> all = e -> true;
                for (int j = 0; source.getProperty(PREFIX + "[" + i + "].predicates[" + j + "]") != null; j++) {
                    String def = String.valueOf(source.getProperty(PREFIX + "[" + i + "].predicates[" + j + "]"));
                    String name = def.substring(0, def.indexOf('='));
                    List<String> args = Arrays.asList(def.substring(def.indexOf('=') + 1).split(","));
                    Predicate<ServerWebExchange> p = switch (name) {
                        case "Path" -> pathFactory.apply(new PathRoutePredicateFactory.Config().setPatterns(args));
                        case "Host" -> hostFactory.apply(new HostRoutePredicateFactory.Config().setPatterns(args));
                        case "Method" -> {
                            MethodRoutePredicateFactory.Config c = new MethodRoutePredicateFactory.Config();
                            c.setMethods(args.stream().map(HttpMethod::valueOf).toArray(HttpMethod[]::new));
                            yield methodFactory.apply(c);
                        }
                        default -> throw new IllegalStateException("测试不认识的断言 " + name + "，补上再跑");
                    };
                    all = all.and(p);
                }
                routes.add(new Route(id, all));
            }
        }
    }

    /** 网关语义：按顺序第一个匹配的路由；都不匹配返回 null（网关回 404） */
    private static String route(String method, String host, String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.valueOf(method), URI.create("http://" + host + path))
                        .header("Host", host));
        return routes.stream().filter(r -> r.predicate().test(exchange)).map(Route::id).findFirst().orElse(null);
    }

    @Test
    void routesWereLoaded() {
        // 已知正例：解析器真的读到了路由（读不到时下面「必须 404」那张表会全部假绿）
        assertThat(routes).extracting(Route::id).contains("order_route", "mall_cart_route", "product_item_route",
                "mall_seckill_route", "product_site_route", "promotion_receive_route");
    }

    @ParameterizedTest(name = "{0} {1}{2} → {3}")
    @CsvSource({
            "GET,  cart.mall.com,    /cart.html,                          mall_cart_route",
            "GET,  cart.mall.com,    /addCartItem,                        mall_cart_route",
            "GET,  cart.mall.com,    /deleteItem,                         mall_cart_route",
            "GET,  cart.mall.com,    /order/confirm.html,                 order_route",
            "POST, cart.mall.com,    /order/submitOrder,                  order_route",
            "GET,  cart.mall.com,    /order/payment.html,                 order_route",
            "POST, cart.mall.com,    /order/address/add,                  order_route",
            "POST, mall.com,         /order/payments/notify,              order_route",
            "POST, cart.mall.com,    /pay/mock/notify,                    pay_mock_route",
            "GET,  mall.com,         /promotion.html,                     promotion_page_route",
            "POST, mall.com,         /coupon/promotion/receive/7,         promotion_receive_route",
            "GET,  auth.mall.com,    /login.html,                         mall_auth_route",
            "POST, auth.mall.com,    /login,                              mall_auth_route",
            "GET,  auth.mall.com,    /sms/sendcode,                       mall_auth_route",
            "GET,  search.mall.com,  /list.html,                          mall_search_route",
            "GET,  item.mall.com,    /1001.html,                          product_item_route",
            "GET,  item.mall.com,    /,                                   product_item_route",
            "GET,  mall.com,         /,                                   product_site_route",
            "GET,  seckill.mall.com, /seckill.html,                       mall_seckill_route",
            "POST, seckill.mall.com, /coupon/seckill/grab/5,              mall_seckill_route",
            "GET,  seckill.mall.com, /coupon/seckill/message/9,           mall_seckill_route",
            "POST, seckill.mall.com, /coupon/seckill/message/9/address,   mall_seckill_route",
            "GET,  seckill.mall.com, /coupon/seckill/address/mine,        mall_seckill_route",
            // 后台仍走 /api/**（JWT 由 AdminAuthFilter 校验）
            "GET,  admin.mall.com,   /api/product/category/list/tree,     product_route",
            "POST, mall.com,         /api/order/order/list,               admin_order_route",
    })
    void storefrontRequestsStillRoute(String method, String host, String path, String expected) {
        assertThat(route(method, host, path)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} {1}{2} → 404")
    @CsvSource({
            // mall-product 后台 CRUD（原来经 item.mall.com / 任意 *.mall.com 可达）
            "GET,  item.mall.com,    /product/category/list/tree",
            "POST, item.mall.com,    /product/skuinfo/delete",
            "POST, item.mall.com,    /product/skuinfo/update",
            "GET,  item.mall.com,    /product/spuinfo/list",
            "GET,  mall.com,         /product/category/list/tree",
            "GET,  item.mall.com,    /product/skuinfo/info/1001",
            "GET,  item.mall.com,    /abc.html",
            // mall-coupon（原来经 seckill.mall.com 全部可达）
            "POST, seckill.mall.com, /coupon/seckill/scheduler/activate/1",
            "POST, seckill.mall.com, /coupon/seckill/activate/1",
            "POST, seckill.mall.com, /coupon/seckill/message/9/order-created",
            "POST, seckill.mall.com, /coupon/memberprice/saveFromMap",
            "POST, seckill.mall.com, /coupon/skufullreduction/saveFromMap",
            "GET,  seckill.mall.com, /coupon/couponhistory/list",
            "POST, seckill.mall.com, /coupon/promotion/internal/use",
            "POST, seckill.mall.com, /coupon/seckillskurelation/update",
            "GET,  mall.com,         /coupon/promotion/receive/7",
            // mall-search
            "POST, search.mall.com,  /search/product/up",
            "POST, search.mall.com,  /search/product/down",
            "GET,  search.mall.com,  /search/similar",
            // mall-cart 的 Feign 接口
            "GET,  cart.mall.com,    /currentUserCartItems",
            "POST, cart.mall.com,    /deleteItems",
            // mall-order 管理接口（原来任何登录会员都能调）
            "GET,  cart.mall.com,    /order/order/list",
            "POST, cart.mall.com,    /order/order/operate",
            "GET,  cart.mall.com,    /order/order/status/abc",
            "POST, cart.mall.com,    /order/submit",
            "POST, cart.mall.com,    /order/outbox/publish",
            "GET,  cart.mall.com,    /order/internal/reco/complements/1",
            "POST, cart.mall.com,    /pay/mock/success",
            "POST, cart.mall.com,    /pay/mock/close",
            "GET,  cart.mall.com,    /pay/mock/notify",
            // mall-payment mock
            "GET,  mall.com,         /payment/mock/reconciliation",
            "POST, mall.com,         /payment/mock/refunds",
    })
    void internalAndAdminEndpointsAreNotRouted(String method, String host, String path) {
        assertThat(route(method, host, path)).as("%s %s%s 不该命中任何前台路由", method, host, path).isNull();
    }
}
