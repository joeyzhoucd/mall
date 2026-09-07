package com.mall.common.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全仓库扫描：每个分页查询的 ORDER BY 都必须落到一个<b>唯一</b>列上。
 *
 * <h3>为什么要在 CI 里守，而不是靠定期人工扫</h3>
 * 这个缺陷来自 renren 生成器的模板 —— 它<b>从来没有生成过 ORDER BY</b>。
 * 所以它不是"修一次就完了"的问题：每新建一张表、每跑一次生成器，
 * 就会再复制一份出来。2026-09-06 一次性修了 45 处，
 * 而如果没有这个测试，下一个新实体又会带回来一处，
 * 并且要等到那张表的数据超过一页才有人发现。
 *
 * <h3>没有确定排序的后果（不是理论，实测过）</h3>
 * 2026-09-04 在运行中的集群上测过 {@code /product/spuinfo/list}：
 * 它只按 create_time 倒序，而 create_time 远不唯一 ——
 * 取 500 条只有 5 个不同的时间戳，最挤的一个上面压着 121 行。
 * 每页 20 条时，<b>第 1 页和第 2 页有 8 行是重复的</b>。
 * 也就是翻商品列表会看到同一个商品两次，同时另一些商品一次都看不到。
 * <p>
 * 数据少于一页时完全正常，所以这类问题能潜伏很久，
 * 而且暴露时没人会把「列表里的商品对不上」联想到缺一个 ORDER BY。
 *
 * <h3>这个测试和 mall-deploy/tools/audit-paging-order.js 是同一套判定</h3>
 * 那个脚本用来在本地扫和出报表（带表格输出），这个测试用来在 CI 上闸。
 * 两边的规则刻意保持一致：跟一层同文件的私有拼装方法、
 * 认字符串和 Lambda 两种 orderBy 写法、把「以 id 结尾」当作唯一列。
 * 判定不一致的话，本地报表和 CI 结论会打架，最后两个都不被信任。
 */
class PagingOrderStabilityTest {

    /**
     * 唯一列的判断是启发式的：认「就叫 id」或「以 _id 结尾」的列。
     *
     * <p><b>已知局限，故意留着</b>：认不出用业务唯一键（比如 order_sn）收尾的情况，
     * 那种会被误判成不稳定。宁可误报也不漏报 ——
     * 漏报的代价是一个悄悄错乱的列表，误报的代价只是有人来看一眼、
     * 确认它其实是唯一的，然后把排序改成用主键收尾（那样更明确）。
     */
    private static final Pattern LOOKS_UNIQUE = Pattern.compile("(^|_)id$", Pattern.CASE_INSENSITIVE);

    private static final Pattern QUERY_PAGE = Pattern.compile(
            "public\\s+PageUtils\\s+queryPage\\s*\\([^)]*\\)\\s*\\{([\\s\\S]*?)\\n {4}\\}");
    private static final Pattern STRING_ORDER = Pattern.compile(
            "orderBy(?:Asc|Desc)\\(\\s*\"([^\"]+)\"");
    private static final Pattern LAMBDA_ORDER = Pattern.compile(
            "orderBy(?:Asc|Desc)\\(\\s*\\w+::get(\\w+)");

    @Test
    @DisplayName("所有分页查询的排序都必须以唯一列收尾")
    void everyPagedQueryHasATotalOrder() throws IOException {
        Map<String, String> unstable = new LinkedHashMap<>();
        int scanned = 0;

        for (Path file : serviceImplFiles()) {
            String src = stripComments(Files.readString(file, StandardCharsets.UTF_8));
            Matcher m = QUERY_PAGE.matcher(src);
            if (!m.find()) {
                continue;
            }
            String body = withHelperBodies(src, m.group(1));
            if (!body.contains(".page(")) {
                continue;
            }
            scanned++;

            List<String> orders = orderColumns(body);
            String last = orders.isEmpty() ? null : orders.get(orders.size() - 1);
            if (last == null || !LOOKS_UNIQUE.matcher(last).find()) {
                unstable.put(file.getFileName().toString(),
                        orders.isEmpty() ? "(没有 ORDER BY)" : String.join(", ", orders));
            }
        }

        // 先确认扫到了东西 —— 一个什么都没扫到的扫描器永远是绿的。
        // 这是这个测试最容易失效的方式：目录结构一变，它就静默地什么都不查了。
        assertTrue(scanned >= 50,
                "只扫到 " + scanned + " 个分页查询，明显偏少 —— "
                        + "很可能是仓库根目录定位或模块目录名变了，导致这个测试实际上什么都没查。"
                        + "先修扫描范围，不要改这个下限。");

        assertTrue(unstable.isEmpty(),
                "以下分页查询的排序不确定（翻页会重复或漏行），共 " + unstable.size() + " 处：\n"
                        + unstable.entrySet().stream()
                                .map(e -> "  " + e.getKey() + "  ->  " + e.getValue())
                                .reduce("", (a, b) -> a + b + "\n")
                        + "修法：在 wrapper 的 ORDER BY 末尾补一个唯一列（通常是主键），例如\n"
                        + "    wrapper.orderByDesc(\"create_time\").orderByDesc(\"id\");\n"
                        + "只加业务排序列是不够的 —— 该列相同的行之间顺序仍然未定义。");
    }

