package com.mall.product.controller;

import com.mall.common.utils.R;
import com.mall.product.entity.CategoryEntity;
import com.mall.product.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;



@RestController
@RequestMapping("product/category")
public class CategoryController {
    @Autowired
    private CategoryService categoryService;

    
    @RequestMapping("/list/tree")
    public R list(@RequestParam Map<String, Object> params) {
        List<CategoryEntity> entities = categoryService.listAsTree();

        return R.ok().put("data", entities);
    }


    
    @RequestMapping("/info/{catId}")
    public R info(@PathVariable("catId") Long catId) {
        CategoryEntity category = categoryService.getById(catId);

        return R.ok().put("data", category);
    }

    
    @RequestMapping("/save")
    public R save(@RequestBody CategoryEntity category) {
        categoryService.save(category);

        return R.ok();
    }

    @RequestMapping("/save/drag")
    public R save(@RequestBody List<CategoryEntity> categories) {
        categoryService.saveBatch(categories);

        return R.ok();
    }

    
    @RequestMapping("/update")
    public R update(@RequestBody CategoryEntity category) {
        categoryService.updateById(category);

        return R.ok();
    }

    
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] catIds) {
        categoryService.removeByIds(Arrays.asList(catIds));
        return R.ok();
    }

}
