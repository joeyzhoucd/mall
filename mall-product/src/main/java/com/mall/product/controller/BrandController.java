package com.mall.product.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.common.validator.groupsequence.DAddGroup;
import com.mall.common.validator.groupsequence.DUpdateGroup;
import com.mall.product.entity.BrandEntity;
import com.mall.product.service.BrandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;



@RestController
@RequestMapping("product/brand")
public class BrandController {
    @Autowired
    private BrandService brandService;

    
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params) {
        PageUtils page = brandService.queryPage(params);

        return R.ok().put("data", page);
    }


    
    @RequestMapping("/info/{brandId}")
    public R info(@PathVariable("brandId") Long brandId) {
        BrandEntity brand = brandService.getById(brandId);

        return R.ok().put("data", brand);
    }


    
    @RequestMapping("/save")
    public R save(@Validated(DAddGroup.class) @RequestBody BrandEntity brand) {
        brandService.save(brand);

        return R.ok();
    }

    
    @RequestMapping("/update")
    public R update(@Validated(DUpdateGroup.class) @RequestBody BrandEntity brand) {
        brandService.updateById(brand);

        return R.ok();
    }

    @RequestMapping("/updateStatus")
    public R updateStatus(@RequestBody BrandEntity brand) {
        brandService.updateById(brand);

        return R.ok();
    }

    
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] brandIds) {
        brandService.removeByIds(Arrays.asList(brandIds));

        return R.ok();
    }

}
