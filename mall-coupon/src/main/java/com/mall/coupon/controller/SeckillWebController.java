package com.mall.coupon.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.constant.ResponseKeys;
import com.mall.common.utils.R;
import com.mall.common.utils.RUtils;
import com.mall.coupon.entity.SeckillSkuRelationEntity;
import com.mall.coupon.feign.ProductFeignService;
import com.mall.coupon.service.SeckillSkuRelationService;
import com.mall.coupon.vo.SeckillPageVo;
import com.mall.coupon.vo.SkuInfoVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 秒杀抢购页（Thymeleaf 渲染），走 seckill.mall.com 域名。
 */
@Controller
public class SeckillWebController {

    private static final Logger log = LoggerFactory.getLogger(SeckillWebController.class);

    @Autowired
    private SeckillSkuRelationService seckillSkuRelationService;

    @Autowired
    private ProductFeignService productFeignService;

    @Autowired
    private ObjectMapper objectMapper;

    @GetMapping("/seckill.html")
    public String seckillPage(@RequestParam("relationId") Long relationId, Model model) {
        SeckillSkuRelationEntity relation = seckillSkuRelationService.getById(relationId);
        if (relation == null) {
            return "seckillNotFound";
        }

        SeckillPageVo pageVo = new SeckillPageVo();
        pageVo.setRelationId(relation.getId());
        pageVo.setSkuId(relation.getSkuId());
        pageVo.setSeckillPrice(relation.getSeckillPrice());
        pageVo.setSeckillCount(relation.getSeckillCount() == null ? 0 : relation.getSeckillCount().intValue());
        pageVo.setSoldCount(relation.getSoldCount() == null ? 0 : relation.getSoldCount());

        try {
            R skuResp = productFeignService.getSkuInfo(relation.getSkuId());
            SkuInfoVo skuInfo = RUtils.getData(skuResp, ResponseKeys.SKU_INFO, objectMapper, new TypeReference<SkuInfoVo>() {});
            if (skuInfo != null) {
                pageVo.setSkuName(skuInfo.getSkuName());
                pageVo.setSkuPic(skuInfo.getSkuDefaultImg());
                pageVo.setOriginalPrice(skuInfo.getPrice());
            }
        } catch (Exception e) {
            // 商品服务查不到就降级展示（没有名字/图片/原价），不能让整个秒杀页因为
            // 这一个非核心信息挂掉。
            log.warn("relationId={} skuId={} 查询商品信息失败,页面降级展示: {}", relationId, relation.getSkuId(), e.getMessage());
        }

        model.addAttribute("page", pageVo);
        return "seckill";
    }
}
