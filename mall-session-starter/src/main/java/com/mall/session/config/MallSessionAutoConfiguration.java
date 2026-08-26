package com.mall.session.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Mall Session 自动配置
 */
@Configuration
@EnableConfigurationProperties(MallSessionProperties.class)
@ConditionalOnProperty(prefix = "mall.session", name = "enabled", havingValue = "true")
@EnableRedisHttpSession
public class MallSessionAutoConfiguration {

    @Bean
    public CookieSerializer cookieSerializer(MallSessionProperties properties) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(properties.getCookieName());
        serializer.setDomainName(properties.getDomain());
        return serializer;
    }

    /**
     * Session 存进 Redis 时用的序列化器。
     * <p>
     * 注意类名里没有 "2"：GenericJackson2JsonRedisSerializer 是 Jackson 2 的实现，
     * GenericJacksonJsonRedisSerializer 才是 Jackson 3 的。Boot 4 默认只带 Jackson 3
     * （包名 tools.jackson.*），继续用带 2 的那个会在运行时抛
     * NoClassDefFoundError: com/fasterxml/jackson/databind/jsontype/TypeResolverBuilder。
     * <p>
     * 这个坑特别值得记：它【编译期完全看不出来】。因为代码里只引用了 Spring 的
     * 序列化器类，Jackson 2 的类是那个 Spring 类内部才用到的，编译器不需要它在
     * classpath 上就能通过。所以 mvn clean package 全绿、镜像构建成功、直到 pod
     * 真正启动创建这个 bean 的那一刻才炸。跨大版本迁移时"编译通过"远不等于"能跑"。
     */
    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        // Jackson 3 版的这个序列化器没有无参构造，必须显式传一个 ObjectMapper
        // （注意是 tools.jackson.databind.ObjectMapper，不是 com.fasterxml 那个）。
        return new GenericJacksonJsonRedisSerializer(new ObjectMapper());
    }
}

