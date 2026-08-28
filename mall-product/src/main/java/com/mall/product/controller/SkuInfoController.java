package com.mall.product.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.product.entity.SkuInfoEntity;
import com.mall.product.service.SkuInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("product/skuinfo")
public class SkuInfoController {
    @Autowired
    private SkuInfoService skuInfoService;

    
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = skuInfoService.queryPageWithDetails(params);
        return R.ok().put("page", page);
    }

    /**
     * 批量删除 SKU。请求体是一个 id 数组（前端单个删除也发数组，只是长度为 1）。
     */
    @PostMapping("/delete")
    public R delete(@RequestBody List<Long> skuIds) {
        try {
            skuInfoService.removeSkus(skuIds);
            return R.ok();
        } catch (Exception e) {
            return R.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 修改 SKU 基础信息。只有名称/标题/副标题/价格会生效，
     * 请求体里的其它字段一律忽略（见 SkuInfoService#updateBasicInfo）。
     */
    @PostMapping("/update")
    public R update(@RequestBody SkuInfoEntity sku) {
        try {
            skuInfoService.updateBasicInfo(sku);
            return R.ok();
        } catch (IllegalArgumentException e) {
            return R.error(e.getMessage());
        } catch (Exception e) {
            return R.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 批量上下架。
     *
     * <p>请求体形如 {@code {"skuIds":[1,2],"publishStatus":1}}。
     * 用 Map 接而不是定义 VO，是为了和前端已有的调用保持一致 ——
     * 前端把 skuIds 传成数字数组、publishStatus 传成数字，
     * 这里用 Number 统一取值，避免 Integer/Long 强转在运行期抛 ClassCastException。
     */
    @PostMapping("/batchPublish")
    public R batchPublish(@RequestBody Map<String, Object> body) {
        try {
            Object idsObj = body.get("skuIds");
            Object statusObj = body.get("publishStatus");
            if (!(idsObj instanceof List<?> rawIds) || statusObj == null) {
                return R.error("参数不完整：需要 skuIds 和 publishStatus");
            }
            List<Long> skuIds = rawIds.stream()
                    .map(o -> Long.valueOf(String.valueOf(o)))
                    .toList();
            int status = Integer.parseInt(String.valueOf(statusObj));
            int updated = skuInfoService.batchPublish(skuIds, status);
            return R.ok().put("updated", updated);
        } catch (IllegalArgumentException e) {
            return R.error(e.getMessage());
        } catch (Exception e) {
            return R.error("操作失败: " + e.getMessage());
        }
    }
}
