package com.mall.mq.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

/**
 * mall-mq 的默认连接参数（最低优先级，服务自己的配置可覆盖）。
 * 2026-10-07 由 mall-mq-default.properties 改为 .yml，原因同 mall-common 的
 * MallCommonEnvironmentPostProcessor（.properties 被工具和 Properties.load 按 ISO-8859-1 读）。
 * 属性源名字不变。
 */
public class MallMqEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "mallMqDefaultProperties";
    private static final String DEFAULT_PATH = "mall-mq-default.yml";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Resource resource = new ClassPathResource(DEFAULT_PATH);
        if (!resource.exists()) {
            return;
        }
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(PROPERTY_SOURCE_NAME, resource);
            if (sources.size() == 1) {
                environment.getPropertySources().addLast(sources.get(0));
            }
        } catch (IOException ignored) {
            // ignore loading failure
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
