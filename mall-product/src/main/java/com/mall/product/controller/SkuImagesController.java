package com.mall.product.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.product.entity.SkuImagesEntity;
import com.mall.product.service.SkuImagesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("product/skuimages")
public class SkuImagesController {
    @Autowired
    private SkuImagesService skuImagesService;

    /**
     * 按 skuId 分页查图片。
     *
     * <p>skuId 由 service 层过滤 —— 那里的注释说明了为什么这个条件不能少：
     * 后台编辑图片的流程是「查出来 -> 全删 -> 重存」，条件漏了会删掉别的 SKU 的图。
     */
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params) {
        PageUtils page = skuImagesService.queryPage(params);
        return R.ok().put("page", page);
    }

    @PostMapping("/save")
    public R save(@RequestBody SkuImagesEntity skuImages) {
        if (skuImages == null || skuImages.getSkuId() == null) {
            return R.error("skuId 不能为空");
        }
        try {
            skuImagesService.save(skuImages);
            return R.ok();
        } catch (Exception e) {
            return R.error("保存失败: " + e.getMessage());
        }
    }

    @PostMapping("/delete")
    public R delete(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            // 空数组直接返回成功而不是去删。MyBatis-Plus 的 removeByIds 遇到空集合
            // 会抛异常，而前端在「原本就没有图片」时确实会发空数组过来。
            return R.ok();
        }
        try {
            skuImagesService.removeByIds(ids);
            return R.ok();
        } catch (Exception e) {
            return R.error("删除失败: " + e.getMessage());
        }
    }
}
