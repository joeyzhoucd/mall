package com.mall.coupon.controller;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Duration;

/**
 * 秒杀抢购页（Thymeleaf 渲染），走 seckill.mall.com 域名。
 */
@Controller
public class SeckillWebController {

    private static final Logger log = LoggerFactory.getLogger(SeckillWebController.class);

    /**
     * 页面数据缓存 TTL：这个页面是"很多人看、内容短时间内几乎不变"的典型场景——
     * 抢购前后可能几十万人反复刷新页面，真正点"抢购"的只是其中一小撮。如果每次
     * 打开页面都去查一次数据库、调一次 mall-product，围观流量会直接把这两个下游
     * 打垮，而且这个流量跟"抢购"这个核心写路径完全没关系，没必要让它们互相影响。
     * 缓存几秒钟，页面上的库存/已售数字会有短暂的不精确，但反正真实库存的准头
     * 由 Redis 原子网关兜底，这个页面上的数字本来就只是"仅供参考"。
     */
    private static final Duration PAGE_CACHE_TTL = Duration.ofSeconds(5);

    @Autowired
    private SeckillSkuRelationService seckillSkuRelationService;

    @Autowired
    private ProductFeignService productFeignService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping("/seckill.html")
    public String seckillPage(@RequestParam("relationId") Long relationId, Model model) {
        SeckillPageVo pageVo = loadFromCache(relationId);
        if (pageVo == null) {
            pageVo = buildPageVo(relationId);
            if (pageVo == null) {
                return "seckillNotFound";
            }
            saveToCache(relationId, pageVo);
        }

        model.addAttribute("page", pageVo);
        return "seckill";
    }

    private SeckillPageVo buildPageVo(Long relationId) {
        SeckillSkuRelationEntity relation = seckillSkuRelationService.getById(relationId);
        if (relation == null) {
            return null;
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
        return pageVo;
    }

    private String pageCacheKey(Long relationId) {
        return "seckill:page:" + relationId;
    }

    private SeckillPageVo loadFromCache(Long relationId) {
        try {
            String json = redisTemplate.opsForValue().get(pageCacheKey(relationId));
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, SeckillPageVo.class);
        } catch (Exception e) {
            // 缓存读失败（比如反序列化出错）不影响主流程，退回去数据库/商品服务查一遍。
            log.warn("relationId={} 秒杀页面缓存读取失败,回源查询: {}", relationId, e.getMessage());
            return null;
        }
    }

    private void saveToCache(Long relationId, SeckillPageVo pageVo) {
        try {
            String json = objectMapper.writeValueAsString(pageVo);
            redisTemplate.opsForValue().set(pageCacheKey(relationId), json, PAGE_CACHE_TTL);
        } catch (Exception e) {
            log.warn("relationId={} 秒杀页面缓存写入失败,不影响本次渲染: {}", relationId, e.getMessage());
        }
    }
}
