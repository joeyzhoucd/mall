package com.mall.coupon.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.coupon.entity.SkuFullReductionEntity;
import com.mall.coupon.service.SkuFullReductionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;




@RestController
@RequestMapping("coupon/skufullreduction")
public class SkuFullReductionController {
    @Autowired
    private SkuFullReductionService skuFullReductionService;

    
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = skuFullReductionService.queryPage(params);

        return R.ok().put("page", page);
    }


    
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		SkuFullReductionEntity skuFullReduction = skuFullReductionService.getById(id);

        return R.ok().put("skuFullReduction", skuFullReduction);
    }

    
    @RequestMapping("/save")
    public R save(@RequestBody SkuFullReductionEntity skuFullReduction){
		skuFullReductionService.save(skuFullReduction);

        return R.ok();
    }

    
    @PostMapping("/saveFromMap")
    public R saveFromMap(@RequestBody Map<String, String> params){
        SkuFullReductionEntity skuFullReduction = new SkuFullReductionEntity();
        skuFullReduction.setSkuId(Long.valueOf(params.get("skuId")));
        skuFullReduction.setFullPrice(new java.math.BigDecimal(params.get("full_price")));
        skuFullReduction.setReducePrice(new java.math.BigDecimal(params.get("reduce_price")));
        skuFullReduction.setAddOther(Integer.valueOf(params.get("add_other")));
        skuFullReductionService.save(skuFullReduction);

        return R.ok();
    }

    
    @RequestMapping("/update")
    public R update(@RequestBody SkuFullReductionEntity skuFullReduction){
		skuFullReductionService.updateById(skuFullReduction);

        return R.ok();
    }

    
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		skuFullReductionService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

}
