package com.mall.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * 把 Long 序列化成字符串，避免超过 2^53 的 id 在 JavaScript 里丢精度
 * （前端 JSON.parse 之后 Number 只有 53 位有效整数位）。
 * <p>
 * Boot 4 迁到 Jackson 3 之后这个类有两处变化：
 * 1. Spring 的 Jackson2ObjectMapperBuilderCustomizer 已经没有了，替代品是
 *    JsonMapperBuilderCustomizer（在 spring-boot-jackson 模块里），回调参数从
 *    Spring 自己的 builder 换成了 Jackson 原生的 JsonMapper.Builder。
 * 2. 因为换成了 Jackson 原生 builder，就没有 Spring 那个方便的 serializerByType()
 *    了，得按 Jackson 的标准做法用 SimpleModule 注册序列化器。
 * <p>
 * 注意 Jackson 3 的包名是 tools.jackson.*（groupId 也从 com.fasterxml.jackson 改成
 * tools.jackson），只有注解包 com.fasterxml.jackson.annotation 为了向后兼容没有改。
 */
@AutoConfiguration
@ConditionalOnClass(JsonMapperBuilderCustomizer.class)
public class JacksonAutoConfiguration {

    @Bean
    public JsonMapperBuilderCustomizer commonJacksonCustomizer() {
        return builder -> {
            SimpleModule longToString = new SimpleModule();
            longToString.addSerializer(Long.class, ToStringSerializer.instance);
            longToString.addSerializer(Long.TYPE, ToStringSerializer.instance);
            builder.addModule(longToString);
        };
    }
}
