package com.mall.coupon.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.coupon.entity.CouponSpuRelationEntity;
import com.mall.coupon.service.CouponSpuRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mall.common.constant.ResponseKeys;
import com.mall.common.utils.RUtils;
import com.mall.coupon.feign.ProductFeignService;
import com.mall.coupon.vo.SkuInfoVo;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.List;




@RestController
@RequestMapping("coupon/couponspurelation")
public class CouponSpuRelationController {
    @Autowired
    private CouponSpuRelationService couponSpuRelationService;

    
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = couponSpuRelationService.queryPage(params);

        return R.ok().put("page", page);
    }


    
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		CouponSpuRelationEntity couponSpuRelation = couponSpuRelationService.getById(id);

        return R.ok().put("couponSpuRelation", couponSpuRelation);
    }

    
    @RequestMapping("/save")
    public R save(@RequestBody CouponSpuRelationEntity couponSpuRelation){
		couponSpuRelationService.save(couponSpuRelation);

        return R.ok();
    }

    
    @RequestMapping("/update")
    public R update(@RequestBody CouponSpuRelationEntity couponSpuRelation){
		couponSpuRelationService.updateById(couponSpuRelation);

        return R.ok();
    }

    
    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		couponSpuRelationService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }


    @Autowired
    private ProductFeignService productFeignService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 给某个 SKU 绑定优惠券。
     *
     * <h3>为什么传的是 skuId，落库却是 spuId</h3>
     * 后台的绑券按钮在 SKU 列表页上，传的是 skuId；而 sms_coupon_spu_relation
     * 这张表是 <b>coupon ↔ SPU</b> 的关联，没有 SKU 粒度。这里先通过 mall-product
     * 把 skuId 解析成 spuId，再按 SPU 落库。
     *
     * <p>也就是说<b>绑券会作用于该 SPU 下的全部规格</b>，不只是点的那一个。
     * 这一点必须让操作的人知道，所以成功响应里带上 spuId 和这句说明，
     * 而不是静悄悄地返回 ok。
     *
     * <p>为什么这里可以放宽到 SPU，而 SKU 的上下架不行（见
     * {@code SkuInfoEntity#publishStatus} 的注释里为什么专门加了一列）：
     * 「某个规格断货要单独下架，其他规格照卖」是常见需求，把它映射到 SPU 会误伤；
     * 而优惠券按商品维度发放本来就是通行做法 —— 促销面向的是商品，不是某个颜色尺码。
     * 两件事表面都是「SKU 传进来、只有 SPU 粒度」，处理方式不同是因为业务语义不同，
     * 不是因为哪个实现起来更省事。
     */
    @PostMapping("/bind")
    public R bind(@RequestBody Map<String, Object> body) {
        Object skuIdObj = body.get("skuId");
        Object couponIdsObj = body.get("couponIds");
        if (skuIdObj == null || !(couponIdsObj instanceof List<?> rawCouponIds)) {
            return R.error("参数不完整：需要 skuId 和 couponIds");
        }

        final Long skuId;
        final List<Long> couponIds;
        try {
            skuId = Long.valueOf(String.valueOf(skuIdObj));
            // 前端把 id 都转成了字符串再发，所以统一走 String -> Long，
            // 不要直接强转 (Long)，那会在运行期抛 ClassCastException。
            couponIds = rawCouponIds.stream().map(o -> Long.valueOf(String.valueOf(o))).toList();
        } catch (NumberFormatException e) {
            return R.error("skuId / couponIds 必须是数字");
        }
        if (couponIds.isEmpty()) {
            return R.ok().put("bound", 0);
        }

        SkuInfoVo skuInfo;
        try {
            R resp = productFeignService.getSkuInfo(skuId);
            skuInfo = RUtils.getData(resp, ResponseKeys.SKU_INFO, objectMapper, new TypeReference<SkuInfoVo>() {});
        } catch (Exception e) {
            // 这里【不能】吞掉异常走兜底：拿不到 spuId 就没法确定绑到哪个商品上，
            // 硬绑只会产生一条指向错误 SPU 的关联，比失败更糟。
            return R.error("查询商品信息失败，无法确定所属 SPU: " + e.getMessage());
        }
        if (skuInfo == null || skuInfo.getSpuId() == null) {
            return R.error("SKU " + skuId + " 不存在或缺少 spuId");
        }
        Long spuId = skuInfo.getSpuId();

        // 已存在的关联跳过。重复插入不会报错（表上没有唯一约束），
        // 但会让同一张券在同一个 SPU 上出现多条，后续按券统计商品数时翻倍。
        List<Long> existing = couponSpuRelationService.list(
                        new QueryWrapper<CouponSpuRelationEntity>()
                                .eq("spu_id", spuId).in("coupon_id", couponIds))
                .stream().map(CouponSpuRelationEntity::getCouponId).toList();

        List<CouponSpuRelationEntity> toSave = couponIds.stream()
                .filter(cid -> !existing.contains(cid))
                .map(cid -> {
                    CouponSpuRelationEntity rel = new CouponSpuRelationEntity();
                    rel.setCouponId(cid);
                    rel.setSpuId(spuId);
                    rel.setSpuName(skuInfo.getSkuName());
                    return rel;
                }).toList();

        if (!toSave.isEmpty()) {
            couponSpuRelationService.saveBatch(toSave);
        }
        return R.ok()
                .put("spuId", spuId)
                .put("bound", toSave.size())
                .put("skipped", couponIds.size() - toSave.size())
                .put("note", "优惠券绑定在 SPU " + spuId + " 上，对该商品的全部规格生效");
    }

}
