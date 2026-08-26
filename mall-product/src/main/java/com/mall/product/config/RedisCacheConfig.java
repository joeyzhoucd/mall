package com.mall.product.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisCacheConfig {

    private static final Duration CATEGORY_CACHE_TTL = Duration.ofHours(24);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // 类名不带 2 的才是 Jackson 3 实现（Boot 4 默认只带 Jackson 3）。带 2 的那个会在
        // 运行时抛 NoClassDefFoundError: com/fasterxml/jackson/databind/jsontype/TypeResolverBuilder，
        // 而且编译期完全看不出来——详见 mall-session-starter 里同一个坑的说明。
        GenericJacksonJsonRedisSerializer valueSerializer = new GenericJacksonJsonRedisSerializer(new ObjectMapper());

        RedisCacheConfiguration baseConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigMap = new HashMap<>();
        cacheConfigMap.put("category", baseConfig.entryTtl(CATEGORY_CACHE_TTL));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(baseConfig.entryTtl(CATEGORY_CACHE_TTL))
                .withInitialCacheConfigurations(cacheConfigMap)
                .build();
    }
}

