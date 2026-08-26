package com.mall.admin.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生成 BCrypt 口令哈希的小工具。不是测试，是放在测试源码里的一次性工具
 * （沿用本仓库已有的 DbDdlExportTest 的做法：默认跳过，需要时用系统属性打开）。
 *
 * <pre>
 * mvn test -pl mall-admin -Dtest=BCryptHashTool -Dbcrypt.generate=true -Dbcrypt.password=admin123
 * </pre>
 *
 * <p>为什么值得留在仓库里而不是临时写一段：
 * 换 admin 口令、加新的种子用户都要算这个哈希，而 BCrypt 的哈希每次都不同（自带随机盐），
 * 没法靠"记下一个字符串"复用。放在这里，下一个人不用再去拼 classpath。
 *
 * <p>它会在打印之后【立刻用同一个编码器验一遍】。这一步不能省：
 * 生成一个自己都验不过的哈希、还照样写进种子数据，会变成"密码怎么都不对"的诡异问题，
 * 而那时候已经很难想到是哈希生成环节出了错。
 */
class BCryptHashTool {

    @Test
    @EnabledIfSystemProperty(named = "bcrypt.generate", matches = "true")
    void generate() {
        String raw = System.getProperty("bcrypt.password", "admin123");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode(raw);
        System.out.println("password = " + raw);
        System.out.println("bcrypt   = " + hash);
        assertTrue(encoder.matches(raw, hash), "生成的哈希自校验失败，不要使用这个结果");
    }
}
