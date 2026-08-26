package com.mall.common.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.io.IOException;
import java.util.Properties;

/**
 * 把所有服务共用的那批基础设施配置（Consul 服务发现、actuator 暴露、链路追踪导出）
 * 作为"默认值"注入进环境，避免在 10 个服务的 application.yml 里各抄一遍。
 * <p>
 * 写法照抄 mall-mq-starter 里的 MallMqEnvironmentPostProcessor——这个仓库已经有这个
 * 模式了，不另造一套。关键点是用 addLast()：优先级排在最后，所以服务自己的
 * application.yml 只要写了同名 key 就能覆盖掉这里的默认值，不会互相打架。
 * <p>
 * 注意这不是配置中心的替代品。等 Config Server 上了之后，这里适合留的是"改了必须
 * 重启才生效、而且各环境都一样"的基础设施配置；真正需要按环境区分或者动态调整的
 * 应该放到 Config Server 去。
 */
public class MallCommonEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = "mallCommonDefaultProperties";
    private static final String DEFAULT_PROPERTIES_PATH = "mall-common-default.properties";

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
            // 读不到就当没有这份默认值，让各服务自己的配置生效，不要因此启动失败
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
