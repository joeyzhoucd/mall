package com.joeyzhoucd.product.controller;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.joeyzhoucd.product.vo.AttrSaveRequestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.joeyzhoucd.product.entity.AttrEntity;
import com.joeyzhoucd.product.service.AttrService;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.R;



/**
 * 商品属性
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@RestController
@RequestMapping("product/attr")
public class AttrController {
    @Autowired
    private AttrService attrService;

    /**
     * 列表
     */
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = attrService.queryPage(params);
        return R.ok().put("data", page);
    }

    /**
     * 规格列表
     */
    @RequestMapping("/spec/list")
    public R listSpecAttr(@RequestParam Map<String, Object> params){
        PageUtils page = attrService.querySpecAttrPage(params);
        return R.ok().put("data", page);
    }


    @PostMapping("/spec/save")
    public R save(@RequestBody AttrSaveRequestVO req) {
        attrService.saveBaseAttr(req);
        return R.ok();
    }

    @PostMapping("/spec/update")
    public R update(@RequestBody AttrSaveRequestVO req) {
        return R.ok();
    }

    @GetMapping("/listUnRelatedAttr/{attrgroupId}")
    public R listUnRelatedAttr(@PathVariable("attrgroupId") Long attrgroupId){
        List<AttrEntity> data = attrService.queryUnRelatedAttr(attrgroupId);
        return R.ok().put("data", data);
    }

    /**
     * 信息
     */
    @RequestMapping("/info/{attrId}")
    public R info(@PathVariable("attrId") Long attrId){
		AttrEntity attr = attrService.getById(attrId);

        return R.ok().put("data", attr);
    }

    /**
     * 保存
     */
    @RequestMapping("/save")
    public R save(@RequestBody AttrEntity attr){
		attrService.save(attr);

        return R.ok();
    }

    /**
     * 修改
     */
    @RequestMapping("/update")
    public R update(@RequestBody AttrEntity attr){
		attrService.updateById(attr);
        return R.ok();
    }

    /**
     * 删除
     */
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] attrIds){
		attrService.removeByIds(Arrays.asList(attrIds));
        return R.ok();
    }

}
