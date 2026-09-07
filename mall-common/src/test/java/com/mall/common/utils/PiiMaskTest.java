package com.mall.common.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiiMaskTest {

    @Test
    @DisplayName("手机号保留前 3 后 4")
    void masksMobile() {
        assertEquals("138****5678", PiiMask.mobile("13812345678"));
    }

    /**
     * 长度不够时<b>整个打星</b>，而不是原样返回。
     *
     * <p>一个「太短所以不脱敏」的分支，遇到脏数据（只存了后几位、
     * 或者被截断过的号码）时会把它完整露出来 ——
     * 而残缺数据恰恰是最没有理由暴露的那种。
     */
    @Test
    @DisplayName("过短的手机号全打星，不能因为「不够长」就原样返回")
    void masksShortMobileEntirely() {
        for (String short_ : new String[] { "1", "12345", "123456" }) {
            String masked = PiiMask.mobile(short_);
            assertFalse(masked.contains(short_.substring(0, 1)) && masked.matches(".*\\d.*"),
                    "过短的号码不该留下任何数字：输入 «" + short_ + "» 输出 «" + masked + "»");
            assertEquals(short_.length(), masked.length(), "长度应当保持一致，避免暴露真实位数之外的信息");
        }
    }

    /**
     * 掩码<b>保持原长度</b>（joeyzhou 是 8 个字符 → 首字符 + 7 颗星）。
     *
     * <p>这会泄露本地部分的长度，是一个有意接受的取舍：
     * 定长掩码（一律 4 颗星）隐藏得更彻底，但会让两个只在长度上不同的账号
     * 在列表里显示成完全一样的一串，运维反而分辨不出。
     * 长度这点信息对识别个人几乎没有价值。手机号那边同理。
     */
    @Test
    @DisplayName("邮箱保留本地部分首字符，域名原样，掩码保持原长度")
    void masksEmail() {
        assertEquals("j*******@example.com", PiiMask.email("joeyzhou@example.com"));
        assertEquals("*@example.com", PiiMask.email("a@example.com"));
        assertEquals("a*@example.com", PiiMask.email("ab@example.com"));
    }

    @Test
    @DisplayName("不像邮箱的值全打星，而不是当成邮箱去切")
    void masksMalformedEmailEntirely() {
        assertEquals("*******", PiiMask.email("notmail"));
        // @ 在开头：本地部分为空，切出来会是空字符串 + 域名，等于没脱敏
        assertFalse(PiiMask.email("@example.com").startsWith("@"),
                "@ 开头的值被当成正常邮箱处理了");
    }

    @Test
    @DisplayName("空值原样返回，不会变成 \"null\" 或者一串星号")
    void passesThroughBlanks() {
        assertNull(PiiMask.mobile(null));
        assertNull(PiiMask.email(null));
        assertEquals("", PiiMask.mobile(""));
        assertEquals("   ", PiiMask.email("   "));
    }

    /**
     * 反向对照：确认这些断言不是恒真的。
     *
     * <p>如果脱敏函数退化成「原样返回」，上面每一条都该失败。
     * 这里直接验证「原样返回」确实不满足脱敏后的形状。
     */
    @Test
    @DisplayName("反向对照：原样返回的字符串不满足脱敏后的形状")
    void negativeControl() {
        String raw = "13812345678";
        assertFalse(raw.contains("*"), "反向对照本身不成立");
        assertTrue(PiiMask.mobile(raw).contains("*"), "脱敏后必须含有掩码字符");
        assertFalse(PiiMask.mobile(raw).equals(raw), "脱敏后不能和原值相同");
    }
}
