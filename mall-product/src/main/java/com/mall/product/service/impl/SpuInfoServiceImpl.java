package com.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.common.utils.R;
import com.mall.product.dao.*;
import com.mall.product.entity.*;
import com.mall.product.feign.SmsFeignService;
import com.mall.product.service.SpuInfoService;
import com.mall.product.vo.SpuSaveVo;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;


@Service("spuInfoService")
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoDao, SpuInfoEntity> implements SpuInfoService {

    @Autowired
    private SmsFeignService smsFeignService;

    @Autowired
    private SpuInfoDescDao spuInfoDescDao;

    @Autowired
    private SpuImagesDao spuImagesDao;

    @Autowired
    private ProductAttrValueDao productAttrValueDao;

    @Autowired
    private SkuInfoDao skuInfoDao;

    @Autowired
    private SkuImagesDao skuImagesDao;

    @Autowired
    private SkuSaleAttrValueDao skuSaleAttrValueDao;

    @Autowired
    private AttrDao attrDao;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<SpuInfoEntity> wrapper = new QueryWrapper<>();

        // å…³é”®å­—æ£€ç´¢ï¼ˆspuName æˆ– id ç²¾ç¡®ï¼‰
        Object keyObj = params.get("key");
        if (keyObj != null) {
            String key = String.valueOf(keyObj).trim();
            if (!key.isEmpty()) {
                wrapper.and(w -> w.eq("id", key).or().like("spu_name", key));
            }
        }

        // åˆ†ç±»IDï¼šåªä¼ ä¸€ä¸ªå€¼
        Object categoryIdObj = params.get("categoryId");
        if (categoryIdObj != null) {
            Long categoryId = parseLongSafe(String.valueOf(categoryIdObj));
            if (categoryId != null && categoryId > 0) {
                wrapper.eq("category_id", categoryId);
            }
        }

        // å“ç‰Œ
        Object brandObj = params.get("brandId");
        if (brandObj != null) {
            Long brandId = parseLongSafe(String.valueOf(brandObj));
            if (brandId != null && brandId > 0) wrapper.eq("brand_id", brandId);
        }

        // çŠ¶æ€ publish_status
        Object statusObj = params.get("status");
        if (statusObj != null) {
            Integer status = parseIntSafe(String.valueOf(statusObj));
            if (status != null) wrapper.eq("publish_status", status);
        }

        wrapper.orderByDesc("create_time");

        IPage<SpuInfoEntity> page = this.page(new Query<SpuInfoEntity>().getPage(params), wrapper);
        return new PageUtils(page);
    }

    private Long parseLongSafe(String s) {
        try { return Long.valueOf(s); } catch (Exception e) { return null; }
    }

    private Integer parseIntSafe(String s) {
        try { return Integer.valueOf(s); } catch (Exception e) { return null; }
    }

    @Override
    @Transactional
    public void saveSpuInfo(SpuSaveVo vo) {
        //1ã€ä¿å­˜spuåŸºæœ¬ä¿¡æ¯ pms_spu_info
        SpuInfoEntity spuInfoEntity = new SpuInfoEntity();
        spuInfoEntity.setSpuName(vo.getSpuName());
        spuInfoEntity.setSpuDescription(vo.getSpuDescription());
        spuInfoEntity.setCategoryId(vo.getCategoryId());
        spuInfoEntity.setBrandId(vo.getBrandId());
        spuInfoEntity.setWeight(vo.getWeight());
        spuInfoEntity.setPublishStatus(vo.getPublishStatus());
        spuInfoEntity.setCreateTime(new Date());
        spuInfoEntity.setUpdateTime(new Date());
        this.save(spuInfoEntity);
        Long spuId = spuInfoEntity.getId();

        //2ã€ä¿å­˜Spuçš„æè¿°å›¾ç‰‡ pms_spu_info_desc
        List<String> decript = vo.getDecript();
        if (!CollectionUtils.isEmpty(decript)) {
            SpuInfoDescEntity spuInfoDescEntity = new SpuInfoDescEntity();
            spuInfoDescEntity.setSpuId(spuId);
            spuInfoDescEntity.setDecript(String.join(",", decript));
            spuInfoDescDao.insert(spuInfoDescEntity);
        }

        //3ã€ä¿å­˜spuçš„å›¾ç‰‡é›† pms_spu_images
        List<String> images = vo.getImages();
        if (!CollectionUtils.isEmpty(images)) {
            List<SpuImagesEntity> spuImagesEntities = new ArrayList<>();
            for (int i = 0; i < images.size(); i++) {
                if (StringUtils.isNotBlank(images.get(i))) {
                    SpuImagesEntity spuImagesEntity = new SpuImagesEntity();
                    spuImagesEntity.setSpuId(spuId);
                    spuImagesEntity.setImgUrl(images.get(i));
                    spuImagesEntity.setImgSort(i);
                    spuImagesEntity.setDefaultImg(i == 0 ? 1 : 0);
                    spuImagesEntities.add(spuImagesEntity);
                }
            }
            // ä¿å­˜SPUå›¾ç‰‡
            if (!CollectionUtils.isEmpty(spuImagesEntities)) {
                for (SpuImagesEntity entity : spuImagesEntities) {
                    spuImagesDao.insert(entity);
                }
            }
        }

        //4ã€ä¿å­˜spuçš„è§„æ ¼å‚æ•°;pms_product_attr_value
        List<SpuSaveVo.BaseAttrs> baseAttrs = vo.getBaseAttrs();
        if (!CollectionUtils.isEmpty(baseAttrs)) {
            List<ProductAttrValueEntity> productAttrValueEntities = new ArrayList<>();
            for (int i = 0; i < baseAttrs.size(); i++) {
                SpuSaveVo.BaseAttrs baseAttr = baseAttrs.get(i);
                ProductAttrValueEntity productAttrValueEntity = new ProductAttrValueEntity();
                productAttrValueEntity.setSpuId(spuId);
                productAttrValueEntity.setAttrId(baseAttr.getAttrId());
                // æŸ¥è¯¢å±žæ€§åç§°
                AttrEntity attrEntity = attrDao.selectById(baseAttr.getAttrId());
                String attrName = attrEntity != null ? attrEntity.getAttrName() : "";
                productAttrValueEntity.setAttrName(attrName);
                productAttrValueEntity.setAttrValue(baseAttr.getAttrValues());
                productAttrValueEntity.setAttrSort(i);
                productAttrValueEntity.setQuickShow(baseAttr.getShowDesc());
                productAttrValueEntities.add(productAttrValueEntity);
            }
            // ä¿å­˜SPUè§„æ ¼å‚æ•°
            for (ProductAttrValueEntity entity : productAttrValueEntities) {
                productAttrValueDao.insert(entity);
            }
        }

        //5ã€ä¿å­˜spuçš„ç§¯åˆ†ä¿¡æ¯; gulimall_sms->sms_spu_bounds
        SpuSaveVo.Bounds bounds = vo.getBounds();
        if (bounds != null) {
            Map<String, String> boundsParams = new HashMap<>();
            boundsParams.put("spuId", String.valueOf(spuId));
            boundsParams.put("buy_bounds", String.valueOf(bounds.getBuyBounds()));
            boundsParams.put("grow_bounds", String.valueOf(bounds.getGrowBounds()));
            R boundsResult = smsFeignService.saveSpuBounds(boundsParams);
            if (boundsResult == null || boundsResult.getCode() != 0) {
                throw new RuntimeException("ä¿å­˜SPUç§¯åˆ†ä¿¡æ¯å¤±è´¥: " + (boundsResult != null ? boundsResult.getMsg() : "è¿œç¨‹æœåŠ¡è°ƒç”¨å¤±è´¥"));
            }
        }

        //5ã€ä¿å­˜å½“å‰spuå¯¹åº”çš„æ‰€æœ‰skuä¿¡æ¯;
        List<SpuSaveVo.Skus> skus = vo.getSkus();
        if (skus != null && !skus.isEmpty()) {
            for (SpuSaveVo.Skus sku : skus) {
                //5.1)ã€skuçš„åŸºæœ¬ä¿¡æ¯; pms_sku_info
                SkuInfoEntity skuInfoEntity = new SkuInfoEntity();
                skuInfoEntity.setSpuId(spuId);
                skuInfoEntity.setSkuName(sku.getSkuName());
                skuInfoEntity.setSkuDesc(sku.getSkuSubtitle());
                skuInfoEntity.setCategoryId(vo.getCategoryId());
                skuInfoEntity.setBrandId(vo.getBrandId());
                skuInfoEntity.setSkuTitle(sku.getSkuTitle());
                skuInfoEntity.setSkuSubtitle(sku.getSkuSubtitle());
                skuInfoEntity.setPrice(sku.getPrice());
                skuInfoDao.insert(skuInfoEntity);
                Long skuId = skuInfoEntity.getSkuId();
                sku.setSkuId(skuId);

                //5.2)ã€skuçš„å›¾ç‰‡ä¿¡æ¯; pms_sku_images
                List<SpuSaveVo.Images> skuImages = sku.getImages();
                if (skuImages != null && !skuImages.isEmpty()) {
                    List<SkuImagesEntity> skuImagesEntities = new ArrayList<>();
                    for (int i = 0; i < skuImages.size(); i++) {
                        SpuSaveVo.Images skuImage = skuImages.get(i);
                        if (StringUtils.isNotBlank(skuImage.getImgUrl())) {
                            SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                            skuImagesEntity.setSkuId(skuId);
                            skuImagesEntity.setImgUrl(skuImage.getImgUrl());
                            skuImagesEntity.setImgSort(i);
                            skuImagesEntity.setDefaultImg(skuImage.getDefaultImg());
                            skuImagesEntities.add(skuImagesEntity);
                        }
                    }
                    if (!skuImagesEntities.isEmpty()) {
                        // ä¿å­˜SKUå›¾ç‰‡
                        for (SkuImagesEntity entity : skuImagesEntities) {
                            skuImagesDao.insert(entity);
                        }
                    }
                }

                //5.3)ã€skuçš„é”€å”®å±žæ€§ä¿¡æ¯:pms_sku_sale_attr_value
                List<SpuSaveVo.Attr> skuAttrs = sku.getAttr();
                if (skuAttrs != null && !skuAttrs.isEmpty()) {
                    List<SkuSaleAttrValueEntity> skuSaleAttrValueEntities = new ArrayList<>();
                    for (int i = 0; i < skuAttrs.size(); i++) {
                        SpuSaveVo.Attr skuAttr = skuAttrs.get(i);
                        SkuSaleAttrValueEntity skuSaleAttrValueEntity = new SkuSaleAttrValueEntity();
                        skuSaleAttrValueEntity.setSkuId(skuId);
                        skuSaleAttrValueEntity.setAttrId(skuAttr.getAttrId());
                        skuSaleAttrValueEntity.setAttrName(skuAttr.getAttrName());
                        skuSaleAttrValueEntity.setAttrValue(skuAttr.getAttrValue());
                        skuSaleAttrValueEntity.setAttrSort(i);
                        skuSaleAttrValueEntities.add(skuSaleAttrValueEntity);
                    }
                    // ä¿å­˜SKUé”€å”®å±žæ€§
                    for (SkuSaleAttrValueEntity entity : skuSaleAttrValueEntities) {
                        skuSaleAttrValueDao.insert(entity);
                    }
                }

                //5.4)ã€skuçš„ä¼˜æƒ ã€æ»¡å‡ç­‰ä¿¡æ¯: gulimall_sms->sms_sku_ladder\sms_sku_full_reduction
                // ä¿å­˜SKUé˜¶æ¢¯ä»·æ ¼
                if (sku.getFullCount() != null && sku.getFullCount() > 0 && sku.getDiscount() != null) {
                    Map<String, String> ladderParams = new HashMap<>();
                    ladderParams.put("skuId", String.valueOf(sku.getSkuId())); // éœ€è¦å…ˆä¿å­˜SKUèŽ·å–ID
                    ladderParams.put("full_count", String.valueOf(sku.getFullCount()));
                    ladderParams.put("discount", String.valueOf(sku.getDiscount()));
                    ladderParams.put("price", String.valueOf(sku.getPrice()));
                    ladderParams.put("add_other", String.valueOf(sku.getCountStatus()));
                    R ladderResult = smsFeignService.saveSkuLadder(ladderParams);
                    if (ladderResult == null || ladderResult.getCode() != 0) {
                        throw new RuntimeException("ä¿å­˜SKUé˜¶æ¢¯ä»·æ ¼å¤±è´¥: " + (ladderResult != null ? ladderResult.getMsg() : "è¿œç¨‹æœåŠ¡è°ƒç”¨å¤±è´¥"));
                    }
                }

                // ä¿å­˜SKUæ»¡å‡ä¿¡æ¯
                if (sku.getFullPrice() != null && sku.getFullPrice().compareTo(java.math.BigDecimal.ZERO) > 0
                        && sku.getReducePrice() != null && sku.getReducePrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                    Map<String, String> fullReductionParams = new HashMap<>();
                    fullReductionParams.put("skuId", String.valueOf(sku.getSkuId())); // éœ€è¦å…ˆä¿å­˜SKUèŽ·å–ID
                    fullReductionParams.put("full_price", String.valueOf(sku.getFullPrice()));
                    fullReductionParams.put("reduce_price", String.valueOf(sku.getReducePrice()));
                    fullReductionParams.put("add_other", String.valueOf(sku.getPriceStatus()));
                    R fullReductionResult = smsFeignService.saveSkuFullReduction(fullReductionParams);
                    if (fullReductionResult == null || fullReductionResult.getCode() != 0) {
                        throw new RuntimeException("ä¿å­˜SKUæ»¡å‡ä¿¡æ¯å¤±è´¥: " + (fullReductionResult != null ? fullReductionResult.getMsg() : "è¿œç¨‹æœåŠ¡è°ƒç”¨å¤±è´¥"));
                    }
                }
                
                // ä¿å­˜SKUä¼šå‘˜ä»·æ ¼
                List<SpuSaveVo.MemberPrice> memberPrices = sku.getMemberPrice();
                if (memberPrices != null && !memberPrices.isEmpty()) {
                    for (SpuSaveVo.MemberPrice memberPrice : memberPrices) {
                        if (memberPrice.getPrice() != null && memberPrice.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                            Map<String, String> memberPriceParams = new HashMap<>();
                            memberPriceParams.put("skuId", String.valueOf(sku.getSkuId()));
                            memberPriceParams.put("memberLevelId", String.valueOf(memberPrice.getId()));
                            memberPriceParams.put("memberLevelName", memberPrice.getName());
                            memberPriceParams.put("memberPrice", String.valueOf(memberPrice.getPrice()));
                            memberPriceParams.put("addOther", "1"); // é»˜è®¤å¯å åŠ 
                            R memberPriceResult = smsFeignService.saveSkuMemberPrice(memberPriceParams);
                            if (memberPriceResult == null || memberPriceResult.getCode() != 0) {
                                throw new RuntimeException("ä¿å­˜SKUä¼šå‘˜ä»·æ ¼å¤±è´¥: " + (memberPriceResult != null ? memberPriceResult.getMsg() : "è¿œç¨‹æœåŠ¡è°ƒç”¨å¤±è´¥"));
                            }
                        }
                    }
                }
            }
        }
    }

}
