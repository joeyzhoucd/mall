package com.joeyzhoucd.product.controller;

import java.util.Arrays;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.joeyzhoucd.product.entity.AttrEntity;
import com.joeyzhoucd.product.service.AttrService;
import com.joeyzhoucd.product.vo.AttrSaveRequestVO;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.R;

/**
 * 商品属性控制器
 * 专门处理规格参数相关的API接口
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
     * 获取规格参数列表
     */
    @RequestMapping("/spec/list")
    public R listSpecAttr(@RequestParam Map<String, Object> params) {
        return getAttrList(attrService.querySpecAttrPage(params));
    }

    /**
     * 新增规格参数
     */
    @PostMapping("/spec/save")
    public R save(@RequestBody AttrSaveRequestVO req) {
        attrService.saveBaseAttr(req);
        return R.ok();
    }

    /**
     * 修改规格参数
     */
    @PostMapping("/spec/update")
    public R update(@RequestBody AttrSaveRequestVO req) {
        attrService.updateBaseAttr(req);
        return R.ok();
    }

    /**
     * 批量删除规格参数
     */
    @PostMapping("/spec/delete")
    public R deleteSpec(@RequestBody Long[] attrIds) {
        return deleteAttrs(attrIds);
    }

    /**
     * 单个删除规格参数
     */
    @PostMapping("/spec/delete/{attrId}")
    public R deleteSpecById(@PathVariable("attrId") Long attrId) {
        return deleteAttr(attrId);
    }

    /**
     * 启用/禁用规格参数
     */
    @PostMapping("/spec/updateEnable")
    public R updateEnable(@RequestBody Map<String, Object> params) {
        return updateAttrEnable(params);
    }

    // ==================== 销售属性相关接口 ====================

    /**
     * 获取销售属性列表
     */
    @RequestMapping("/sale/list")
    public R listSaleAttr(@RequestParam Map<String, Object> params) {
        return getAttrList(attrService.querySaleAttrPage(params));
    }

    /**
     * 新增销售属性
     */
    @PostMapping("/sale/save")
    public R saveSaleAttr(@RequestBody AttrSaveRequestVO req) {
        attrService.saveSaleAttr(req);
        return R.ok();
    }

    /**
     * 修改销售属性
     */
    @PostMapping("/sale/update")
    public R updateSaleAttr(@RequestBody AttrSaveRequestVO req) {
        attrService.updateSaleAttr(req);
        return R.ok();
    }

    /**
     * 批量删除销售属性
     */
    @PostMapping("/sale/delete")
    public R deleteSaleAttr(@RequestBody Long[] attrIds) {
        return deleteAttrs(attrIds);
    }

    /**
     * 单个删除销售属性
     */
    @PostMapping("/sale/delete/{attrId}")
    public R deleteSaleAttrById(@PathVariable("attrId") Long attrId) {
        return deleteAttr(attrId);
    }

    /**
     * 启用/禁用销售属性
     */
    @PostMapping("/sale/updateEnable")
    public R updateSaleAttrEnable(@RequestBody Map<String, Object> params) {
        return updateAttrEnable(params);
    }

    // ==================== 私有公共方法 ====================

    /**
     * 批量删除属性
     */
    private R deleteAttrs(Long[] attrIds) {
        attrService.deleteAttrsWithRelations(attrIds);
        return R.ok();
    }

    /**
     * 单个删除属性
     */
    private R deleteAttr(Long attrId) {
        attrService.deleteAttrWithRelations(attrId);
        return R.ok();
    }

    /**
     * 更新属性启用状态
     */
    private R updateAttrEnable(Map<String, Object> params) {
        Long attrId = Long.valueOf(params.get("attrId").toString());
        Integer enable = Integer.valueOf(params.get("enable").toString());
        AttrEntity attr = new AttrEntity();
        attr.setAttrId(attrId);
        attr.setEnable(enable.longValue());
        attrService.updateById(attr);
        return R.ok();
    }

    /**
     * 获取属性列表
     */
    private R getAttrList(PageUtils page) {
        return R.ok().put("data", page);
    }
}
