package com.mall.product.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.product.entity.AttrAttrgroupRelationEntity;
import com.mall.product.service.AttrAttrgroupRelationService;
import com.mall.product.vo.AttrAttrgroupRelationVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;



@RestController
@RequestMapping("product/attrattrgrouprelation")
public class AttrAttrgroupRelationController {
    @Autowired
    private AttrAttrgroupRelationService attrAttrgroupRelationService;

    
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params) {
        PageUtils page = attrAttrgroupRelationService.queryPage(params);

        return R.ok().put("data", page);
    }

    @GetMapping("/getAttrsByGroupId/{groupId}")
    public R getAttrsByGroupId(@PathVariable("groupId") Long groupId) {
        List<AttrAttrgroupRelationVO> relations = attrAttrgroupRelationService.getAttrsByGroupId(groupId);
        return R.ok().put("data", relations);
    }

    
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id) {
        AttrAttrgroupRelationEntity attrAttrgroupRelation = attrAttrgroupRelationService.getById(id);

        return R.ok().put("data", attrAttrgroupRelation);
    }

    
    @RequestMapping("/save")
    public R save(@RequestBody AttrAttrgroupRelationEntity attrAttrgroupRelation) {
        attrAttrgroupRelationService.save(attrAttrgroupRelation);

        return R.ok();
    }

    
    @RequestMapping("/update")
    public R update(@RequestBody AttrAttrgroupRelationEntity attrAttrgroupRelation) {
        attrAttrgroupRelationService.updateById(attrAttrgroupRelation);

        return R.ok();
    }

    
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids) {
        attrAttrgroupRelationService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

    
    @RequestMapping("/delete/{attrId}/{groupId}")
    public R deleteRelation(@PathVariable("attrId") Long attrId, @PathVariable("groupId") Long groupId) {
        attrAttrgroupRelationService.removeRelation(attrId, groupId);
        return R.ok();
    }

    
    @RequestMapping("/saveBatch")
    public R saveBatch(@RequestBody List<AttrAttrgroupRelationEntity> relations) {
        attrAttrgroupRelationService.saveBatch(relations);
        return R.ok();
    }

}
