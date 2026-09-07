package com.mall.member.vo;

import com.mall.member.entity.MemberEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守住「后台会员接口不会漏出凭证」。
 *
 * <h3>为什么值得单独写一个测试</h3>
 * 这类问题的特点是<b>改坏了完全不报错</b>：
 * 把 MemberAdminVo 换回 MemberEntity，或者给实体加一个新字段，
 * 编译通过、接口 200、页面正常显示 —— 只是响应 JSON 里多了一列 BCrypt 哈希，
 * 而没有任何人会去看响应体的第 17 个字段。
 */
class MemberAdminVoTest {

    /**
     * 明确不能出后端的字段。
     *
     * <ul>
     *   <li>{@code password} —— BCrypt 哈希。哈希不等于安全，它是离线爆破的输入。</li>
     *   <li>{@code accessToken} —— 第三方登录令牌，拿到就能<b>直接</b>以该用户身份
     *       调那个平台的接口，不需要破解。</li>
     *   <li>{@code socialUid} / {@code expiresIn} —— 社交账号关联信息。</li>
     * </ul>
     */
    private static final Set<String> MUST_NOT_LEAK =
            new LinkedHashSet<>(Arrays.asList("password", "accessToken", "socialUid", "expiresIn"));

    @Test
    @DisplayName("VO 上不能有任何一个凭证字段")
    void voHasNoCredentialFields() {
        Set<String> voFields = fieldNames(MemberAdminVo.class);
        for (String forbidden : MUST_NOT_LEAK) {
            assertFalse(voFields.contains(forbidden),
                    "MemberAdminVo 上出现了不该外泄的字段 «" + forbidden + "»");
        }
    }

    /**
     * 实体新增字段时，这条测试<b>会失败</b> —— 这是有意的。
     *
     * <p>白名单的意义就在这里：给 MemberEntity 加一个 {@code idCardNo}，
     * 如果没人在这里做决定（要么复制进 VO、要么加进 MUST_NOT_LEAK），
     * 构建就红。反过来，如果用的是「排除黑名单」的写法，
     * 新字段会自动出现在响应里，而没有任何地方会提醒。
     *
     * <p>看到这条失败时要做的<b>不是</b>把字段名加进 MUST_NOT_LEAK 了事，
     * 而是先判断它算不算个人敏感信息。
     */
    @Test
    @DisplayName("实体的每个字段都必须被显式分类：要么在 VO 里，要么在禁止清单里")
    void everyEntityFieldIsClassified() {
        Set<String> voFields = fieldNames(MemberAdminVo.class);
        Set<String> unclassified = fieldNames(MemberEntity.class).stream()
                .filter(name -> !voFields.contains(name))
                .filter(name -> !MUST_NOT_LEAK.contains(name))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertTrue(unclassified.isEmpty(),
                "MemberEntity 上有未分类的字段：" + unclassified
                        + "。请判断它是否属于个人敏感信息："
                        + "不敏感就复制进 MemberAdminVo，敏感就加进 MUST_NOT_LEAK。"
                        + "不要因为想让构建变绿就随手加进禁止清单。");
    }

    @Test
    @DisplayName("列表项脱敏，详情不脱敏")
    void listItemIsMaskedAndDetailIsNot() {
        MemberEntity entity = sample();

        MemberAdminVo listItem = MemberAdminVo.ofListItem(entity, "黄金会员");
        assertEquals("138****5678", listItem.getMobile(), "列表里的手机号没有脱敏");
        assertTrue(listItem.getEmail().contains("*"), "列表里的邮箱没有脱敏：" + listItem.getEmail());

        MemberAdminVo detail = MemberAdminVo.ofDetail(entity, "黄金会员");
        assertEquals("13812345678", detail.getMobile(), "详情里的手机号不该脱敏");
        assertEquals("joeyzhou@example.com", detail.getEmail(), "详情里的邮箱不该脱敏");
    }

    @Test
    @DisplayName("非敏感字段要真的复制过来，不能只是「没漏」")
    void copiesTheFieldsItShouldCopy() {
        MemberEntity entity = sample();
        MemberAdminVo vo = MemberAdminVo.ofDetail(entity, "黄金会员");

        assertEquals(8000001L, vo.getId());
        assertEquals("joey", vo.getUsername());
        assertEquals("乔伊", vo.getNickname());
        assertEquals("黄金会员", vo.getLevelName(), "等级名没带上");
        assertEquals(3L, vo.getLevelId());
        assertEquals(120, vo.getIntegration());
    }

    /**
     * 等级表查不到时 levelName 为 null，而 levelId 还在。
     *
     * <p>当前 ums_member_level 是<b>空表</b>（2026-09-06 实测 0 行），
     * 所以这是线上的实际情况，不是边界情况。
     * 前端必须能回落到显示 levelId。
     */
    @Test
    @DisplayName("等级查不到时 levelName 为 null，但 levelId 必须保留")
    void keepsLevelIdWhenLevelNameIsUnknown() {
        MemberAdminVo vo = MemberAdminVo.ofListItem(sample(), null);
        assertEquals(3L, vo.getLevelId(), "levelName 查不到时 levelId 也丢了，前端就无从回落");
        assertEquals(null, vo.getLevelName());
    }

    private static MemberEntity sample() {
        MemberEntity entity = new MemberEntity();
        entity.setId(8000001L);
        entity.setLevelId(3L);
        entity.setUsername("joey");
        entity.setNickname("乔伊");
        entity.setMobile("13812345678");
        entity.setEmail("joeyzhou@example.com");
        entity.setIntegration(120);
        entity.setPassword("$2a$10$abcdefghijklmnopqrstuv");
        entity.setAccessToken("ya29.a0AfH6SMB-secret-token");
        entity.setSocialUid("wx_open_id_123");
        return entity;
    }

    /** 只看实例字段：Lombok 和 JaCoCo 都会往类里塞静态/合成字段。 */
    private static Set<String> fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .filter(f -> !f.isSynthetic())
                .map(Field::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
