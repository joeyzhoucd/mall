package com.mall.ware.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.common.constant.OrderStatus;
import com.mall.common.constant.ErrorCode;
import com.mall.common.constant.ResponseKeys;
import com.mall.common.constant.StockConstants;
import com.mall.common.constant.StockLockStatus;
import com.mall.common.to.StockDeductTo;
import com.mall.common.to.StockReleaseItemTo;
import com.mall.common.to.StockReleaseTo;
import com.mall.ware.dao.WareSkuDao;
import com.mall.ware.entity.WareSkuEntity;
import com.mall.ware.entity.WareOrderTaskDetailEntity;
import com.mall.ware.entity.WareOrderTaskEntity;
import com.mall.ware.feign.OrderFeignService;
import com.mall.ware.service.WareSkuService;
import com.mall.ware.service.WareOrderTaskDetailService;
import com.mall.ware.service.WareOrderTaskService;
import com.mall.ware.vo.OrderItemLockVo;
import com.mall.ware.vo.StockFailVo;
import com.mall.ware.vo.WareSkuLockVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.mall.common.utils.RUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.mall.common.constant.MqConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.mall.ware.entity.WareInfoEntity;
import com.mall.ware.service.WareInfoService;
import java.util.stream.Collectors;

@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuDao, WareSkuEntity> implements WareSkuService {

    /**
     * 只在 setStock 里用：SKU 还没有任何库存记录时，判断系统里是不是只有一个仓库。
     * 只有一个才敢替调用方决定建在哪；多个仓时必须由调用方指定。
     */
    @Autowired
    private WareInfoService wareInfoService;

    @Autowired
    private com.mall.ware.dao.WareSkuDao wareSkuDao;

    @Autowired
    private com.mall.ware.dao.WareOrderTaskDetailDao wareOrderTaskDetailDao;

    @Autowired
    private com.mall.ware.service.StockAtomicOps stockAtomicOps;

    @Autowired
    private WareOrderTaskService wareOrderTaskService;

    @Autowired
    private WareOrderTaskDetailService wareOrderTaskDetailService;

    @Autowired
    private OrderFeignService orderFeignService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<WareSkuEntity> wrapper = new QueryWrapper<>();

        // Search by warehouse ID, SKU ID, or SKU name
        String key = (String) params.get("key");
        if (StringUtils.isNotBlank(key)) {
            wrapper.and(w -> {
                try {
                    Long id = Long.valueOf(key);
                    w.eq("id", id).or().eq("sku_id", id);
                } catch (NumberFormatException e) {
                    // If not a number, search by SKU name
                    w.like("sku_name", key);
                }
            });
        }

        // Filter by warehouse ID
        Object wareId = params.get("wareId");
        if (wareId != null) {
            wrapper.eq("ware_id", wareId);
        }

        // Filter by stock status
        Object stockStatus = params.get("stockStatus");
        if (stockStatus != null) {
            if ("inStock".equals(stockStatus)) {
                wrapper.gt("stock", 0);
            } else if ("outOfStock".equals(stockStatus)) {
                wrapper.eq("stock", 0);
            } else if ("lowStock".equals(stockStatus)) {
                wrapper.lt("stock", 10); // Low stock threshold 10
            }
        }

        wrapper.orderByDesc("id"); // Sort by ID descending

        IPage<WareSkuEntity> page = this.page(
                new Query<WareSkuEntity>().getPage(params),
                wrapper
        );

        return new PageUtils(page);
    }

    @Override
    public void addStock(Long skuId, Long wareId, Integer skuNum, String skuName) {
        QueryWrapper<WareSkuEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("sku_id", skuId).eq("ware_id", wareId);
        WareSkuEntity exist = this.getOne(wrapper);
        if (exist == null) {
            WareSkuEntity entity = new WareSkuEntity();
            entity.setSkuId(skuId);
            entity.setWareId(wareId);
            entity.setStock(skuNum == null ? 0 : skuNum);
            entity.setSkuName(skuName);
            entity.setStockLocked(0);
            this.save(entity);
        } else {
            // 相对增量，避免两个收货单同时入库同一个 sku 时丢更新。
            wareSkuDao.addStockById(exist.getId(), skuNum == null ? 0 : skuNum);
        }
    }

    @Override
    @Transactional
    public boolean orderLockStock(WareSkuLockVo lockVo) {
        if (lockVo == null || lockVo.getLocks() == null || lockVo.getLocks().isEmpty()) {
            return true;
        }
        WareOrderTaskEntity taskEntity = new WareOrderTaskEntity();
        taskEntity.setOrderSn(lockVo.getOrderSn());
        taskEntity.setCreateTime(new java.util.Date());
        wareOrderTaskService.save(taskEntity);
        List<WareOrderTaskDetailEntity> lockedDetails = new ArrayList<>();
        List<LockedSku> lockedWares = new ArrayList<>();
        for (OrderItemLockVo item : lockVo.getLocks()) {
            if (item == null || item.getSkuId() == null || item.getCount() == null) {
                continue;
            }
            // 原来这里是「查出所有仓库 → 在内存里判断 stock - locked >= count → 选中后写回」。
            // 那是典型的 check-then-act 超卖：判断和写入之间没有任何保护，两个并发下单
            // 可以同时通过同一份可用库存的检查，各自把 stock_locked 写成自己算出来的值，
            // 结果锁定量小于实际卖出量，也就是超卖。而且这个问题不需要多副本就会出现。
            //
            // 现在改成：候选仓库仍然要查（需要知道有哪些行、按什么顺序尝试），但
            // 【判断交给数据库】—— lockStock 把 stock - stock_locked >= count 写进 WHERE，
            // 加法写成相对表达式，一条语句一把行锁。影响 0 行就说明这个仓库不够，
            // 换下一个继续试；全部试完都不够才算失败。
            // 注意查询只用来枚举候选，真正的裁决权在 UPDATE 的影响行数上——
            // 如果拿查询结果去决定「哪些仓库值得试」，就又退回 check-then-act 了。
            List<WareSkuEntity> wareSkus = this.list(new QueryWrapper<WareSkuEntity>().eq("sku_id", item.getSkuId()));
            WareSkuEntity matched = null;
            if (wareSkus != null) {
                for (WareSkuEntity wareSku : wareSkus) {
                    if (wareSku == null || wareSku.getId() == null) {
                        continue;
                    }
                    if (wareSkuDao.lockStock(wareSku.getId(), item.getCount()) == 1) {
                        matched = wareSku;
                        break;
                    }
                }
            }
            if (matched == null) {
                rollbackLockedItems(lockedDetails, lockedWares);
                return false;
            }

            WareOrderTaskDetailEntity detailEntity = new WareOrderTaskDetailEntity();
            detailEntity.setSkuId(item.getSkuId());
            detailEntity.setSkuName(item.getTitle());
            detailEntity.setSkuNum(item.getCount());
            detailEntity.setTaskId(taskEntity.getId());
            detailEntity.setLockStatus(StockLockStatus.LOCKED);
            // 记下真正锁成功的那个仓库。这个信息在这里本来就有（matched 就是抢锁
            // 成功的那一行），此前只是没有存下来，导致释放/扣减时只能靠猜。
            detailEntity.setWareId(matched.getWareId());
            wareOrderTaskDetailService.save(detailEntity);
            lockedDetails.add(detailEntity);
            lockedWares.add(new LockedSku(matched.getId(), item.getCount()));
        }
        return true;
    }

    private void rollbackLockedItems(List<WareOrderTaskDetailEntity> lockedDetails, List<LockedSku> lockedWares) {
        for (LockedSku lockedSku : lockedWares) {
            if (lockedSku == null || lockedSku.getWareSkuId() == null || lockedSku.getCount() == null) {
                continue;
            }
            // 回滚时我们确切知道刚才锁的是哪一行（lockedWares 里记了 ware_sku 主键），
            // 所以按主键做原子释放。条件 stock_locked >= count 保证不会减成负数。
            wareSkuDao.releaseLockedById(lockedSku.getWareSkuId(), lockedSku.getCount());
        }
        for (WareOrderTaskDetailEntity detail : lockedDetails) {
            if (detail == null || detail.getId() == null) {
                continue;
            }
            // 这里其实可以证明是单一所有者（明细刚由本线程在同一个事务里创建，
            // 外界还看不到这笔订单），但仍然用 CAS：让「推进明细状态」在整个模块里
            // 只有一种写法，避免以后有人照着这行复制出一个真的有并发的整行写回。
            wareOrderTaskDetailDao.casLockStatus(detail.getId(), StockLockStatus.LOCKED, StockLockStatus.UNLOCKED);
        }
    }

    private static class LockedSku {
        private final Long wareSkuId;
        private final Integer count;

        private LockedSku(Long wareSkuId, Integer count) {
            this.wareSkuId = wareSkuId;
            this.count = count;
        }

        public Long getWareSkuId() {
            return wareSkuId;
        }

        public Integer getCount() {
            return count;
        }
    }

    @Override
    public void unlockStock(StockReleaseTo releaseTo) {
        if (releaseTo == null || releaseTo.getItems() == null || releaseTo.getItems().isEmpty()) {
            return;
        }
        for (StockReleaseItemTo item : releaseTo.getItems()) {
            if (item != null && item.getOrderSn() == null) {
                item.setOrderSn(releaseTo.getOrderSn());
            }
            unlockStock(item);
        }
    }

    @Override
    public void unlockStock(StockReleaseItemTo itemTo) {
        if (itemTo == null || itemTo.getSkuId() == null || itemTo.getCount() == null) {
            return;
        }
        if (StringUtils.isBlank(itemTo.getOrderSn())) {
            return;
        }
        WareOrderTaskEntity taskEntity = wareOrderTaskService.getByOrderSn(itemTo.getOrderSn());
        if (taskEntity == null) {
            return;
        }
        WareOrderTaskDetailEntity detailEntity = wareOrderTaskDetailService.getByTaskIdAndSkuId(taskEntity.getId(), itemTo.getSkuId());
        if (detailEntity == null || detailEntity.getLockStatus() == null || detailEntity.getLockStatus() != StockLockStatus.LOCKED) {
            return;
        }
        if (detailEntity.getRetryCount() != null && detailEntity.getRetryCount() >= StockConstants.RETRY_LIMIT) {
            return;
        }
        Integer status = fetchOrderStatusWithRetry(detailEntity, itemTo.getOrderSn());
        if (status == null) {
            return;
        }
        // 0:新建,1:已支付,4:已关闭
        if (status != OrderStatus.CLOSED) {
            return;
        }
        unlockStockByDetail(itemTo, detailEntity);
        }

    @Override
    public void deductStock(StockDeductTo deductTo) {
        if (deductTo == null || deductTo.getItems() == null || deductTo.getItems().isEmpty()) {
            return;
        }
        for (StockReleaseItemTo item : deductTo.getItems()) {
            if (item != null && item.getOrderSn() == null) {
                item.setOrderSn(deductTo.getOrderSn());
            }
            deductStockItem(item);
        }
    }

    private void deductStockItem(StockReleaseItemTo itemTo) {
        if (itemTo == null || itemTo.getSkuId() == null || itemTo.getCount() == null) {
            return;
        }
        if (StringUtils.isBlank(itemTo.getOrderSn())) {
            return;
        }
        WareOrderTaskEntity taskEntity = wareOrderTaskService.getByOrderSn(itemTo.getOrderSn());
        if (taskEntity == null) {
            return;
        }
        WareOrderTaskDetailEntity detailEntity = wareOrderTaskDetailService.getByTaskIdAndSkuId(taskEntity.getId(), itemTo.getSkuId());
        if (detailEntity == null || detailEntity.getLockStatus() == null || detailEntity.getLockStatus() != StockLockStatus.LOCKED) {
            return;
        }
        if (detailEntity.getRetryCount() != null && detailEntity.getRetryCount() >= StockConstants.RETRY_LIMIT) {
            return;
        }
        Integer status = fetchOrderStatusWithRetry(detailEntity, itemTo.getOrderSn());
        if (status == null) {
            return;
        }
        // 0:新建,1:已支付,4:已关闭
        if (status != OrderStatus.PAYED) {
            return;
        }
        deductStockByDetail(itemTo, detailEntity);

        com.mall.common.to.OrderOperateTo operateTo = new com.mall.common.to.OrderOperateTo();
        operateTo.setOrderSn(itemTo.getOrderSn());
        operateTo.setStatus(OrderStatus.PAYED);
        operateTo.setNote("库存扣减成功");
        operateTo.setOperateMan("system");
        orderFeignService.recordOperate(operateTo);
    }

    @Override
    public void retryStockOps(StockReleaseItemTo itemTo) {
        if (itemTo == null || itemTo.getSkuId() == null || itemTo.getCount() == null) {
            return;
        }
        if (StringUtils.isBlank(itemTo.getOrderSn())) {
            return;
        }
        WareOrderTaskEntity taskEntity = wareOrderTaskService.getByOrderSn(itemTo.getOrderSn());
        if (taskEntity == null) {
            return;
        }
        WareOrderTaskDetailEntity detailEntity = wareOrderTaskDetailService.getByTaskIdAndSkuId(taskEntity.getId(), itemTo.getSkuId());
        if (detailEntity == null || detailEntity.getLockStatus() == null || detailEntity.getLockStatus() != StockLockStatus.LOCKED) {
            return;
        }
        if (detailEntity.getRetryCount() != null && detailEntity.getRetryCount() >= StockConstants.RETRY_LIMIT) {
            return;
        }
        Integer status = fetchOrderStatusWithRetry(detailEntity, itemTo.getOrderSn());
        if (status == null) {
            return;
        }
        if (status == OrderStatus.CLOSED) {
            unlockStockByDetail(itemTo, detailEntity);
            return;
        }
        if (status == OrderStatus.PAYED) {
            deductStockByDetail(itemTo, detailEntity);
        }
    }

    @Override
    public boolean manualRetryFailed(Long taskDetailId) {
        if (taskDetailId == null) {
            return false;
        }
        WareOrderTaskDetailEntity detailEntity = wareOrderTaskDetailService.getById(taskDetailId);
        if (detailEntity == null || detailEntity.getLockStatus() == null) {
            return false;
        }
        if (StockLockStatus.FAILED != detailEntity.getLockStatus()) {
            return false;
        }
        // 后台手动重试：把 FAILED 改回 LOCKED 也用 CAS，避免两个运维同时点重试、
        // 或者点重试的同时重试任务正在处理这条明细。抢不到就直接返回 false。
        if (wareOrderTaskDetailDao.casLockStatus(detailEntity.getId(), StockLockStatus.FAILED, StockLockStatus.LOCKED) == 0) {
            return false;
        }
        wareOrderTaskDetailDao.resetRetryCount(detailEntity.getId());

        WareOrderTaskEntity taskEntity = wareOrderTaskService.getById(detailEntity.getTaskId());
        if (taskEntity == null || taskEntity.getOrderSn() == null) {
            return false;
        }
        StockReleaseItemTo itemTo = new StockReleaseItemTo();
        itemTo.setOrderSn(taskEntity.getOrderSn());
        itemTo.setSkuId(detailEntity.getSkuId());
        itemTo.setCount(detailEntity.getSkuNum());
        retryStockOps(itemTo);
        return true;
    }

    @Override
    public List<StockFailVo> listFailedDetails() {
        List<WareOrderTaskDetailEntity> details = wareOrderTaskDetailService.listByLockStatus(StockLockStatus.FAILED);
        if (details == null || details.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<StockFailVo> result = new ArrayList<>();
        for (WareOrderTaskDetailEntity detail : details) {
            StockFailVo vo = new StockFailVo();
            vo.setTaskDetailId(detail.getId());
            vo.setSkuId(detail.getSkuId());
            vo.setSkuNum(detail.getSkuNum());
            vo.setLockStatus(detail.getLockStatus());
            vo.setRetryCount(detail.getRetryCount());
            WareOrderTaskEntity task = wareOrderTaskService.getById(detail.getTaskId());
            if (task != null) {
                vo.setOrderSn(task.getOrderSn());
            }
            result.add(vo);
        }
        return result;
    }

    private Integer fetchOrderStatusWithRetry(WareOrderTaskDetailEntity detailEntity, String orderSn) {
        try {
            com.mall.common.utils.R orderResp = orderFeignService.getOrderStatus(orderSn);
            Integer code = RUtils.getCode(orderResp);
            if (code != null && code.equals(ErrorCode.ORDER_NOT_FOUND.getCode())) {
                return OrderStatus.CLOSED;
            }
            if (!RUtils.isOk(orderResp)) {
                throw new RuntimeException("order status query failed");
            }
            Integer status = RUtils.getInteger(orderResp, ResponseKeys.STATUS);
            if (status == null) {
                throw new RuntimeException("order status invalid");
            }
            return status;
        } catch (RuntimeException ex) {
            int retry = increaseRetry(detailEntity);
            if (retry < StockConstants.RETRY_LIMIT) {
                throw ex;
            }
            return null;
        }
    }

    /**
     * 累加重试次数，达到上限则把明细标为 FAILED 并发一条失败消息。
     * <p>
     * 三处都改成了原子操作，原因见 WareOrderTaskDetailDao 上的注释。最关键的是
     * 不能再用 updateById 整行写回 —— 那会把内存里过期的 lock_status 一起写回去，
     * 把别的执行流已经推进到 UNLOCKED/DEDUCTED 的明细复活成 LOCKED，
     * 于是重试任务再处理一遍，库存被重复释放/扣减。
     * <p>
     * 失败消息由「赢下 LOCKED→FAILED 这次 CAS」的执行流发送，所以恰好发一次。
     */
    private int increaseRetry(WareOrderTaskDetailEntity detailEntity) {
        Long id = detailEntity.getId();
        if (id == null) {
            return Integer.MAX_VALUE;
        }
        if (wareOrderTaskDetailDao.incrementRetryIfLocked(id) == 0) {
            // 明细已经不是 LOCKED，说明别人处理完了，本次不该再累加也不该再往下走
            return Integer.MAX_VALUE;
        }
        WareOrderTaskDetailEntity fresh = wareOrderTaskDetailDao.selectById(id);
        int next = (fresh == null || fresh.getRetryCount() == null) ? 0 : fresh.getRetryCount();
        if (next >= StockConstants.RETRY_LIMIT) {
            if (wareOrderTaskDetailDao.casLockStatus(id, StockLockStatus.LOCKED, StockLockStatus.FAILED) == 1) {
                sendStockFailMessage(fresh);
            }
        }
        return next;
    }

    private void sendStockFailMessage(WareOrderTaskDetailEntity detailEntity) {
        if (detailEntity == null) {
            return;
        }
        rabbitTemplate.convertAndSend(
                MqConstants.STOCK_RELEASE_EXCHANGE,
                MqConstants.STOCK_FAIL_ROUTING_KEY,
                detailEntity
        );
    }

    /**
     * 释放锁定的库存。
     * <p>
     * 原来的写法有两个并发问题，都不需要多副本就会出现（RabbitMQ 监听线程本身并发）：
     * 一是取 wareSkus.get(0) 之后在内存里读-改-写再整行覆盖，两个不同订单同时释放会丢更新；
     * 二是调用方那句 lockStatus != LOCKED 就返回的判断和这里的写入之间没有保护，
     * 两个执行流可以同时通过，同一笔明细被处理两次。
     * <p>
     * 现在整个操作交给 StockAtomicOps：先 CAS 推进明细状态抢处理权，再做原子条件更新，
     * 两步在一个事务里。返回 false 表示这条明细已经被别人处理过，本次调用什么都不该做。
     */
    private void unlockStockByDetail(StockReleaseItemTo itemTo, WareOrderTaskDetailEntity detailEntity) {
        stockAtomicOps.unlock(detailEntity.getId(), itemTo.getSkuId(), detailEntity.getWareId(), itemTo.getCount());
    }

    /**
     * 扣减库存（订单已支付）。问题和修法同 {@link #unlockStockByDetail}，
     * 但这里的后果更严重：原写法在两个执行流的读写交错时（A 读 100 写 98，
     * B 读 98 写 96）会把真实库存扣两次，也就是凭空少掉一份货。
     */
    private void deductStockByDetail(StockReleaseItemTo itemTo, WareOrderTaskDetailEntity detailEntity) {
        stockAtomicOps.deduct(detailEntity.getId(), itemTo.getSkuId(), detailEntity.getWareId(), itemTo.getCount());
    }

    /**
     * 后台设置库存。
     *
     * <h3>「设置某个 SKU 的库存」这句话本身是有歧义的</h3>
     * 一个 SKU 可以存在于多个仓库，库存是分仓记的。前端只传了 skuId 和 stock，
     * 没说是哪个仓。这里<b>不猜</b>：
     * <ul>
     *   <li>显式传了 wareId  -> 就用它；</li>
     *   <li>该 SKU 只有一条仓库记录 -> 用那一条，没有歧义；</li>
     *   <li>该 SKU 有多条记录 -> <b>报错并列出候选仓</b>。随便挑一个（比如 id 最小的）
     *       会让管理员以为改的是总库存，实际只改了其中一个仓，
     *       而其余仓的数字纹丝不动 —— 页面上看不出来，只有发货时才发现对不上；</li>
     *   <li>该 SKU 没有任何记录 -> 全系统只有一个仓时在那里建，
     *       多个仓时同样报错要求指定。</li>
     * </ul>
     * 这条纪律和 {@code StockAtomicOps} 里坚持带 ware_id 的理由是同一个：
     * 分仓库存一旦「总数对、分布错」，在单仓环境里永远暴露不出来。
     *
     * <h3>不能设成低于已锁定的数量</h3>
     * stock_locked 是已经被订单占住、还没扣减的量。把 stock 设到它以下，
     * 可售量（stock - stock_locked）就变成负数，后续下单的库存判断会全部失真。
     * 这里直接拒绝，而不是悄悄截断到 stock_locked —— 截断的话管理员填的数字
     * 和实际写入的不一样，且没有任何提示。
     */
    @Override
    @Transactional
    public Long setStock(Long skuId, Long wareId, Integer stock) {
        if (skuId == null || stock == null || stock < 0) {
            throw new IllegalArgumentException("skuId 不能为空，stock 不能为空或负数");
        }

        List<WareSkuEntity> rows = this.list(new QueryWrapper<WareSkuEntity>().eq("sku_id", skuId));

        Long targetWareId = wareId;
        if (targetWareId == null) {
            if (rows.size() == 1) {
                targetWareId = rows.get(0).getWareId();
            } else if (rows.size() > 1) {
                String candidates = rows.stream()
                        .map(r -> r.getWareId() + "(当前 " + r.getStock() + ")")
                        .collect(Collectors.joining(", "));
                throw new IllegalArgumentException(
                        "SKU " + skuId + " 在多个仓库有库存，必须指定 wareId。候选：" + candidates);
            } else {
                List<WareInfoEntity> wares = wareInfoService.list();
                if (wares.size() == 1) {
                    targetWareId = wares.get(0).getId();
                } else {
                    throw new IllegalArgumentException(
                            "SKU " + skuId + " 还没有库存记录，且系统里有 " + wares.size()
                                    + " 个仓库，必须指定 wareId");
                }
            }
        }

        final Long finalWareId = targetWareId;
        WareSkuEntity existing = rows.stream()
                .filter(r -> finalWareId.equals(r.getWareId()))
                .findFirst().orElse(null);

        if (existing == null) {
            WareSkuEntity created = new WareSkuEntity();
            created.setSkuId(skuId);
            created.setWareId(finalWareId);
            created.setStock(stock);
            created.setStockLocked(0);
            this.save(created);
            return finalWareId;
        }

        int locked = existing.getStockLocked() == null ? 0 : existing.getStockLocked();
        if (stock < locked) {
            throw new IllegalArgumentException(
                    "库存不能低于已锁定数量：仓库 " + finalWareId + " 当前已锁定 " + locked
                            + "，要设置的值是 " + stock + "。请先处理这些在途订单。");
        }

        WareSkuEntity patch = new WareSkuEntity();
        patch.setId(existing.getId());
        patch.setStock(stock);
        this.updateById(patch);
        return finalWareId;
    }

}