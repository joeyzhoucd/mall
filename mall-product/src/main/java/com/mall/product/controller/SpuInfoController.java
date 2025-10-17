package com.mall.product.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.product.service.SpuInfoService;
import com.mall.product.vo.SpuSaveVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


/**
 * spuä¿¡æ¯
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@RestController
@RequestMapping("product/spuinfo")
public class SpuInfoController {
    @Autowired
    private SpuInfoService spuInfoService;

    /**
     * åˆ—è¡¨
     */
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = spuInfoService.queryPage(params);
        return R.ok().put("page", page);
    }

    /**
     * ä¿å­˜SPUä¿¡æ¯
     */
    @PostMapping("/save")
    public R save(@RequestBody SpuSaveVo spuSaveVo) {
        try {
            spuInfoService.saveSpuInfo(spuSaveVo);
            return R.ok();
        } catch (Exception e) {
            return R.error("ä¿å­˜å¤±è´¥ï¼š" + e.getMessage());
        }
    }

    /**
     * é¢„ç•™æŽ¥å£ - SPUä¿¡æ¯åŠŸèƒ½å¾…å¼€å‘
     */
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "SPUä¿¡æ¯åŠŸèƒ½å¾…å¼€å‘");
    }
}
