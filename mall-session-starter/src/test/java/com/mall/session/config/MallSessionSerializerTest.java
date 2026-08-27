package com.mall.session.config;

import com.mall.session.vo.LoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守住「会话里的对象读回来还是原来那个类型」。
 *
 * <h3>为什么必须有这条测试</h3>
 * 会话共享的整条链路只依赖一件事：{@code session.getAttribute("loginUser")} 读回来的
 * 东西是 {@link LoginUser}，而不是一个 {@code LinkedHashMap}。各服务的拦截器都写成
 * <pre>if (loginUser instanceof LoginUser) { ... }</pre>
 * 类型不对时它们<b>不会报错</b>，只是当成没登录。
 * <p>
 * 2026-08-27 压测时真的踩到了：Jackson 2 → 3 迁移把
 * {@code new GenericJackson2JsonRedisSerializer()}（无参构造，内部会开默认类型信息）
 * 换成了 {@code new GenericJacksonJsonRedisSerializer(new ObjectMapper())}
 * （裸 ObjectMapper，不开）。于是：
 * <ul>
 *   <li>编译通过；</li>
 *   <li>启动正常，没有任何异常日志；</li>
 *   <li>登录接口返回 302 跳转成功；</li>
 *   <li>Redis 里的 session 内容看起来完全正常；</li>
 *   <li>但每个需要登录态的请求都返回「请先登录」。</li>
 * </ul>
 * 全链路健康检查是绿的。管理后台走 JWT 不走 session，所以用真实浏览器验证后台
 * 登录时也没暴露出来。这就是这个仓库里反复出现的那类「编译干净、监控全绿、功能是坏的」。
 */
class MallSessionSerializerTest {

    private final MallSessionAutoConfiguration config = new MallSessionAutoConfiguration();

    @Test
    @DisplayName("生产配置的序列化器：LoginUser 往返之后仍然是 LoginUser")
    void productionSerializerPreservesType() {
        RedisSerializer<Object> serializer = config.springSessionDefaultRedisSerializer();

        LoginUser original = sample();
        byte[] bytes = serializer.serialize(original);
        Object restored = serializer.deserialize(bytes);

        // 这一条是全部要点。类型不对的话各服务的拦截器一律判定为未登录。
        assertThat(restored)
                .as("会话里的 LoginUser 读回来不是 LoginUser 了，所有需要登录态的接口都会返回「请先登录」")
                .isInstanceOf(LoginUser.class);
        assertThat((LoginUser) restored)
                .usingRecursiveComparison()
                .isEqualTo(original);
    }

    @Test
    @DisplayName("序列化结果里带类型信息，这是类型能还原的前提")
    void serializedFormCarriesTypeInformation() {
        RedisSerializer<Object> serializer = config.springSessionDefaultRedisSerializer();
        String json = new String(serializer.serialize(sample()), StandardCharsets.UTF_8);

        // 不写死 "@class" 这个字段名（它可以通过 typePropertyName 改），
        // 只要求类名出现在输出里 —— 那才是反序列化真正依赖的东西。
        assertThat(json)
                .as("序列化结果里没有类名，反序列化时 Jackson 无从知道该实例化什么: %s", json)
                .contains(LoginUser.class.getName());
    }

    @Test
    @DisplayName("阴性对照：裸 ObjectMapper 的序列化器【读不回】原类型")
    void bareObjectMapperLosesType() {
        // 这就是修复前的写法。留着它是为了证明上面两条断言【确实能发现这个错误】——
        // 否则「测试通过」可能只是因为断言太宽松，那种测试比没有更糟：
        // 它会让人以为这个风险已经被守住了。
        RedisSerializer<Object> broken = new GenericJacksonJsonRedisSerializer(new ObjectMapper());

        Object restored = broken.deserialize(broken.serialize(sample()));

        assertThat(restored)
                .as("裸 ObjectMapper 居然保留了类型信息 —— 那说明上面两条测试证明不了什么，需要重新设计")
                .isNotInstanceOf(LoginUser.class);
    }

    private static LoginUser sample() {
        LoginUser user = new LoginUser();
        user.setId(8000001L);
        user.setUsername("lt0001");
        user.setNickname("压测会员1");
        user.setMobile("13908000001");
        user.setLevelId(null);
        user.setIcon(null);
        return user;
    }
}
