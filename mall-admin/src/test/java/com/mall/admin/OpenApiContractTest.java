package com.mall.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 契约与实现的双向核对。
 *
 * <h3>为什么需要这个测试</h3>
 * openapi/admin-api.yaml 被声明为"唯一依据"，但一份没人校验的契约文件只是注释 ——
 * 实现漂移了它不会告诉你。这个测试把契约变成可执行的约束：
 * <ul>
 *   <li>契约里写了、代码里没实现 → 前端会调到 404，而契约看起来一切正常；</li>
 *   <li>代码里实现了、契约里没写 → 契约不再是真相，下一个人照它开发会踩坑。</li>
 * </ul>
 * 两个方向都要查。只查一个方向的契约测试，用不了多久就会退化成摆设。
 *
 * <h3>实现方式：反射扫编译产物，不解析源码、也不起 Spring 上下文</h3>
 * 用 {@link ClassPathScanningCandidateComponentProvider} 找到所有 &#64;RestController，
 * 再反射读方法上的映射注解，拼出"实际提供的 URL 集合"。这样：
 * <ul>
 *   <li>比正则解析源码准确（注解的值就是运行时真正生效的那个值）；</li>
 *   <li>比 &#64;SpringBootTest 便宜 —— 不需要数据库、Redis、Consul，本地和 CI 都能秒级跑完。
 *       这一点很重要：需要真实中间件的测试在本机跑不起来，最后一定会被打上跳过标记。</li>
 * </ul>
 *
 * <p>另外还检查了几条【项目特有】的契约不变量（比通用的 OpenAPI 语法校验更有价值）：
 * token 请求头的安全方案、以及 /sys/menu/list 必须是裸数组。后者是全契约里唯一一个
 * 不带信封的响应，最容易被后来加的统一返回值包装器悄悄破坏。
 */
class OpenApiContractTest {

    private static final String SPEC = "openapi/admin-api.yaml";

    /** 契约里刻意不包含、但实现上会存在的路径前缀（actuator 之类由框架提供的端点）。 */
    private static final List<String> IGNORED_PREFIXES = List.of("/actuator", "/error");

    @Test
    @DisplayName("契约文件可解析，且 operationId 唯一")
    void specIsWellFormed() {
        Map<String, Object> spec = loadSpec();
        assertNotNull(spec.get("paths"), "契约里没有 paths");

        Map<String, Object> paths = asMap(spec.get("paths"));
        Set<String> operationIds = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (Map.Entry<String, Object> path : paths.entrySet()) {
            Map<String, Object> operations = asMap(path.getValue());
            assertFalse(operations.isEmpty(), "路径 " + path.getKey() + " 下没有任何操作");
            for (Map.Entry<String, Object> op : operations.entrySet()) {
                Object id = asMap(op.getValue()).get("operationId");
                assertNotNull(id, path.getKey() + " 的 " + op.getKey() + " 缺少 operationId");
                if (!operationIds.add(String.valueOf(id))) {
                    duplicates.add(String.valueOf(id));
                }
            }
        }
        assertTrue(duplicates.isEmpty(), "operationId 重复: " + duplicates);
    }

    @Test
    @DisplayName("契约里声明的每个端点都有对应的实现")
    void everyDocumentedEndpointIsImplemented() {
        Set<String> documented = documentedEndpoints();
        Set<String> implemented = implementedEndpoints();
        List<String> missing = documented.stream()
                .filter(e -> !implemented.contains(e))
                .sorted()
                .toList();
        assertTrue(missing.isEmpty(),
                () -> System.lineSeparator()
                        + "契约里写了但没有实现的端点（前端调用会 404）:" + System.lineSeparator()
                        + String.join(System.lineSeparator(), missing));
    }

    @Test
    @DisplayName("每个实现的端点都写进了契约")
    void everyImplementedEndpointIsDocumented() {
        Set<String> documented = documentedEndpoints();
        Set<String> implemented = implementedEndpoints();
        List<String> undocumented = implemented.stream()
                .filter(e -> !documented.contains(e))
                .sorted()
                .toList();
        assertTrue(undocumented.isEmpty(),
                () -> System.lineSeparator()
                        + "实现了但契约里没写的端点（契约不再是真相）:" + System.lineSeparator()
                        + String.join(System.lineSeparator(), undocumented));
    }

    @Test
    @DisplayName("安全方案必须是名为 token 的请求头，不是 Authorization Bearer")
    void securitySchemeIsBareTokenHeader() {
        Map<String, Object> schemes = asMap(asMap(loadSpec().get("components")).get("securitySchemes"));
        assertEquals(1, schemes.size(), "只应有一个安全方案");
        Map<String, Object> scheme = asMap(schemes.values().iterator().next());
        assertEquals("apiKey", scheme.get("type"));
        assertEquals("header", scheme.get("in"));
        // 前端在 httpRequest.js 里写死了 config.headers['token']，改成别的名字就全部 401
        assertEquals("token", scheme.get("name"),
                "令牌请求头必须叫 token（前端 httpRequest.js 写死的），不能是 Authorization");
    }

