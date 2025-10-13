package com.joeyzhoucd.coupon.controller;

import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.R;
import com.joeyzhoucd.coupon.entity.SkuLadderEntity;
import com.joeyzhoucd.coupon.service.SkuLadderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;



/**
 * 商品阶梯价格
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:08:26
 */
@RestController
@RequestMapping("coupon/skuladder")
public class SkuLadderController {
    @Autowired
    private SkuLadderService skuLadderService;

    /**
     * 列表
     */
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = skuLadderService.queryPage(params);

        return R.ok().put("page", page);
    }


    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		SkuLadderEntity skuLadder = skuLadderService.getById(id);

        return R.ok().put("skuLadder", skuLadder);
    }

    /**
     * 保存
     */
    @RequestMapping("/save")
    public R save(@RequestBody SkuLadderEntity skuLadder){
		skuLadderService.save(skuLadder);

        return R.ok();
    }

    /**
     * 保存（通过Map参数）
     */
    @PostMapping("/saveFromMap")
    public R saveFromMap(@RequestBody Map<String, String> params){
        SkuLadderEntity skuLadder = new SkuLadderEntity();
        skuLadder.setSkuId(Long.valueOf(params.get("skuId")));
        skuLadder.setFullCount(Integer.valueOf(params.get("full_count")));
        skuLadder.setDiscount(new java.math.BigDecimal(params.get("discount")));
        skuLadder.setPrice(new java.math.BigDecimal(params.get("price")));
        skuLadder.setAddOther(Integer.valueOf(params.get("add_other")));
        skuLadderService.save(skuLadder);

        return R.ok();
    }

    /**
     * 修改
     */
    @RequestMapping("/update")
    public R update(@RequestBody SkuLadderEntity skuLadder){
		skuLadderService.updateById(skuLadder);

        return R.ok();
    }

    /**
     * 删除
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		skuLadderService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

}
