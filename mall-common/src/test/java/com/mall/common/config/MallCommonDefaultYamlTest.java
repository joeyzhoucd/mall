package com.mall.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * mall-common-default.yml / mall-mq-default.yml 的约定（2026-10-07 由 .properties 转来）。
 *
 * <p>最要紧的一条：<b>每个值都必须是字符串</b>。.properties 的值天然是字符串；YAML 会把没加引号的
 * on/off/yes/no 变成布尔、08 变成数字。所以文件里的值一律单引号 —— 新增条目忘了加引号，这里会失败
 * （负控那条证明这个检查确实能抓到）。转换当时另做过一次逐键核对：YAML 读出的键值集合和原 .properties 完全相同。
 */
class MallCommonDefaultYamlTest {

    private static List<Object> values(PropertySource<?> ps) {
        return Arrays.stream(((EnumerablePropertySource<?>) ps).getPropertyNames()).map(ps::getProperty).toList();
    }

    private static PropertySource<?> load(Resource r) throws Exception {
        return MallCommonEnvironmentPostProcessor.load(r);
    }

    @Test
    void everyValueInMallCommonDefaultIsAString() throws Exception {
        PropertySource<?> ps = load(new ClassPathResource(MallCommonEnvironmentPostProcessor.DEFAULT_PATH));
        assertThat(values(ps)).hasSizeGreaterThanOrEqualTo(55).allSatisfy(v -> assertThat(v).isInstanceOf(String.class));
        assertThat(ps.getName()).isEqualTo(MallCommonEnvironmentPostProcessor.PROPERTY_SOURCE_NAME);
    }

    @Test
    void everyValueInMallMqDefaultIsAString() throws Exception {
        // mall-mq-starter 是另一个模块；Maven 跑测试时工作目录是模块目录
        PropertySource<?> ps = load(new FileSystemResource("../mall-mq-starter/src/main/resources/mall-mq-default.yml"));
        assertThat(values(ps)).hasSize(4).allSatisfy(v -> assertThat(v).isInstanceOf(String.class));
    }

    @Test
    void postProcessorActuallyLoadsTheYamlFromTheClasspath() {
        // 文件名、加载器、属性源名字三处必须对得上：任何一处不对，默认值整份静默缺席（不报错）
        org.springframework.core.env.StandardEnvironment env = new org.springframework.core.env.StandardEnvironment();
        new MallCommonEnvironmentPostProcessor().postProcessEnvironment(env, null);
        assertThat(env.getPropertySources().contains(MallCommonEnvironmentPostProcessor.PROPERTY_SOURCE_NAME)).isTrue();
        assertThat(env.getProperty("mall.internal.enforce")).isEqualTo("false"); // ${INTERNAL_ENFORCE:false} 已解析
        // 最低优先级：在它前面的属性源（这里用系统属性）要能覆盖它
        System.setProperty("mall.internal.enforce", "true");
        try {
            assertThat(env.getProperty("mall.internal.enforce")).isEqualTo("true");
        } finally {
            System.clearProperty("mall.internal.enforce");
        }
    }

    @Test
    void knownValuesSurvivedTheConversion() throws Exception {
        PropertySource<?> ps = load(new ClassPathResource(MallCommonEnvironmentPostProcessor.DEFAULT_PATH));
        assertThat(ps.getProperty("mall.internal.enforce")).isEqualTo("${INTERNAL_ENFORCE:false}");
        assertThat(ps.getProperty("mall.admin.jwt.public-key")).isEqualTo("${JWT_PUBLIC_KEY:}");
        assertThat(ps.getProperty("mall.admin-api.enforce")).isEqualTo("${ADMIN_API_ENFORCE:false}");
    }

    @Test
    void nonAsciiValuesAreReadAsUtf8() throws Exception {
        PropertySource<?> ps = load(new ByteArrayResource("greeting: '你好，商城'\n".getBytes(StandardCharsets.UTF_8)));
        assertThat(ps.getProperty("greeting")).isEqualTo("你好，商城");
    }

    @Test
    void negativeControlAnUnquotedOffIsNotAString() throws Exception {
        PropertySource<?> ps = load(new ByteArrayResource("a.b: off\n".getBytes(StandardCharsets.UTF_8)));
        assertThat(ps.getProperty("a.b")).isNotInstanceOf(String.class);
    }
}
