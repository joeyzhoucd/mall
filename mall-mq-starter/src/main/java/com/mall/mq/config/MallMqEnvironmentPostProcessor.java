package com.mall.mq.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Properties;

public class MallMqEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "mallMqDefaultProperties";
    private static final String DEFAULT_PROPERTIES_PATH = "mall-mq-default.properties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Resource resource = new ClassPathResource(DEFAULT_PROPERTIES_PATH);
        if (!resource.exists()) {
            return;
        }
        try {
            Properties properties = PropertiesLoaderUtils.loadProperties(resource);
            environment.getPropertySources().addLast(new PropertiesPropertySource(PROPERTY_SOURCE_NAME, properties));
        } catch (IOException ignored) {
            // ignore loading failure
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

