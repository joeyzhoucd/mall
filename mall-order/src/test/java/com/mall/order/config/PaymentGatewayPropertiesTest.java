package com.mall.order.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 签名密钥缺失时必须启动失败，不能退回任何可推断的值。
 * "${...}" 那条是重点：@ConfigurationProperties 对解析不了的占位符会原样保留字面串（实测），
 * 只查 isBlank 会放过它。
 */
class PaymentGatewayPropertiesTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "${PAYMENT_GATEWAY_SIGN_KEY}"})
    void rejectsMissingSignKey(String signKey) {
        assertThatThrownBy(() -> new PaymentGatewayProperties("http://p", "http://n", "http://r", signKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAYMENT_GATEWAY_SIGN_KEY");
    }

    @Test
    void keepsConfiguredSignKey() {
        assertThat(new PaymentGatewayProperties("http://p", "http://n", "http://r", "real-key").signKey())
                .isEqualTo("real-key");
    }
}
