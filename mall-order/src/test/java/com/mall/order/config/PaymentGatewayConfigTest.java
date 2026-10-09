package com.mall.order.config;

import com.mall.common.security.InternalTokenInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 调 mall-payment 的 RestClient 必须带服务间内部令牌（它不走 Feign，全局拦截器管不到）。 */
class PaymentGatewayConfigTest {

    private static PaymentGatewayProperties props() {
        return new PaymentGatewayProperties("http://mall-payment:9010", "http://n", "http://r", "some-sign-key");
    }

    private static void call(RestClient client) {
        client.get().uri("/payment/mock/payments/alipay/SN1").retrieve().toBodilessEntity();
    }

    @Test
    void sendsInternalTokenOnEveryRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://mall-payment:9010/payment/mock/payments/alipay/SN1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(InternalTokenInterceptor.HEADER, "the-internal-token"))
                .andRespond(withSuccess());

        call(new PaymentGatewayConfig().paymentGatewayRestClient(props(), builder, "the-internal-token"));
        server.verify();
    }

    @Test
    void noHeaderWhenTokenNotConfigured() {
        for (String token : new String[]{"", "${INTERNAL_TOKEN}"}) {
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("http://mall-payment:9010/payment/mock/payments/alipay/SN1"))
                    .andExpect(headerDoesNotExist(InternalTokenInterceptor.HEADER))
                    .andRespond(withSuccess());

            call(new PaymentGatewayConfig().paymentGatewayRestClient(props(), builder, token));
            server.verify();
        }
    }
}