    @Test
    @DisplayName("/sys/menu/list 的响应必须是裸数组，不能套信封")
    void menuListReturnsBareArray() {
        Map<String, Object> paths = asMap(loadSpec().get("paths"));
        Map<String, Object> get = asMap(asMap(paths.get("/sys/menu/list")).get("get"));
        Map<String, Object> ok = asMap(asMap(get.get("responses")).get("200"));
        Map<String, Object> schema = asMap(asMap(asMap(ok.get("content")).get("application/json")).get("schema"));
        // 前端 menu.vue 是 treeDataTranslate(data, 'menuId')，把 data 直接当数组用、连 code 都不看。
        // 套上信封会让菜单管理页和角色授权树双双静默变空。
        assertEquals("array", schema.get("type"),
                "/sys/menu/list 必须返回裸数组，套 code/msg 信封会让菜单树和角色授权树静默变空");
    }

    // ------------------------------------------------------------------

    private Set<String> documentedEndpoints() {
        Set<String> result = new TreeSet<>();
        Map<String, Object> paths = asMap(loadSpec().get("paths"));
        for (Map.Entry<String, Object> path : paths.entrySet()) {
            for (String method : asMap(path.getValue()).keySet()) {
                result.add(method.toUpperCase(Locale.ROOT) + " " + normalize(path.getKey()));
            }
        }
        return result;
    }

    /** 反射扫出所有 &#64;RestController 实际提供的端点。 */
    private Set<String> implementedEndpoints() {
        Set<String> result = new TreeSet<>();
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (BeanDefinition definition : scanner.findCandidateComponents("com.mall.admin.controller")) {
            Class<?> type;
            try {
                type = Class.forName(definition.getBeanClassName());
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException("扫到了类但加载不了: " + definition.getBeanClassName(), ex);
            }
            String base = "";
            RequestMapping classMapping = type.getAnnotation(RequestMapping.class);
            if (classMapping != null && classMapping.value().length > 0) {
                base = classMapping.value()[0];
            }
            for (Method method : type.getDeclaredMethods()) {
                for (Mapping mapping : Mapping.of(method)) {
                    String full = join(base, mapping.path());
                    if (IGNORED_PREFIXES.stream().anyMatch(full::startsWith)) {
                        continue;
                    }
                    result.add(mapping.httpMethod() + " " + normalize(full));
                }
            }
        }
        assertFalse(result.isEmpty(),
                "一个 @RestController 都没扫到 —— 说明这个测试本身失效了，"
                + "不能因此得出「契约与实现一致」的结论");
        return result;
    }

    /**
     * 把路径参数名归一化成 {} ，这样 /sys/user/info/{userId} 和 /sys/user/info/{id}
     * 被视为同一个端点。契约和代码里的参数名不必字字相同，形状一致就够；
     * 强求名字一致只会制造无意义的失败。
     */
    private static String normalize(String path) {
        return path.replaceAll("\\{[^}]*}", "{}");
    }

    private static String join(String base, String path) {
        String b = base == null ? "" : base.trim();
        String p = path == null ? "" : path.trim();
        if (!b.isEmpty() && !b.startsWith("/")) {
            b = "/" + b;
        }
        if (!p.isEmpty() && !p.startsWith("/")) {
            p = "/" + p;
        }
        String joined = (b + p).replaceAll("//+", "/");
        return joined.isEmpty() ? "/" : joined;
    }

    private Map<String, Object> loadSpec() {
        try (InputStream in = new ClassPathResource(SPEC).getInputStream()) {
            Map<String, Object> spec = new Yaml().load(in);
            assertNotNull(spec, "契约文件解析结果为空: " + SPEC);
            return spec;
        } catch (IOException ex) {
            throw new IllegalStateException("读不到契约文件 " + SPEC, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        assertNotNull(o, "期望是一个对象，实际是 null");
        assertInstanceOf(Map.class, o, "期望是一个对象，实际是 " + o.getClass());
        return (Map<String, Object>) o;
    }

    /** 一个方法上的一条映射：HTTP 方法 + 路径。 */
    private record Mapping(String httpMethod, String path) {

        static List<Mapping> of(Method method) {
            List<Mapping> result = new ArrayList<>();
            add(result, method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class) == null
                    ? null : "GET", method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class) == null
                    ? null : method.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class).value());
            add(result, method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class) == null
                    ? null : "POST", method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class) == null
                    ? null : method.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class).value());
            RequestMapping generic = method.getAnnotation(RequestMapping.class);
            if (generic != null) {
                String httpMethod = generic.method().length > 0 ? generic.method()[0].name() : "GET";
                add(result, httpMethod, generic.value());
            }
            return result;
        }

        private static void add(List<Mapping> target, String httpMethod, String[] paths) {
            if (httpMethod == null) {
                return;
            }
            if (paths == null || paths.length == 0) {
                target.add(new Mapping(httpMethod, ""));
                return;
            }
            for (String p : paths) {
                target.add(new Mapping(httpMethod, p));
            }
        }
    }
}
