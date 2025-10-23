package com.mall.coupon.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.coupon.entity.SkuLadderEntity;
import com.mall.coupon.service.SkuLadderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;




@RestController
@RequestMapping("coupon/skuladder")
public class SkuLadderController {
    @Autowired
    private SkuLadderService skuLadderService;

    
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = skuLadderService.queryPage(params);

        return R.ok().put("page", page);
    }


    
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		SkuLadderEntity skuLadder = skuLadderService.getById(id);

        return R.ok().put("skuLadder", skuLadder);
    }

    
    @RequestMapping("/save")
    public R save(@RequestBody SkuLadderEntity skuLadder){
		skuLadderService.save(skuLadder);

        return R.ok();
    }

    
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

    
    @RequestMapping("/update")
    public R update(@RequestBody SkuLadderEntity skuLadder){
		skuLadderService.updateById(skuLadder);

        return R.ok();
    }

    
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		skuLadderService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

}
