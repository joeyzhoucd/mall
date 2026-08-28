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
     * Update warehouse SKU
     */
    @RequestMapping("/update")
    public R update(@RequestBody WareSkuEntity wareSku){
		wareSkuService.updateById(wareSku);
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