    /**
     * 反向对照：确认上面那条断言真的能失败。
     *
     * <p>手工构造两个「退化」的方法体 —— 一个没有 ORDER BY，
     * 一个只按非唯一列排 —— 它们必须都被判成不稳定。
     * 否则这个扫描器就是个永远通过的摆设。
     */
    @Test
    @DisplayName("反向对照：没有排序、和只按非唯一列排序，都必须被判为不稳定")
    void negativeControl() {
        String noOrder = "IPage<XEntity> page = this.page(q, new QueryWrapper<>());";
        assertTrue(orderColumns(noOrder).isEmpty(), "反向对照本身不成立：没有 orderBy 却解析出了排序列");

        String businessOnly = "wrapper.orderByDesc(\"create_time\"); this.page(q, wrapper);";
        List<String> orders = orderColumns(businessOnly);
        assertEquals(List.of("create_time"), orders);
        assertFalse(LOOKS_UNIQUE.matcher(orders.get(orders.size() - 1)).find(),
                "反向对照本身不成立：create_time 被当成了唯一列");

        // 正向：以 id 收尾必须被判为稳定
        String fixed = "wrapper.orderByDesc(\"create_time\").orderByDesc(\"id\");";
        List<String> fixedOrders = orderColumns(fixed);
        assertTrue(LOOKS_UNIQUE.matcher(fixedOrders.get(fixedOrders.size() - 1)).find(),
                "以 id 收尾却没被判为稳定");

        // Lambda 写法也要认，否则已经修好的会被误报
        String lambda = "wrapper.orderByDesc(XEntity::getAttrGroupId);";
        assertEquals(List.of("attr_group_id"), orderColumns(lambda),
                "Lambda 形式的 orderBy 没有被识别，会把修好的代码误报成不稳定");
    }

    // -----------------------------------------------------------------------

    private static List<String> orderColumns(String body) {
        List<String> orders = new ArrayList<>();
        Matcher s = STRING_ORDER.matcher(body);
        while (s.find()) {
            orders.add(s.group(1));
        }
        Matcher l = LAMBDA_ORDER.matcher(body);
        while (l.find()) {
            // getAttrGroupId -> attr_group_id，和列名对齐
            orders.add(l.group(1).replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase());
        }
        return orders;
    }

