package com.mall.coupon.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守住秒杀热点读走统一缓存封装、写路径提交后失效。
 *
 * <h3>2026-09-06：断言从「整个文件里出现过」改成「在这个方法里出现」</h3>
 * 第一版是 {@code relation.contains("evictRelationAfterCommit")} 这种
 * 扫整个文件的写法。它只能保证<b>五条写路径里至少有一条</b>有失效调用，
 * 而声称守的是「秒杀写入都要失效」。
 * <p>
 * 同一天在 mall-ware 那边实测确认过这个盲区是真的：
 * 从五条写路径里删掉一条（{@code orderLockStock}），
 * 同样写法的 WareHotReadCacheTest <b>照常全绿</b>。
 * <p>
 * 真实的退化几乎总是「五处里少了一处」，而不是功能被整个删光。
 * 一个只在后者才会红的断言，给的是虚假的安全感。
 *
 * <h3>这里最要紧的是 incrementSoldCount</h3>
 * 它在<b>每一次抢购成功</b>时执行。少了失效的表现是秒杀页上的已售数量停住不动，
 * 而那正是这个页面上唯一会变的数字 —— 看起来像"没人买"，
 * 而不是像"缓存没刷新"。
 */
class PromotionHotReadCacheTest {

    private static final Path SECKILL_WEB_SOURCE = Path.of(
            "src/main/java/com/mall/coupon/controller/SeckillWebController.java");
    private static final Path SECKILL_RELATION_SOURCE = Path.of(
            "src/main/java/com/mall/coupon/service/impl/SeckillSkuRelationServiceImpl.java");
    private static final Path SECKILL_SESSION_SOURCE = Path.of(
            "src/main/java/com/mall/coupon/service/impl/SeckillSessionServiceImpl.java");

    @Test
    @DisplayName("秒杀热点读必须走 MultiLevelCacheClient")
    void seckillHotReadsUseMultiLevelCache() throws IOException {
        String web = Files.readString(SECKILL_WEB_SOURCE, StandardCharsets.UTF_8);
        String relation = Files.readString(SECKILL_RELATION_SOURCE, StandardCharsets.UTF_8);
        String session = Files.readString(SECKILL_SESSION_SOURCE, StandardCharsets.UTF_8);

        assertTrue(web.contains("PromotionHotCacheInvalidator.SECKILL_PAGE_CACHE_NAME"),
                "秒杀页必须走统一缓存封装，不能用手写的 Redis JSON 缓存");
        assertTrue(methodBody(relation, "getById").contains("PromotionHotCacheInvalidator.SECKILL_RELATION_CACHE_NAME"),
                "秒杀关系 getById 必须走统一缓存封装");
        assertTrue(methodBody(session, "getById").contains("PromotionHotCacheInvalidator.SECKILL_SESSION_CACHE_NAME"),
                "秒杀场次 getById 必须走统一缓存封装");
    }

    /**
     * 秒杀关系的五条写路径<b>逐个</b>断言。
     *
     * <p>方法名写死是有意的：新增写路径时清单不会自动跟上，
     * 但那种情况本来就需要人判断该不该失效，而不是让测试悄悄放行。
     */
    @Test
    @DisplayName("秒杀关系的每条写路径都要提交后失效")
    void everyRelationWritePathEvictsAfterCommit() throws IOException {
        String relation = Files.readString(SECKILL_RELATION_SOURCE, StandardCharsets.UTF_8);

        for (String method : new String[] { "incrementSoldCount", "save", "saveBatch", "updateById", "removeByIds" }) {
            assertTrue(methodBody(relation, method).contains("evictRelation"),
                    "秒杀关系写路径 " + method + " 没有在提交后失效 relation/page 缓存 —— "
                            + "页面上的数字会停在旧值，而且不会有任何报错");
        }
    }

    @Test
    @DisplayName("秒杀场次的每条写路径都要提交后失效")
    void everySessionWritePathEvictsAfterCommit() throws IOException {
        String session = Files.readString(SECKILL_SESSION_SOURCE, StandardCharsets.UTF_8);

        for (String method : new String[] { "save", "saveBatch", "updateById", "removeByIds" }) {
            assertTrue(methodBody(session, method).contains("evictSession"),
                    "秒杀场次写路径 " + method + " 没有在提交后失效 session 缓存");
        }
    }

    /**
     * 取一个方法从签名到下一个同级方法之间的文本。
     *
     * <p>和 mall-product 的 SkuHotReadCacheTest 是同一套办法，
     * 那边已经用正向对照验证过它不会越界抓到下一个方法。
     */
    private static String methodBody(String source, String methodName) {
        int nameStart;
        int from = 0;
        while (true) {
            int candidate = source.indexOf(methodName + "(", from);
            assertTrue(candidate >= 0, "源码里找不到方法: " + methodName);

            int declarationStart = lastIndexOfDeclaration(source, candidate);
            int openingBrace = source.indexOf("{", declarationStart);
            if (declarationStart >= 0 && openingBrace > candidate) {
                nameStart = candidate;
                break;
            }
            from = candidate + methodName.length();
        }
        int start = lastIndexOfDeclaration(source, nameStart);
        return source.substring(start, nextDeclaration(source, start));
    }

    private static int lastIndexOfDeclaration(String source, int before) {
        int best = -1;
        for (String modifier : new String[] { "\n    public ", "\n    private ", "\n    protected " }) {
            best = Math.max(best, source.lastIndexOf(modifier, before));
        }
        return best;
    }

    private static int nextDeclaration(String source, int after) {
        int best = source.length();
        for (String modifier : new String[] { "\n    public ", "\n    private ", "\n    protected " }) {
            int at = source.indexOf(modifier, after + 1);
            if (at >= 0) {
                best = Math.min(best, at);
            }
        }
        return best;
    }
}
