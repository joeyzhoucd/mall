package com.mall.ware.dao;

import com.mall.ware.entity.WareSkuEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


/**
 * 库存表的 DAO。
 * <p>
 * 下面这几个方法都是【原子条件更新】，用来替换原先"查出实体 → 在内存里加减 →
 * updateById 整行覆盖"的写法。那种写法有两个并发问题，而且不需要多副本就会出现
 * （RabbitMQ 监听线程本身是并发的）：
 * <ul>
 *   <li>丢更新：两个不同订单同时释放库存，各自读到 stock_locked=10，
 *       一个写 8、一个写 7，最终值取决于写入顺序，其中一次释放直接丢失。</li>
 *   <li>超卖：锁库存时先 {@code if (stock - locked >= count)} 判断、再写回，
 *       判断和写入之间没有任何保护，两个并发请求可以同时通过同一份可用库存的检查。</li>
 * </ul>
 * 换成"把判断写进 WHERE 条件、把加减写成相对表达式"之后，判断和修改在数据库里
 * 是一条语句、一把行锁，天然原子。调用方靠【影响行数】判断成功与否：
 * 返回 0 就是条件不满足（库存不够 / 已经被别人处理过），不需要也不应该再补一次读。
 */
@Mapper
public interface WareSkuDao extends BaseMapper<WareSkuEntity> {

    /**
     * 锁定库存。条件写在 WHERE 里，可用库存不足时影响 0 行，不会锁成负数。
     *
     * @return 1 = 锁定成功，0 = 该仓库可用库存不足
     */
    int lockStock(@Param("id") Long wareSkuId, @Param("count") Integer count);

    /**
     * 释放锁定（订单取消）。按 ware_sku 主键，用于锁定失败时的回滚——那时候
     * 我们确切知道刚才锁的是哪一行。
     */
    int releaseLockedById(@Param("id") Long wareSkuId, @Param("count") Integer count);

    /**
     * 释放锁定（按 sku）。
     * <p>
     * 已知局限：库存工作单明细表（wms_ware_order_task_detail）没有记录当初锁的是
     * 哪个仓库，所以这里只能按 sku_id 找一行 stock_locked 够扣的来释放。
     * 当前环境只有 1 个仓库、每个 sku 只有 1 行库存记录（已查库确认），所以结果是
     * 精确的；但如果将来一个 sku 分布在多个仓库，sku 的总量仍然正确、
     * 各仓库之间的 stock_locked 可能对不上。
     * 彻底修法是给明细表加 ware_id 字段（锁的时候记下来，释放时按它定位），
     * 那是一次 schema 变更，等真的引入多仓库时一起做。
     */
    int releaseLockedBySku(@Param("skuId") Long skuId, @Param("count") Integer count);

    /**
     * 扣减库存（订单已支付）：真实库存和锁定量同时减。两个条件都写进 WHERE，
     * 任一不满足就影响 0 行，不会把库存扣成负数。
     */
    int deductStockBySku(@Param("skuId") Long skuId, @Param("count") Integer count);

    /**
     * 采购入库：给已存在的库存行加数量。并发压力远低于下单链路（是后台收货动作），
     * 但同样不该用「读出来 + 在内存里加 + 整行覆盖」——两个收货单同时入库同一个 sku
     * 会丢掉一次。写成相对表达式即可，没有任何额外成本。
     */
    int addStockById(@Param("id") Long wareSkuId, @Param("count") Integer count);
}