    /**
     * 把 queryPage 调用到的<b>同文件私有方法</b>的方法体也纳进来。
     *
     * <p>必须做这一层：BrandServiceImpl / OrderServiceImpl / MemberServiceImpl
     * 都把条件和排序抽到了私有的 buildQueryWrapper 里。只看 queryPage 会把它们
     * 全部误报成「没有 ORDER BY」，而一份混着假阳性的清单很快就没人看了。
     *
     * <p>只跟一层 —— 再深就该怀疑那个方法在做太多事。
     */
    private static String withHelperBodies(String fullSrc, String body) throws IOException {
        StringBuilder merged = new StringBuilder(body);
        List<String> seen = new ArrayList<>(List.of("queryPage", "page", "get", "getPage"));
        Matcher calls = Pattern.compile("\\b([a-z]\\w*)\\s*\\(").matcher(body);
        while (calls.find()) {
            String name = calls.group(1);
            if (seen.contains(name)) {
                continue;
            }
            seen.add(name);
            String helperBody = extractMethod(fullSrc, name);
            if (helperBody != null && !body.contains(helperBody)) {
                merged.append('\n').append(helperBody);
            }
        }

        // 再跟一层【另一个类】的静态方法。
        //
        // 订单/库存的 Outbox 和消费幂等四个服务把拼装抽到了 mall-common 的
        // MqAdminQuery（四个服务共用一份），排序写在那里。不跟这一层的话
        // 这四个会被报成「没有 ORDER BY」—— 而它们是对的，
        // MqAdminQueryTest 里有专门守排序的断言。
        //
        // 这一层是补上的：第一版只跟同文件私有方法，实测报出 4 个假阳性。
        // 混着假阳性的清单会让人开始怀疑整张表，最后干脆不看。
        // audit-paging-order.js 里也做了同样的一层，两边保持一致。
        Matcher staticCalls = Pattern.compile("\\b([A-Z]\\w*)\\.([a-z]\\w*)\\s*\\(").matcher(body);
        while (staticCalls.find()) {
            String key = staticCalls.group(1) + "." + staticCalls.group(2);
            if (seen.contains(key)) {
                continue;
            }
            seen.add(key);
            Path classFile = findClassFile(staticCalls.group(1));
            if (classFile == null) {
                // 找不到对应文件说明是 JDK 或第三方的调用（Integer.valueOf 之类）。
                // 这条查找本身就是过滤器。
                continue;
            }
            String classSrc = stripComments(Files.readString(classFile, StandardCharsets.UTF_8));
            String helperBody = extractMethod(classSrc, staticCalls.group(2));
            if (helperBody != null && !body.contains(helperBody)) {
                merged.append('\n').append(helperBody);
            }
        }
        return merged.toString();
    }

    /** 取一个方法的方法体（到行首 4 空格 + 右大括号为止）。找不到返回 null。 */
    private static String extractMethod(String src, String methodName) {
        Matcher m = Pattern.compile(
                "\\b" + Pattern.quote(methodName) + "\\s*\\([^)]*\\)\\s*\\{([\\s\\S]*?)\\n {4}\\}")
                .matcher(src);
        return m.find() ? m.group(1) : null;
    }

    /** 按类名找源文件。按文件名索引，同名类会互相覆盖 —— 已知的粗糙之处。 */
    private static Path findClassFile(String className) throws IOException {
        return classFileIndex().get(className + ".java");
    }

    private static Map<String, Path> classIndex;

    private static Map<String, Path> classFileIndex() throws IOException {
        if (classIndex == null) {
            classIndex = new LinkedHashMap<>();
            Path repoRoot = Path.of("").toAbsolutePath().getParent();
            try (Stream<Path> walk = Files.walk(repoRoot)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".java"))
                        .filter(p -> !p.toString().contains(java.io.File.separator + "target" + java.io.File.separator))
                        .forEach(p -> classIndex.put(p.getFileName().toString(), p));
            }
        }
        return classIndex;
    }

    /**
     * 去掉注释再匹配。
     *
     * <p>不做这一步的话，注释里写着「原来只有 orderByDesc("create_time")」
     * 会被当成真代码 —— audit-paging-order.js 第一版就栽在这里，
     * SpuInfoServiceImpl 的排序键被报成 create_time, create_time, id。
     * 一个把注释当代码的扫描器，给出的每个结论都不可信。
     */
    private static String stripComments(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
    }

    /**
     * 扫描范围：仓库里所有模块的 {@code *ServiceImpl.java}。
     *
     * <p>测试的工作目录是模块目录（mall-common），所以往上一级就是仓库根。
     * 显式校验根目录下确实有 mall-common —— 定位错了就直接失败，
     * 而不是扫到 0 个文件然后"通过"。
     */
    private static List<Path> serviceImplFiles() throws IOException {
        Path repoRoot = Path.of("").toAbsolutePath().getParent();
        assertTrue(repoRoot != null && Files.isDirectory(repoRoot.resolve("mall-common")),
                "定位不到仓库根目录（期望当前目录的上一级下面有 mall-common），实际上一级是 " + repoRoot);

        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(repoRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("ServiceImpl.java"))
                    .filter(p -> !p.toString().contains(java.io.File.separator + "target" + java.io.File.separator))
                    .forEach(files::add);
        }
        return files;
    }
}
