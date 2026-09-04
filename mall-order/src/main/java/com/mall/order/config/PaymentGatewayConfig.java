package com.mall.order.config;

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

    @Bean
    public RestClient paymentGatewayRestClient(PaymentGatewayProperties properties, RestClient.Builder builder) {
        return builder.baseUrl(properties.baseUrl()).build();
    }
}
