package com.joeyzhoucd.product.controller;

import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.R;
import com.joeyzhoucd.product.entity.CategoryBrandRelationEntity;
import com.joeyzhoucd.product.service.CategoryBrandRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;


/**
 * 属性&属性分组关联
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@RestController
@RequestMapping("product/categorybrandrelation")
public class CategoryBrandRelationController {
    @Autowired
    private CategoryBrandRelationService categoryBrandRelationService;

    /**
     * 列表
     */
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params) {
        PageUtils page = categoryBrandRelationService.queryPage(params);

        return R.ok().put("data", page);
    }


    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id) {
        CategoryBrandRelationEntity categoryBrandRelation = categoryBrandRelationService.getById(id);

        return R.ok().put("data", categoryBrandRelation);
    }

    /**
     * 保存
     */
    @RequestMapping("/save")
    public R save(@RequestBody CategoryBrandRelationEntity categoryBrandRelation) {
        categoryBrandRelationService.save(categoryBrandRelation);

        return R.ok();
    }

    /**
     * 修改
     */
    @RequestMapping("/update")
    public R update(@RequestBody CategoryBrandRelationEntity categoryBrandRelation) {
        categoryBrandRelationService.updateById(categoryBrandRelation);

        return R.ok();
    }

    /**
     * 删除
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids) {
        categoryBrandRelationService.removeByIds(Arrays.asList(ids));
        return R.ok();
    }

    /**
     * 根据分类删除
     */
    @PostMapping("/deleteByBrandId/{brandId}")
    public R deleteByBrandId(@PathVariable Long brandId) {
        categoryBrandRelationService.deleteByBrandId(brandId);
        return R.ok();
    }

    @PostMapping("/saveBatch")
    public R save(@RequestBody List<CategoryBrandRelationEntity> categoryBrandRelations) {
        categoryBrandRelationService.saveBatch(categoryBrandRelations);
        return R.ok();
    }

    @PostMapping("/updateRelations/{brandId}")
    public R updateRelations(@PathVariable Long brandId, @RequestBody List<Long> categoryIds) {
        categoryBrandRelationService.updateBrandCategoryRelations(brandId, categoryIds);
        return R.ok();
    }


    @GetMapping("/getRelationsByBrandId/{brandId}")
    public R getRelationsByBrandId(@PathVariable Long brandId) {
        List<CategoryBrandRelationEntity> categoryBrandRelations = categoryBrandRelationService.getRelationsByBrandId(brandId);
        return R.ok().put("data", categoryBrandRelations);
    }

    @GetMapping("/getRelationsByCategoryId/{categoryId}")
    public R getRelationsByCategoryId(@PathVariable Long categoryId) {
        List<CategoryBrandRelationEntity> categoryBrandRelations = categoryBrandRelationService.getRelationsByCategoryId(categoryId);
        return R.ok().put("data", categoryBrandRelations);
    }

}
