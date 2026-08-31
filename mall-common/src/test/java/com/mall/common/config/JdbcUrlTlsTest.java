package com.mall.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 守住数据库连接必须走 TLS。
 *
 * <h3>为什么需要这条</h3>
 * 六个服务原来的 JDBC URL 里都带 {@code useSSL=false}。实测确认过三件事：
 * <ul>
 *   <li>这个参数<b>是生效的</b>（等价 {@code sslMode=DISABLED}），连接确实是明文
 *       —— {@code Ssl_cipher} 和 {@code Ssl_version} 都是空；</li>
 *   <li>而服务端<b>本来就支持 TLS 1.3</b>：主库和从库都是 {@code have_ssl=YES}、
 *       {@code tls_version=TLSv1.2,TLSv1.3}，带自签服务端证书。同一个 URL 去掉那个参数，
 *       立刻协商出 {@code TLSv1.3 / TLS_AES_256_GCM_SHA384}；</li>
 *   <li>也就是说那行参数<b>主动关掉了一个免费的加密</b>，让凭据和每一行数据在集群内明文传输。</li>
 * </ul>
 *
 * <h3>为什么靠测试守，而不是靠注释</h3>
 * {@code useSSL=false} 是 MySQL 教程里的默认写法，几乎所有示例都这么抄
 * （它最初是为了消掉旧驱动的一条 SSL 警告）。新增一个连库的服务时，
 * 从别处复制一段 URL 过来是最自然的动作，而复制来的那段大概率就带着它。
 * 这不是「有人会故意改回去」，是「不盯着就会自己长回来」。
 *
 * <h3>顺带说明一个【不该采纳】的修法</h3>
 * MySQL 8 的 {@code caching_sha2_password} 在服务端认证缓存为空时要求客户端取 RSA 公钥，
 * 而驱动默认 {@code allowPublicKeyRetrieval=false} 会拒绝。网上最常见的答案是把它打开。
 * <b>那个方向是反的</b>：它保持明文，还允许在未加密信道上取公钥 ——
 * 正是驱动那个默认值要防的中间人攻击。走 TLS 之后密码可以安全传输，压根不需要取公钥，
 * 原问题自动消失。所以这里也断言不出现 {@code allowPublicKeyRetrieval=true}。
 *
 * <h3>实现方式</h3>
 * 按文件系统扫同级模块的 application.yml（和 {@link AutoConfigurationSignatureTest}
 * 读 {@code pom.xml} 一样，surefire 的工作目录是模块 basedir，{@code ../} 就是 reactor 根）。
 * 不起 Spring 上下文 —— 这条约束是关于「配置文件里写了什么」，跟运行时无关，
 * 而需要中间件的测试在本机跑不起来，最后一定会被打上跳过标记。
 */
class JdbcUrlTlsTest {

    /** 只取 jdbc:mysql 那一行，避免匹配到注释里提到的示例。 */
    private static final Pattern JDBC_LINE = Pattern.compile("^\s*url:\s*(jdbc:mysql:[^\s]+)\s*$", Pattern.MULTILINE);

    private record Url(String module, String value) {
    }

    private static List<Url> collect() throws IOException {
        Path root = Path.of("..").toAbsolutePath().normalize();
        List<Url> found = new ArrayList<>();
        try (Stream<Path> mods = Files.list(root)) {
            for (Path mod : mods.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("mall-")).toList()) {
                Path yml = mod.resolve("src/main/resources/application.yml");
                if (!Files.exists(yml)) {
                    continue;
                }
                Matcher m = JDBC_LINE.matcher(Files.readString(yml, StandardCharsets.UTF_8));
                while (m.find()) {
                    found.add(new Url(mod.getFileName().toString(), m.group(1)));
                }
            }
        }
        return found;
    }

    @Test
    @DisplayName("扫描本身是有效的：必须真的找到若干条 jdbc:mysql URL")
    void scannerActuallyFindsUrls() throws IOException {
        // 阳性对照。没有这条的话，路径写错、正则失配都会让下面两条测试"通过"——
        // 一个扫不到任何东西的检查永远是绿的，比没有检查更糟。
        List<Url> urls = collect();
        assertThat(urls)
                .as("一条 jdbc:mysql URL 都没扫到，说明扫描逻辑坏了（路径或正则），不是真的没有")
                .hasSizeGreaterThanOrEqualTo(6);
    }

    @Test
    @DisplayName("不允许 useSSL / sslMode=DISABLED —— 那会让连接退回明文")
    void noPlaintextDatabaseConnections() throws IOException {
        for (Url u : collect()) {
            assertThat(u.value())
                    .as("%s 的 JDBC URL 里出现了 useSSL。这个参数已被 sslMode 取代，"
                            + "而且写 useSSL=false 会让连接退回明文 —— 服务端是支持 TLS 1.3 的。", u.module())
                    .doesNotContain("useSSL");
            assertThat(u.value())
                    .as("%s 显式禁用了 TLS", u.module())
                    .doesNotContain("sslMode=DISABLED");
            assertThat(u.value())
                    .as("%s 的 JDBC URL 没有指定 sslMode。不指定时驱动默认是 PREFERRED，"
                            + "服务端不支持 TLS 就【静默退回明文】—— 要的是明确的 REQUIRED。", u.module())
                    .contains("sslMode=");
        }
    }

    @Test
    @DisplayName("不允许 allowPublicKeyRetrieval=true —— 修的方向是反的")
    void noPublicKeyRetrieval() throws IOException {
        for (Url u : collect()) {
            assertThat(u.value())
                    .as("%s 打开了 allowPublicKeyRetrieval。这是 caching_sha2_password 报错时"
                            + "最常见的搜索结果，但方向是反的：它保持明文、还允许在未加密信道上取 RSA 公钥，"
                            + "正是驱动默认拒绝要防的中间人攻击。走 TLS 就不需要它。", u.module())
                    .doesNotContain("allowPublicKeyRetrieval=true");
        }
    }
}
