package com.mall.order.config;

import com.mall.common.security.InternalTokenInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({
        PaymentGatewayProperties.class,
        PaymentReconciliationProperties.class,
        PaymentStatementReconciliationProperties.class,
        OrderOutboxProperties.class
})
public class PaymentGatewayConfig {

    /**
     * 调 mall-payment 的客户端。它不走 Feign（RestClient 直连 base-url），所以 mall-common 的全局 Feign
     * 拦截器管不到它 —— 2026-10-08 之前这里的请求不带任何凭证，mall-payment 的建单/查单/退款接口在集群内
     * 谁都能调。这里显式带上服务间内部令牌（和 Feign 同一个属性、同一个头），下一步 mall-payment 标
     * {@code @InternalApi} 校验它。顺序不能反：先让调用方带上，再让提供方开始查，否则滚动途中对账全 401。
     *
     * <p>令牌为空（本地不配 INTERNAL_TOKEN）时不加这个头，和 Feign 拦截器的行为一致。
     */
    @Bean
    public RestClient paymentGatewayRestClient(PaymentGatewayProperties properties, RestClient.Builder builder,
                                               @Value("${mall.internal.token:}") String internalToken) {
        RestClient.Builder b = builder.baseUrl(properties.baseUrl());
        if (internalToken != null && !internalToken.isBlank() && !internalToken.contains("${")) {
            b = b.defaultHeader(InternalTokenInterceptor.HEADER, internalToken);
        }
        return b.build();
    }
}
