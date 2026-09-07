package com.mall.ware.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守住库存热点读走统一缓存封装、写路径提交后失效。
 *
 * <h3>2026-09-06：断言从「整个文件里出现过」改成「在这个方法里出现」</h3>
 * 第一版用的是 {@code source.contains("...evictSkuAfterCommit")} ——
 * 扫整个文件。它<b>只能保证五条写路径里至少有一条有失效调用</b>，
 * 而声称守的是「库存变更都要失效」。
 * <p>
 * 实测确认过这个盲区：把 {@code orderLockStock} 里那一句删掉
 * （剩下四处不动），两条测试<b>照常全绿</b>。而那恰恰是最要紧的一条 ——
 * 锁库存改的是 {@code stock_locked}，不失效的话可售量会一直显示旧值。
 * <p>
 * 一个"至少有一处"的断言给的是虚假的安全感：它在功能被删光时才会红，
 * 而真实的退化几乎总是「五处里少了一处」。
 */
class WareHotReadCacheTest {

    private static final Path WARE_SKU_SOURCE = Path.of(
            "src/main/java/com/mall/ware/service/impl/WareSkuServiceImpl.java");
    private static final Path WARE_SKU_CONTROLLER_SOURCE = Path.of(
            "src/main/java/com/mall/ware/controller/WareSkuController.java");
    private static final Path STOCK_ATOMIC_OPS_SOURCE = Path.of(
            "src/main/java/com/mall/ware/service/StockAtomicOps.java");

    @Test
    @DisplayName("库存热点读必须走 MultiLevelCacheClient，且要在对应的方法里")
    void stockHotReadsUseMultiLevelCache() throws IOException {
        String source = Files.readString(WARE_SKU_SOURCE, StandardCharsets.UTF_8);
        String controller = Files.readString(WARE_SKU_CONTROLLER_SOURCE, StandardCharsets.UTF_8);

        assertTrue(methodBody(source, "listBySkuId").contains("WareHotCacheInvalidator.WARE_SKU_BY_SKU_CACHE_NAME"),
                "listBySkuId 必须通过 MultiLevelCacheClient 缓存分仓库存行");
        assertTrue(methodBody(source, "getAvailableStock").contains("WareHotCacheInvalidator.SKU_AVAILABLE_STOCK_CACHE_NAME"),
                "getAvailableStock 必须通过 MultiLevelCacheClient 缓存算出来的可售量");
        assertTrue(controller.contains("wareSkuService.getAvailableStock(skuId)"),
                "库存查询接口必须走带缓存的那个服务方法，不能自己拼");
    }

    /**
     * 五条写路径<b>逐个</b>断言。
     *
     * <p>方法名写死在这里是有意的：新增一条改库存的写路径时，
     * 这个清单不会自动跟上 —— 但那种情况本来就需要人来判断该不该失效，
     * 而不是让测试悄悄放行。清单式断言至少让「已知的五条」不会退化。
     */
    @Test
    @DisplayName("五条库存写路径都要提交后失效，逐个查而不是「至少有一处」")
    void everyStockWritePathEvictsAfterCommit() throws IOException {
        String source = Files.readString(WARE_SKU_SOURCE, StandardCharsets.UTF_8);

        for (String method : new String[] { "addStock", "orderLockStock", "save", "updateById", "removeByIds" }) {
            assertTrue(methodBody(source, method).contains("wareHotCacheInvalidator.evictSk"),
                    "写路径 " + method + " 没有在提交后失效 SKU 库存缓存 —— "
                            + "库存变了但可售量还显示旧值，而且不会有任何报错");
        }
    }

    /**
     * 释放和扣减这两个原子操作也要失效。
     *
     * <p>它们改的是 {@code stock_locked} 和 {@code stock}，
     * 正是可售量缓存算出来的那两个值。这两条路径由 MQ 消费驱动
     * （订单超时释放、支付成功扣减），<b>没有人在界面上盯着</b> ——
     * 少了失效的表现是"库存数字过一阵才对得上"，很容易被当成正常延迟。
     */
    @Test
    @DisplayName("释放/扣减这两个原子操作也要失效")
    void atomicStockOpsEvictAfterCommit() throws IOException {
        String atomicOps = Files.readString(STOCK_ATOMIC_OPS_SOURCE, StandardCharsets.UTF_8);

        assertTrue(methodBody(atomicOps, "unlock").contains("evictSkuAfterCommit"),
                "释放锁定库存（unlock）后没有失效缓存");
        assertTrue(methodBody(atomicOps, "deduct").contains("evictSkuAfterCommit"),
                "扣减锁定库存（deduct）后没有失效缓存");
    }

    /**
     * 取一个方法从签名到下一个同级方法之间的文本。
     *
     * <p>和 mall-product 的 SkuHotReadCacheTest 用的是同一套办法，
     * 那边已经用正向对照验证过它不会越界抓到下一个方法
     * （把常量从 item() 移到后面的 getBySkuId() 里，断言确实失败了）。
     */
    private static String methodBody(String source, String methodName) {
        int nameStart = -1;
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

        int next = nextDeclaration(source, start);
        return source.substring(start, next);
    }

    /** 往前找最近的方法声明起点。public / private / protected 都要认。 */
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
