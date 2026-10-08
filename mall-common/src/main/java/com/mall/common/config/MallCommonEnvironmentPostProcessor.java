package com.mall.common.config;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

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
 *
 * <h3>为什么是 .yml 而不是 .properties（2026-10-07）</h3>
 * 原来是 mall-common-default.properties：文件按 UTF-8 存（几百行中文注释），但 IntelliJ 等工具
 * 默认按 ISO-8859-1 打开 .properties（满屏乱码），运行时 {@code Properties.load(InputStream)} 也是
 * ISO-8859-1 —— 现在值都是 ASCII 没出事，第一个中文值就会静默变乱码。YAML 规范只有 UTF-8，两个问题一起消失。
 * 转换时逐键核对过：YAML 读出的键值集合和原 .properties 完全相同，且每个值都是 String
 * （文件里的值一律单引号，见文件头说明；{@code MallCommonDefaultYamlTest} 守着这一点）。
 * 属性源名字没变，仍然 addLast，/actuator/env 里看到的来源和优先级都和原来一样。
 */
public class MallCommonEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "mallCommonDefaultProperties";
    static final String DEFAULT_PATH = "mall-common-default.yml";

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
            environment.getPropertySources().addLast(load(resource));
        } catch (IOException ignored) {
            // 读不到就当没有这份默认值，让各服务自己的配置生效，不要因此启动失败
        }
    }

    /** 读一份单文档 YAML 成属性源（名字固定为 {@link #PROPERTY_SOURCE_NAME}）。YAML 按 UTF-8 解码。 */
    static PropertySource<?> load(Resource resource) throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(PROPERTY_SOURCE_NAME, resource);
        if (sources.size() != 1) {
            throw new IOException(DEFAULT_PATH + " 应该是单文档 YAML，实际 " + sources.size() + " 个文档");
        }
        return sources.get(0);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
