package com.joeyzhoucd.product.controller;

import java.util.Arrays;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.joeyzhoucd.product.entity.SkuBaseAttrValueEntity;
import com.joeyzhoucd.product.service.SkuBaseAttrValueService;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.R;



/**
 * sku基本属性&值
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@RestController
@RequestMapping("product/skubaseattrvalue")
public class SkuBaseAttrValueController {
    @Autowired
    private SkuBaseAttrValueService skuBaseAttrValueService;

    /**
     * 列表
     */
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = skuBaseAttrValueService.queryPage(params);

        return R.ok().put("data", page);
    }


    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		SkuBaseAttrValueEntity skuBaseAttrValue = skuBaseAttrValueService.getById(id);

        return R.ok().put("data", skuBaseAttrValue);
    }

    /**
     * 保存
     */
    @RequestMapping("/save")
    public R save(@RequestBody SkuBaseAttrValueEntity skuBaseAttrValue){
		skuBaseAttrValueService.save(skuBaseAttrValue);

        return R.ok();
    }

    /**
     * 修改
     */
    @RequestMapping("/update")
    public R update(@RequestBody SkuBaseAttrValueEntity skuBaseAttrValue){
		skuBaseAttrValueService.updateById(skuBaseAttrValue);

        return R.ok();
    }

    /**
     * 删除
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		skuBaseAttrValueService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

}
