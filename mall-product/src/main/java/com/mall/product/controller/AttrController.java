package com.mall.product.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.product.entity.AttrEntity;
import com.mall.product.service.AttrService;
import com.mall.product.vo.AttrSaveRequestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("product/attr")
public class AttrController {

    @Autowired
    private AttrService attrService;

    
    @RequestMapping("/spec/list")
    public R listSpecAttr(@RequestParam Map<String, Object> params) {
        return getAttrList(attrService.querySpecAttrPage(params));
    }

    
    @PostMapping("/spec/save")
    public R save(@RequestBody AttrSaveRequestVO req) {
        attrService.saveBaseAttr(req);
        return R.ok();
    }

    
    @PostMapping("/spec/update")
    public R update(@RequestBody AttrSaveRequestVO req) {
        attrService.updateBaseAttr(req);
        return R.ok();
    }

    
    @PostMapping("/spec/delete")
    public R deleteSpec(@RequestBody Long[] attrIds) {
        return deleteAttrs(attrIds);
    }

    
    @PostMapping("/spec/delete/{attrId}")
    public R deleteSpecById(@PathVariable("attrId") Long attrId) {
        return deleteAttr(attrId);
    }

    
    @PostMapping("/spec/updateEnable")
    public R updateEnable(@RequestBody Map<String, Object> params) {
        return updateAttrEnable(params);
    }

    // ==================== Sale Attributes ====================

    
    @RequestMapping("/sale/list")
    public R listSaleAttr(@RequestParam Map<String, Object> params) {
        return getAttrList(attrService.querySaleAttrPage(params));
    }

	
	@GetMapping("/sale/list/{categoryId}")
	public R listSaleAttrByPath(@PathVariable("categoryId") Long categoryId, @RequestParam Map<String, Object> params) {
		params.put("categoryId", categoryId);
		if (!params.containsKey("page")) {
			params.put("page", "1");
		}
		if (!params.containsKey("limit")) {
			params.put("limit", "500");
		}
		return getAttrList(attrService.querySaleAttrPage(params));
	}

    
    @PostMapping("/sale/save")
    public R saveSaleAttr(@RequestBody AttrSaveRequestVO req) {
        attrService.saveSaleAttr(req);
        return R.ok();
    }

    
    @PostMapping("/sale/update")
    public R updateSaleAttr(@RequestBody AttrSaveRequestVO req) {
        attrService.updateSaleAttr(req);
        return R.ok();
    }

    
    @PostMapping("/sale/delete")
    public R deleteSaleAttr(@RequestBody Long[] attrIds) {
        return deleteAttrs(attrIds);
    }

    
    @PostMapping("/sale/delete/{attrId}")
    public R deleteSaleAttrById(@PathVariable("attrId") Long attrId) {
        return deleteAttr(attrId);
    }

    
    @PostMapping("/sale/updateEnable")
    public R updateSaleAttrEnable(@RequestBody Map<String, Object> params) {
        return updateAttrEnable(params);
    }

    // ==================== Helper Methods ====================

    
    private R deleteAttrs(Long[] attrIds) {
        attrService.deleteAttrsWithRelations(attrIds);
        return R.ok();
    }

    
    private R deleteAttr(Long attrId) {
        attrService.deleteAttrWithRelations(attrId);
        return R.ok();
    }

    
    private R updateAttrEnable(Map<String, Object> params) {
        Long attrId = Long.valueOf(params.get("attrId").toString());
        Integer enable = Integer.valueOf(params.get("enable").toString());
        AttrEntity attr = new AttrEntity();
        attr.setAttrId(attrId);
        attr.setEnable(enable.longValue());
        attrService.updateById(attr);
        return R.ok();
    }

    
    private R getAttrList(PageUtils page) {
        return R.ok().put("data", page);
    }

    
    @RequestMapping("/list")
    public R listAttrs(@RequestParam Map<String, Object> params) {
        Object categoryId = params.get("categoryId");
        Object attrType = params.get("attrType");

        if (categoryId != null && attrType != null) {
            // Check if it's a specification attribute
            if (Integer.valueOf(attrType.toString()) == 1) {
                // Query specification attributes by attrGroupId and return specification attributes
                // For now, query all specification attributes
                params.put("attr_type", 1);
                PageUtils page = attrService.querySpecAttrPage(params);
                return R.ok().put("data", page);
            } else {
                // Sale attributes
                PageUtils page = attrService.querySaleAttrPage(params);
                return R.ok().put("data", page);
            }
        }

        return R.error("Invalid parameters");
    }

    
    @RequestMapping("/unrelated/{attrGroupId}")
    public R getUnRelatedAttrs(@PathVariable("attrGroupId") Long attrGroupId) {
        List<AttrEntity> attrs = attrService.queryUnRelatedAttr(attrGroupId);
        return R.ok().put("data", attrs);
    }
}