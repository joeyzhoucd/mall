package com.mall.product.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.product.entity.AttrGroupEntity;
import com.mall.product.service.AttrGroupService;
import com.mall.product.vo.AttrGroupWithAttrVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;



@RestController
@RequestMapping("product/attrgroup")
public class AttrGroupController {
    @Autowired
    private AttrGroupService attrGroupService;

    
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params) {
        PageUtils page = attrGroupService.queryPage(params);
        return R.ok().put("data", page);
    }

    @RequestMapping("/withattr/{categoryId}")
    public R list(@PathVariable("categoryId") Long categoryId) {
        List<AttrGroupWithAttrVO> attrGroupWithAttrs = attrGroupService.getAttrGroupWithAttrs(categoryId);
        return R.ok().put("data", attrGroupWithAttrs);
    }


    
    @RequestMapping("/info/{attrGroupId}")
    public R info(@PathVariable("attrGroupId") Long attrGroupId) {
        AttrGroupEntity attrGroup = attrGroupService.getById(attrGroupId);

        return R.ok().put("data", attrGroup);
    }

    
    @RequestMapping("/save")
    public R save(@RequestBody AttrGroupEntity attrGroup) {
        attrGroupService.save(attrGroup);

        return R.ok();
    }

    
    @RequestMapping("/update")
    public R update(@RequestBody AttrGroupEntity attrGroup) {
        attrGroupService.updateById(attrGroup);

        return R.ok();
    }

    
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] attrGroupIds) {
        attrGroupService.removeByIds(Arrays.asList(attrGroupIds));

        return R.ok();
    }

    @RequestMapping("/delete/{attrGroupId}")
    public R deleteById(@PathVariable("attrGroupId") Long attrGroupId) {
        attrGroupService.removeById(attrGroupId);

        return R.ok();
    }

}
