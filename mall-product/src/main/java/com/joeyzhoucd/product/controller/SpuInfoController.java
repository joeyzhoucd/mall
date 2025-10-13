package com.joeyzhoucd.product.controller;

import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.R;
import com.joeyzhoucd.product.service.SpuInfoService;
import com.joeyzhoucd.product.vo.SpuSaveVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;


/**
 * spu信息
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
     * 列表
     */
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = spuInfoService.queryPage(params);
        return R.ok().put("page", page);
    }

    /**
     * 保存SPU信息
     */
    @PostMapping("/save")
    public R save(@RequestBody SpuSaveVo spuSaveVo) {
        try {
            spuInfoService.saveSpuInfo(spuSaveVo);
            return R.ok();
        } catch (Exception e) {
            return R.error("保存失败：" + e.getMessage());
        }
    }

    /**
     * 预留接口 - SPU信息功能待开发
     */
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "SPU信息功能待开发");
    }
}
