package com.mall.session.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
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

    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        return new GenericJackson2JsonRedisSerializer();
    }
}

