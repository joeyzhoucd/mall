package com.mall.product.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.product.service.SpuInfoService;
import com.mall.product.vo.SpuSaveVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import com.mall.product.entity.SpuInfoEntity;
import java.util.ArrayList;
import java.util.List;



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

    /**
     * 查单个 SPU。
     */
    @GetMapping("/info/{spuId}")
    public R info(@PathVariable("spuId") Long spuId) {
        SpuInfoEntity spuInfo = spuInfoService.getById(spuId);
        if (spuInfo == null) {
            return R.error("SPU 不存在: " + spuId);
        }
        return R.ok().put("spuInfo", spuInfo);
    }

    /**
     * 商品下架：置 publish_status=0 并从 ES 移除。
     *
     * <p>请求体是 id 数组（前端单个下架也发数组）。逐个处理而不是批量，
     * 是为了让其中一个失败不影响其余 —— 但这样就【不是原子的】，
     * 所以把失败的 id 明确回给调用方，而不是笼统报一个「操作失败」。
     */
    @PostMapping("/unpublish")
    public R unpublish(@RequestBody List<Long> spuIds) {
        if (spuIds == null || spuIds.isEmpty()) {
            return R.error("spuIds 不能为空");
        }
        List<String> failures = new ArrayList<>();
        for (Long spuId : spuIds) {
            try {
                spuInfoService.downSpu(spuId);
            } catch (Exception e) {
                failures.add(spuId + ": " + e.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            return R.error("部分下架失败 -> " + String.join("; ", failures));
        }
        return R.ok();
    }

    /**
     * 删除 SPU 及其下的 SKU、图片、属性，并清理 ES 文档。
     */
    @PostMapping("/delete")
    public R delete(@RequestBody List<Long> spuIds) {
        try {
            spuInfoService.removeSpus(spuIds);
            return R.ok();
        } catch (Exception e) {
            return R.error("删除失败: " + e.getMessage());
        }
    }

}