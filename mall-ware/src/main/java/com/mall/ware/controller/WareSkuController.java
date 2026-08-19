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
}