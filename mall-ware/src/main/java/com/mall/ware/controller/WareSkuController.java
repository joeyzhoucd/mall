package com.mall.ware.controller;

import com.mall.common.constant.ErrorCode;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.common.to.StockReleaseTo;
import com.mall.ware.entity.WareSkuEntity;
import com.mall.ware.service.WareSkuService;
import com.mall.ware.vo.WareSkuLockVo;
import com.mall.ware.vo.StockFailVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;

/**
 * Warehouse SKU Controller
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@RestController
@RequestMapping("ware/waresku")
public class WareSkuController {
    @Autowired
    private WareSkuService wareSkuService;

    /**
     * Get warehouse SKU list
     */
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = wareSkuService.queryPage(params);
        return R.ok().put("page", page);
    }

    /**
     * Get warehouse SKU info by ID
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		WareSkuEntity wareSku = wareSkuService.getById(id);
        return R.ok().put("wareSku", wareSku);
    }

    /**
     * Save warehouse SKU
     */
    @RequestMapping("/save")
    public R save(@RequestBody WareSkuEntity wareSku){
		wareSkuService.save(wareSku);
        return R.ok();
    }

    /**
     * 修改库存记录的<b>非数量</b>字段。
     *
     * <h3>为什么不再是「整个实体丢给 updateById」</h3>
     * 这个方法是 renren 生成器生成的，原来直接 {@code updateById(wareSku)} ——
     * 也就是任何调用方都能把 {@code stock} 和 {@code stock_locked} 写成任意值。
     * <p>
     * {@code stock_locked} 是【被订单占住、还没扣减】的数量，由
     * {@link com.mall.ware.service.StockAtomicOps} 里那组带 CAS 的 SQL 维护，
     * 每一次增减都和一条订单明细的状态迁移严格对应。直接写它会把这本账毁掉：
     * 可售量（stock - stock_locked）立刻失真，而且<b>不会有任何报错</b> ——
     * 症状要等到某个订单发不出货、或者库存莫名其妙多出来的时候才出现，
     * 那时已经无从追溯是谁写的。
     * <p>
     * {@code stock} 也不在这里改：改库存有专门的 {@code POST /updateStock}，
     * 它会校验「不能低于已锁定数量」，并在一个 SKU 存在于多个仓时拒绝而不是随便挑一个。
     * <p>
     * 所以这里只留 {@code skuName} 这类描述性字段。传了数量字段直接报错，
     * 而不是静默忽略 —— 静默忽略会让调用方以为改成功了。
     */
    @PostMapping("/update")
    public R update(@RequestBody WareSkuEntity wareSku) {
        if (wareSku == null || wareSku.getId() == null) {
            return R.error("id 不能为空");
        }
        if (wareSku.getStock() != null || wareSku.getStockLocked() != null) {
            return R.error("这个接口不能改库存数量。改可售库存用 POST /ware/waresku/updateStock；"
                    + "stock_locked 由订单流程的 CAS 逻辑维护，任何情况下都不允许直接写。");
        }
        WareSkuEntity patch = new WareSkuEntity();
        patch.setId(wareSku.getId());
        patch.setSkuName(wareSku.getSkuName());
        wareSkuService.updateById(patch);
        return R.ok();
    }

    /**
     * Delete warehouse SKU
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		wareSkuService.removeByIds(Arrays.asList(ids));
        return R.ok();
    }

    /**
     * Lock stock for order
     */
    @PostMapping("/lock/order")
    public R orderLockStock(@RequestBody WareSkuLockVo lockVo) {
        boolean locked = wareSkuService.orderLockStock(lockVo);
        if (!locked) {
            return R.error(ErrorCode.STOCK_NOT_ENOUGH);
        }
        return R.ok();
    }

    /**
     * Unlock stock for order
     */
    @PostMapping("/unlock/order")
    public R orderUnlockStock(@RequestBody StockReleaseTo releaseTo) {
        wareSkuService.unlockStock(releaseTo);
        return R.ok();
    }

    /**
     * List failed stock lock tasks for manual compensation
     */
    @GetMapping("/fail/list")
    public R listFailedTasks() {
        java.util.List<StockFailVo> list = wareSkuService.listFailedDetails();
        return R.ok().put("list", list);
    }

    /**
     * Manual retry for failed task
     */
    @PostMapping("/fail/retry/{detailId}")
    public R retryFailed(@PathVariable("detailId") Long detailId) {
        boolean ok = wareSkuService.manualRetryFailed(detailId);
        return ok ? R.ok() : R.error(ErrorCode.REQUEST_FAILED);
    }

    /**
     * 后台设置某个 SKU 的库存。
     *
     * <p>请求体 {@code {"skuId":"1","stock":"100"}}，可选 {@code "wareId":"1"}。
     * 前端把数字都转成了字符串发过来，所以统一用 String 解析，
     * 不能直接声明成 Long/Integer 字段去接 —— Jackson 对 "100" -> Integer 是可以的，
     * 但一旦前端某处改成发数字或空串，两种写法的失败方式完全不同，
     * 这里显式解析并给出明确报错。
     *
     * <p>歧义（一个 SKU 多个仓）由 service 层拒绝而不是替调用方猜，
     * 报错信息里会列出候选仓库，所以这里把 IllegalArgumentException 的消息原样回传。
     */
    @PostMapping("/updateStock")
    public R updateStock(@RequestBody Map<String, Object> body) {
        try {
            Object skuIdObj = body.get("skuId");
            Object stockObj = body.get("stock");
            if (skuIdObj == null || stockObj == null) {
                return R.error("参数不完整：需要 skuId 和 stock");
            }
            Long skuId = Long.valueOf(String.valueOf(skuIdObj).trim());
            Integer stock = Integer.valueOf(String.valueOf(stockObj).trim());
            Object wareIdObj = body.get("wareId");
            Long wareId = (wareIdObj == null || String.valueOf(wareIdObj).isBlank())
                    ? null : Long.valueOf(String.valueOf(wareIdObj).trim());

            Long usedWareId = wareSkuService.setStock(skuId, wareId, stock);
            return R.ok().put("wareId", usedWareId);
        } catch (NumberFormatException e) {
            return R.error("skuId / stock / wareId 必须是数字");
        } catch (IllegalArgumentException e) {
            return R.error(e.getMessage());
        } catch (Exception e) {
            return R.error("库存更新失败: " + e.getMessage());
        }
    }

}