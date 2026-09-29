package com.mall.ware.schedule;

import com.mall.common.constant.StockConstants;
import com.mall.common.constant.StockLockStatus;
import com.mall.common.to.StockReleaseItemTo;
import com.mall.ware.entity.WareOrderTaskDetailEntity;
import com.mall.ware.entity.WareOrderTaskEntity;
import com.mall.ware.service.WareOrderTaskDetailService;
import com.mall.ware.service.WareOrderTaskService;
import com.mall.ware.service.WareSkuService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Date;
import java.util.List;

/**
 * 库存锁的兜底：MQ 那条正常路径（关单 → 释放、支付 → 扣减）漏掉的，由这里按订单状态补做。
 *
 * <h3>宽限期：只碰锁了 10 分钟以上的（2026-09-29 加）</h3>
 * 原来每轮扫<b>全部</b> LOCKED 明细。submitOrder 是「先调 mall-ware 锁库存（ware 侧已提交）→
 * 再保存订单 → 事务提交」，这中间订单对别的事务不可见；扫描正好落进这段窗口的话，
 * 查订单拿到 ORDER_NOT_FOUND、按已关闭释放 —— 随后订单照常建成，付款时扣减因为明细已不是
 * LOCKED 被跳过，同一份货可以再卖一次。十三万单历史里没撞上过：扫描按主键升序、新锁的排在最后，
 * 忙的时候前面几千条先查完，轮到它时订单早提交了；风险集中在<b>闲时</b>（约每单 0.5s / 300s）。
 * 10 分钟远大于一次提交的耗时（秒级），又小于超时关单的 30 分钟；顺带让每轮不再逐条去查
 * 刚下的待付款单（那些到 30 分钟由关单消息释放，本来就不需要这里）。
 *
 * <h3>FAILED 自动恢复（2026-09-29 加）</h3>
 * 重试 3 次（5 分钟一轮）失败就标 FAILED，原来之后再没有任何东西会碰它，只能人工点重试。
 * 实测：一次约 15 分钟的网络中断（mall-ware 查不到订单状态）让 574 单、213 个 SKU、2643 件库存
 * 永久锁住卖不出去。现在每 30 分钟把 FAILED 交给 {@link WareSkuService#manualRetryFailed}
 * （CAS 回 LOCKED、清零重试、按订单状态走正常流程）；网络还没好就再 FAILED、半小时后再来 ——
 * 不放弃，也不高频打扰下游。
 */
@Component
public class StockRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(StockRetryScheduler.class);

    /** 宽限期：锁了多久之后兜底才介入 */
    static final long MIN_LOCK_AGE_MS = 10 * 60 * 1000L;
    /** FAILED 自动恢复的间隔 */
    static final long FAILED_RECOVERY_INTERVAL_MS = 30 * 60 * 1000L;

    @Autowired
    private WareOrderTaskDetailService wareOrderTaskDetailService;

    @Autowired
    private WareOrderTaskService wareOrderTaskService;

    @Autowired
    private WareSkuService wareSkuService;

    private Clock clock = Clock.systemUTC();

    @Scheduled(fixedDelay = StockConstants.RETRY_INTERVAL_MS)
    public void retryStockOps() {
        Date lockedBefore = new Date(clock.millis() - MIN_LOCK_AGE_MS);
        List<WareOrderTaskDetailEntity> details = wareOrderTaskDetailService.listRetryingDetails(
                StockLockStatus.LOCKED,
                StockConstants.RETRY_LIMIT,
                lockedBefore
        );
        for (WareOrderTaskDetailEntity detail : details) {
            if (detail == null) {
                continue;
            }
            WareOrderTaskEntity task = wareOrderTaskService.getById(detail.getTaskId());
            if (task == null || task.getOrderSn() == null) {
                continue;
            }
            StockReleaseItemTo itemTo = new StockReleaseItemTo();
            itemTo.setOrderSn(task.getOrderSn());
            itemTo.setSkuId(detail.getSkuId());
            itemTo.setCount(detail.getSkuNum());
            try {
                wareSkuService.retryStockOps(itemTo);
            } catch (Exception ignored) {
                // retry handled in service
            }
        }
    }

    @Scheduled(initialDelay = 2 * 60 * 1000L, fixedDelay = FAILED_RECOVERY_INTERVAL_MS)
    public void recoverFailed() {
        List<WareOrderTaskDetailEntity> failed = wareOrderTaskDetailService.listByLockStatus(StockLockStatus.FAILED);
        if (failed == null || failed.isEmpty()) {
            return;
        }
        int resumed = 0, errors = 0;
        for (WareOrderTaskDetailEntity detail : failed) {
            if (detail == null || detail.getId() == null) {
                continue;
            }
            try {
                if (wareSkuService.manualRetryFailed(detail.getId())) {
                    resumed++;
                }
            } catch (Exception e) {
                errors++;
            }
        }
        log.warn("库存 FAILED 明细自动恢复：共 {} 条，重新交给正常流程 {} 条，出错 {} 条（下游仍不可用时会再次 FAILED，{} 分钟后再试）",
                failed.size(), resumed, errors, FAILED_RECOVERY_INTERVAL_MS / 60000);
    }
}
