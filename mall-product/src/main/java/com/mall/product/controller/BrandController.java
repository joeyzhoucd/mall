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


    /**
     * 按 id 删除单个品牌。
     *
     * <p>已经有一个 {@code POST /delete}（请求体是 id 数组）了，为什么还要这个：
     * 前端的单条删除按钮调的是 {@code POST /delete/{brandId}}，路径不一样，
     * 走不到上面那个。这类「路径对不上」是接口审计脚本
     * （mall-deploy/tools/audit-endpoints.js）的典型产出。
     *
     * <p>保留两个而不是改前端统一到一个，是因为它们语义确实不同：
     * 批量删除失败时希望整体回滚，单条删除希望直接告诉调用方这一条的结果。
     */
    @PostMapping("/delete/{brandId}")
    public R deleteOne(@PathVariable("brandId") Long brandId) {
        try {
            brandService.removeById(brandId);
            return R.ok();
        } catch (Exception e) {
            return R.error("删除失败: " + e.getMessage());
        }
    }

}
