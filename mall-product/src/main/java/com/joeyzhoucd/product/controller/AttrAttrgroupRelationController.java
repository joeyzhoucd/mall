package com.joeyzhoucd.product.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.joeyzhoucd.product.vo.AttrAttrgroupRelationVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.joeyzhoucd.product.entity.AttrAttrgroupRelationEntity;
import com.joeyzhoucd.product.service.AttrAttrgroupRelationService;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.R;



/**
 * 属性&属性分组关联
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@RestController
@RequestMapping("product/attrattrgrouprelation")
public class AttrAttrgroupRelationController {
    @Autowired
    private AttrAttrgroupRelationService attrAttrgroupRelationService;

    /**
     * 列表
     */
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = attrAttrgroupRelationService.queryPage(params);

        return R.ok().put("data", page);
    }

    @GetMapping("/getAttrsByGroupId/{groupId}")
    public R getAttrsByGroupId(@PathVariable("groupId") Long groupId){
        List<AttrAttrgroupRelationVO> relations = attrAttrgroupRelationService.getAttrsByGroupId(groupId);
        return R.ok().put("data", relations);
    }

    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		AttrAttrgroupRelationEntity attrAttrgroupRelation = attrAttrgroupRelationService.getById(id);

        return R.ok().put("data", attrAttrgroupRelation);
    }

    /**
     * 保存
     */
    @RequestMapping("/save")
    public R save(@RequestBody AttrAttrgroupRelationEntity attrAttrgroupRelation){
		attrAttrgroupRelationService.save(attrAttrgroupRelation);

        return R.ok();
    }

    /**
     * 修改
     */
    @RequestMapping("/update")
    public R update(@RequestBody AttrAttrgroupRelationEntity attrAttrgroupRelation){
		attrAttrgroupRelationService.updateById(attrAttrgroupRelation);

        return R.ok();
    }

    /**
     * 删除
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		attrAttrgroupRelationService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

}
