package com.mall.product.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.product.service.SpuInfoService;
import com.mall.product.vo.SpuSaveVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;



@RestController
@RequestMapping("product/spuinfo")
public class SpuInfoController {
    @Autowired
    private SpuInfoService spuInfoService;

    
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = spuInfoService.queryPage(params);
        return R.ok().put("page", page);
    }

    
    @PostMapping("/save")
    public R save(@RequestBody SpuSaveVo spuSaveVo) {
        try {
            spuInfoService.saveSpuInfo(spuSaveVo);
            return R.ok();
        } catch (Exception e) {
            return R.error("保存失败: " + e.getMessage());
        }
    }

    /**
     * 商品上架
     */
    @PostMapping("/{spuId}/up")
    public R spuUp(@PathVariable("spuId") Long spuId) {
        try {
            spuInfoService.upSpu(spuId);
            return R.ok();
        } catch (Exception e) {
            return R.error("商品上架失败: " + e.getMessage());
        }
    }

    
    @RequestMapping("/placeholder")
    public R placeholder() {
        return R.ok().put("message", "SPU信息占位符方法");
    }
}