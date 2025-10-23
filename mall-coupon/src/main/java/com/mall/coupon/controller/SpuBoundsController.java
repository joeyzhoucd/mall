package com.mall.coupon.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.coupon.entity.SpuBoundsEntity;
import com.mall.coupon.service.SpuBoundsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;




@RestController
@RequestMapping("coupon/spubounds")
public class SpuBoundsController {
    @Autowired
    private SpuBoundsService spuBoundsService;

    
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = spuBoundsService.queryPage(params);

        return R.ok().put("page", page);
    }


    
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		SpuBoundsEntity spuBounds = spuBoundsService.getById(id);

        return R.ok().put("spuBounds", spuBounds);
    }

    
    @RequestMapping("/save")
    public R save(@RequestBody SpuBoundsEntity spuBounds){
		spuBoundsService.save(spuBounds);

        return R.ok();
    }

    
    @PostMapping("/saveFromMap")
    public R saveFromMap(@RequestBody Map<String, String> params){
        SpuBoundsEntity spuBounds = new SpuBoundsEntity();
        spuBounds.setSpuId(Long.valueOf(params.get("spuId")));
        spuBounds.setBuyBounds(new java.math.BigDecimal(params.get("buy_bounds")));
        spuBounds.setGrowBounds(new java.math.BigDecimal(params.get("grow_bounds")));
        spuBounds.setWork(1); // Â»ËœÂ®Â¤â‚¬Â¼
        spuBoundsService.save(spuBounds);

        return R.ok();
    }

    
    @RequestMapping("/update")
    public R update(@RequestBody SpuBoundsEntity spuBounds){
		spuBoundsService.updateById(spuBounds);

        return R.ok();
    }

    
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		spuBoundsService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

}
