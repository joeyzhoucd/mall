package com.mall.session.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
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
     * 允许反序列化的类型白名单。
     * <p>
     * 开启默认类型信息之后，Redis 里的 JSON 会带上 {@code @class} 字段，反序列化时
     * Jackson 会按这个字段去实例化类。如果不加限制（builder 上那个方法就直接叫
     * {@code enableUnsafeDefaultTyping}），任何能往 Redis 写数据的人都可以指定一个
     * 任意类名，触发所谓「反序列化 gadget」—— 借用 classpath 上某些类在构造/setter
     * 里的副作用达到代码执行。这类漏洞在 Jackson 2 时代出过很多个 CVE。
     * <p>
     * 所以这里只放行本项目自己的类和必要的 JDK 容器/包装类型。Session 里除了
     * {@link com.mall.session.vo.LoginUser} 之外还有 Spring Session 自己写的
     * 时间戳等字段（Long / Integer），以及业务上可能往 session 里放的集合。
     */
    private static PolymorphicTypeValidator sessionTypeValidator() {
        return BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.mall.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .build();
    }

    /**
     * Session 存进 Redis 时用的序列化器。
     * <p>
     * <b>必须开启默认类型信息（{@code enableDefaultTyping}），否则会话共享是坏的。</b>
     * 这一条是 2026-08-27 压测时发现的真实 bug，值得完整记下来：
     * <p>
     * 迁移到 Jackson 3 时，这里原本写的是
     * <pre>new GenericJacksonJsonRedisSerializer(new ObjectMapper())</pre>
     * 因为 Jackson 3 版没有无参构造，只能自己传一个 ObjectMapper。但被替换掉的
     * Jackson 2 版<b>无参构造内部会开启默认类型信息</b>，而裸的 {@code new ObjectMapper()}
     * 不会 —— 一个「只是把类名去掉个 2」的改动，顺手把类型信息弄丢了。
     * <p>
     * 后果链条完全没有报错：登录接口照样返回 302 跳转成功，session 照样写进 Redis
     * 且内容看着完全正常（{@code {"id":8000001,"username":"lt0001"}}），
     * 但少了 {@code @class} 字段，读回来就是个 {@code LinkedHashMap}。于是
     * {@code CouponInterceptor} 里的 {@code loginUser instanceof LoginUser} 判定为 false，
     * 每一个需要登录态的请求都返回「请先登录」。
     * <p>
     * 全链路健康检查是绿的，日志里没有任何异常，Redis 里数据也在 —— 唯一的症状是
     * 「登录完还是说没登录」。而且管理后台走 JWT 不走 session，所以之前用真实浏览器
     * 验证后台登录时完全没有暴露这个问题。
     * <p>
     * {@link MallSessionSerializerTest} 就是为了让这个错误下次会直接测试失败。
     * <p>
     * 另外一个已经踩过的坑（保留原注释）：类名里没有 "2"。
     * GenericJackson2JsonRedisSerializer 是 Jackson 2 的实现，
     * GenericJacksonJsonRedisSerializer 才是 Jackson 3 的。Boot 4 默认只带 Jackson 3
     * （包名 tools.jackson.*），继续用带 2 的那个会在运行时抛
     * NoClassDefFoundError: com/fasterxml/jackson/databind/jsontype/TypeResolverBuilder。
     * 那个坑同样【编译期完全看不出来】：代码里只引用了 Spring 的序列化器类，
     * Jackson 2 的类是那个 Spring 类内部才用到的。所以 mvn clean package 全绿、
     * 镜像构建成功、直到 pod 启动创建这个 bean 那一刻才炸。
     * <p>
     * 两个坑合起来是同一个教训：<b>跨大版本迁移时，把类名/包名换对只是第一步，
     * 还要问被换掉的那个类在默认构造里替你做了什么</b>。
     */
    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        return GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(sessionTypeValidator())
                .build();
    }
}

