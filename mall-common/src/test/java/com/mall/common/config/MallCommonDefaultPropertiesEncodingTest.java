package com.mall.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * mall-common-default.properties 是 UTF-8 存的，必须按 UTF-8 读（2026-10-07）。
 * 负控那一条证明旧写法（loadProperties(resource)，ISO-8859-1）确实会把中文值读坏 ——
 * 否则「新写法读对了」这条可能只是因为测试值碰巧是 ASCII。
 */
class MallCommonDefaultPropertiesEncodingTest {

    private static final ByteArrayResource CHINESE_VALUE =
            new ByteArrayResource("greeting=你好，商城\n".getBytes(StandardCharsets.UTF_8));

    @Test
    void utf8LoaderKeepsNonAsciiValues() throws Exception {
        assertThat(MallCommonEnvironmentPostProcessor.loadUtf8(CHINESE_VALUE).getProperty("greeting"))
                .isEqualTo("你好，商城");
    }

    @Test
    void negativeControlTheOldLoaderGarblesTheSameValue() throws Exception {
        Properties legacy = PropertiesLoaderUtils.loadProperties(CHINESE_VALUE);
        assertThat(legacy.getProperty("greeting")).isNotEqualTo("你好，商城");
    }

    @Test
    void realFileStillLoadsAndKeysAreUnchanged() throws Exception {
        ClassPathResource real = new ClassPathResource("mall-common-default.properties");
        Properties utf8 = MallCommonEnvironmentPostProcessor.loadUtf8(real);
        Properties legacy = PropertiesLoaderUtils.loadProperties(real);
        // 现在所有值都是 ASCII：换成 UTF-8 读取不改变任何一个键或值
        assertThat(utf8).isEqualTo(legacy);
        assertThat(utf8.getProperty("mall.internal.enforce")).isEqualTo("${INTERNAL_ENFORCE:false}");
    }
}
