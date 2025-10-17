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

/**
 * å•†å“å±žæ€§æŽ§åˆ¶å™¨
 * ä¸“é—¨å¤„ç†è§„æ ¼å‚æ•°ç›¸å…³çš„APIæŽ¥å£
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
     * èŽ·å–è§„æ ¼å‚æ•°åˆ—è¡¨
     */
    @RequestMapping("/spec/list")
    public R listSpecAttr(@RequestParam Map<String, Object> params) {
        return getAttrList(attrService.querySpecAttrPage(params));
    }

    /**
     * æ–°å¢žè§„æ ¼å‚æ•°
     */
    @PostMapping("/spec/save")
    public R save(@RequestBody AttrSaveRequestVO req) {
        attrService.saveBaseAttr(req);
        return R.ok();
    }

    /**
     * ä¿®æ”¹è§„æ ¼å‚æ•°
     */
    @PostMapping("/spec/update")
    public R update(@RequestBody AttrSaveRequestVO req) {
        attrService.updateBaseAttr(req);
        return R.ok();
    }

    /**
     * æ‰¹é‡åˆ é™¤è§„æ ¼å‚æ•°
     */
    @PostMapping("/spec/delete")
    public R deleteSpec(@RequestBody Long[] attrIds) {
        return deleteAttrs(attrIds);
    }

    /**
     * å•ä¸ªåˆ é™¤è§„æ ¼å‚æ•°
     */
    @PostMapping("/spec/delete/{attrId}")
    public R deleteSpecById(@PathVariable("attrId") Long attrId) {
        return deleteAttr(attrId);
    }

    /**
     * å¯ç”¨/ç¦ç”¨è§„æ ¼å‚æ•°
     */
    @PostMapping("/spec/updateEnable")
    public R updateEnable(@RequestBody Map<String, Object> params) {
        return updateAttrEnable(params);
    }

    // ==================== é”€å”®å±žæ€§ç›¸å…³æŽ¥å£ ====================

    /**
     * èŽ·å–é”€å”®å±žæ€§åˆ—è¡¨
     */
    @RequestMapping("/sale/list")
    public R listSaleAttr(@RequestParam Map<String, Object> params) {
        return getAttrList(attrService.querySaleAttrPage(params));
    }

	/**
	 * èŽ·å–é”€å”®å±žæ€§åˆ—è¡¨ï¼ˆå…¼å®¹è·¯å¾„å‚æ•°æ–¹å¼ï¼‰
	 * ç¤ºä¾‹ï¼š/product/attr/sale/list/{categoryId}
	 */
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

    /**
     * æ–°å¢žé”€å”®å±žæ€§
     */
    @PostMapping("/sale/save")
    public R saveSaleAttr(@RequestBody AttrSaveRequestVO req) {
        attrService.saveSaleAttr(req);
        return R.ok();
    }

    /**
     * ä¿®æ”¹é”€å”®å±žæ€§
     */
    @PostMapping("/sale/update")
    public R updateSaleAttr(@RequestBody AttrSaveRequestVO req) {
        attrService.updateSaleAttr(req);
        return R.ok();
    }

    /**
     * æ‰¹é‡åˆ é™¤é”€å”®å±žæ€§
     */
    @PostMapping("/sale/delete")
    public R deleteSaleAttr(@RequestBody Long[] attrIds) {
        return deleteAttrs(attrIds);
    }

    /**
     * å•ä¸ªåˆ é™¤é”€å”®å±žæ€§
     */
    @PostMapping("/sale/delete/{attrId}")
    public R deleteSaleAttrById(@PathVariable("attrId") Long attrId) {
        return deleteAttr(attrId);
    }

    /**
     * å¯ç”¨/ç¦ç”¨é”€å”®å±žæ€§
     */
    @PostMapping("/sale/updateEnable")
    public R updateSaleAttrEnable(@RequestBody Map<String, Object> params) {
        return updateAttrEnable(params);
    }

    // ==================== ç§æœ‰å…¬å…±æ–¹æ³• ====================

    /**
     * æ‰¹é‡åˆ é™¤å±žæ€§
     */
    private R deleteAttrs(Long[] attrIds) {
        attrService.deleteAttrsWithRelations(attrIds);
        return R.ok();
    }

    /**
     * å•ä¸ªåˆ é™¤å±žæ€§
     */
    private R deleteAttr(Long attrId) {
        attrService.deleteAttrWithRelations(attrId);
        return R.ok();
    }

    /**
     * æ›´æ–°å±žæ€§å¯ç”¨çŠ¶æ€
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
     * èŽ·å–å±žæ€§åˆ—è¡¨
     */
    private R getAttrList(PageUtils page) {
        return R.ok().put("data", page);
    }

    /**
     * èŽ·å–æœªå…³è”çš„å±žæ€§åˆ—è¡¨ï¼ˆç”¨äºŽå±žæ€§åˆ†ç»„å…³è”ï¼‰
     */
    @RequestMapping("/list")
    public R listAttrs(@RequestParam Map<String, Object> params) {
        Object categoryId = params.get("categoryId");
        Object attrType = params.get("attrType");

        if (categoryId != null && attrType != null) {
            // å¦‚æžœæ˜¯åŸºæœ¬å±žæ€§ï¼Œè¿”å›žæœªå…³è”çš„å±žæ€§
            if (Integer.valueOf(attrType.toString()) == 1) {
                // è¿™é‡Œéœ€è¦ä¼ å…¥attrGroupIdï¼Œä½†å‰ç«¯æ²¡æœ‰ä¼ ï¼Œæˆ‘ä»¬éœ€è¦ç‰¹æ®Šå¤„ç†
                // æš‚æ—¶è¿”å›žæ‰€æœ‰åŸºæœ¬å±žæ€§ï¼Œè®©å‰ç«¯è¿‡æ»¤
                params.put("attr_type", 1);
                PageUtils page = attrService.querySpecAttrPage(params);
                return R.ok().put("data", page);
            } else {
                // é”€å”®å±žæ€§
                PageUtils page = attrService.querySaleAttrPage(params);
                return R.ok().put("data", page);
            }
        }

        return R.error("å‚æ•°é”™è¯¯");
    }

    /**
     * èŽ·å–æŒ‡å®šåˆ†ç»„ä¸‹æœªå…³è”çš„å±žæ€§åˆ—è¡¨
     */
    @RequestMapping("/unrelated/{attrGroupId}")
    public R getUnRelatedAttrs(@PathVariable("attrGroupId") Long attrGroupId) {
        List<AttrEntity> attrs = attrService.queryUnRelatedAttr(attrGroupId);
        return R.ok().put("data", attrs);
    }
}
