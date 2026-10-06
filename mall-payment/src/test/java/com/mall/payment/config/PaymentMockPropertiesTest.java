package com.mall.payment.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 同 mall-order 的 PaymentGatewayPropertiesTest：缺密钥必须失败，"${...}" 字面串也算缺。 */
class PaymentMockPropertiesTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "${PAYMENT_MOCK_SIGN_KEY}"})
    void rejectsMissingSignKey(String signKey) {
        assertThatThrownBy(() -> new PaymentMockProperties(signKey, null, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PAYMENT_MOCK_SIGN_KEY");
    }

    @Test
    void keepsConfiguredSignKey() {
        assertThat(new PaymentMockProperties("real-key", null, null, null, null).signKey()).isEqualTo("real-key");
    }
}
