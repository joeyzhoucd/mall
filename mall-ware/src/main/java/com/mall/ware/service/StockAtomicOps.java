package com.mall.ware.service;

import com.mall.common.constant.StockLockStatus;
import com.mall.ware.dao.WareOrderTaskDetailDao;
import com.mall.ware.dao.WareSkuDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存释放/扣减的原子操作。
 * <p>
 * <b>为什么单独抽一个 bean，而不是在 WareSkuServiceImpl 里加 @Transactional：</b>
 * 这两个操作各自要做两件事——先 CAS 推进明细状态抢到处理权，再改库存——两件事必须
 * 一起成功或一起回滚，所以需要一个事务包住。但原来调用它们的
 * {@code unlockStock} / {@code deductStock} 方法里还夹着 Feign 远程调用
 * （给订单服务记操作日志），把事务放到那一层会让一个数据库事务跨越一次跨服务调用，
 * 连接被占住的时间取决于对方的响应速度，高并发下会把连接池拖垮。
 * <p>
 * 另外 Spring 的 @Transactional 是靠代理生效的，同一个类内部直接调用私有方法不会
 * 经过代理，注解等于没写——这也是必须换个 bean 的原因。
 * <p>
 * <b>为什么顺序是"先 CAS 明细，再改库存"：</b>
 * 两者在同一个事务里，正常情况下顺序无所谓。但顺序决定了"事务提交前进程被杀"时
 * 留下的痕迹方向。先 CAS 的话，万一事务机制本身失效，留下的是"明细已完成、库存没动"
 * ——表现为一部分库存被永久锁住（可以被对账发现的资源泄漏）；反过来则是
 * "库存已改、明细还是 LOCKED"，重试任务会再扣一次，直接变成超卖。
 * 两种都不该发生，但要选一个更安全的失败方向。
 */
@Component
public class StockAtomicOps {

    @Autowired
    private WareSkuDao wareSkuDao;

    @Autowired
    private WareOrderTaskDetailDao wareOrderTaskDetailDao;

    /**
     * 释放锁定的库存（订单取消/超时）。
     *
     * @return true = 本次调用真正执行了释放；false = 这条明细已经被别人处理过，
     *         调用方不要再做任何后续动作（比如别再发一次消息、别再记一次日志）
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean unlock(Long detailId, Long skuId, Integer count) {
        if (detailId == null || skuId == null || count == null || count <= 0) {
            return false;
        }
        if (wareOrderTaskDetailDao.casLockStatus(detailId, StockLockStatus.LOCKED, StockLockStatus.UNLOCKED) == 0) {
            // 没抢到处理权：状态已经不是 LOCKED，说明另一个执行流已经处理完了
            return false;
        }
        wareSkuDao.releaseLockedBySku(skuId, count);
        return true;
    }

    /**
     * 扣减库存（订单已支付）。
     *
     * @return true = 本次调用真正执行了扣减；false = 已被别人处理过
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deduct(Long detailId, Long skuId, Integer count) {
        if (detailId == null || skuId == null || count == null || count <= 0) {
            return false;
        }
        if (wareOrderTaskDetailDao.casLockStatus(detailId, StockLockStatus.LOCKED, StockLockStatus.DEDUCTED) == 0) {
            return false;
        }
        wareSkuDao.deductStockBySku(skuId, count);
        return true;
    }
}
