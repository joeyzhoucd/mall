package com.mall.search;

import com.mall.common.config.ConfigMetadataChecker;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 拿本模块 classpath 上所有依赖自带的配置元数据，校验本模块的配置文件。
 * <p>
 * 为什么每个模块都要有一份、不能只在一个地方统一跑：同一个属性在不同模块的有效性不同。
 * 比如 spring.cloud.gateway.server.webflux.* 只在 mall-gateway 有效（只有它依赖网关 starter），
 * 放到别的模块就是未知属性。校验必须发生在【各自的 classpath】上才有意义。
 * <p>
 * 这个测试守的是 Boot 2.7 到 Boot 4 迁移里最难查的一类问题：属性名变了，写旧名字
 * 不报错也不告警，只是那段配置完全不生效（pod 全绿、健康检查通过、功能坏掉）。
 * 详见 ConfigMetadataChecker 的类注释，那里列了本次实际踩到的 5 个。
 * <p>
 * 它是纯粹的静态检查，不启动 Spring 上下文，所以不需要数据库、Redis、MQ，
 * 本地和 CI 都能秒级跑完。
 */
class ConfigMetadataTest {

    @Test
    void 配置文件里不应出现已失效或不存在的属性() {
        List<ConfigMetadataChecker.Problem> problems = ConfigMetadataChecker.check("application.yml", "mall-common-default.properties");
        assertTrue(problems.isEmpty(), () -> System.lineSeparator()
                + "发现 " + problems.size() + " 个有问题的配置项："
                + System.lineSeparator()
                + problems.stream().map(Object::toString).collect(Collectors.joining(System.lineSeparator()))
                + System.lineSeparator()
                + "DEPRECATED_ERROR = 元数据里标了 deprecation.level=error，绑定层面已经不认这个名字。"
                + System.lineSeparator()
                + "UNKNOWN = 元数据里根本没有这个属性，通常是被重命名且没留废弃记录，或者拼错。");
    }
}
