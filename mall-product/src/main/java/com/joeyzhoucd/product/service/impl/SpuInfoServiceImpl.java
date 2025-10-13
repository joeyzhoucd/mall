package com.joeyzhoucd.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.Query;
import com.joeyzhoucd.common.utils.R;
import com.joeyzhoucd.product.dao.*;
import com.joeyzhoucd.product.entity.*;
import com.joeyzhoucd.product.feign.SmsFeignService;
import com.joeyzhoucd.product.service.SpuInfoService;
import com.joeyzhoucd.product.vo.SpuSaveVo;
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

        // 关键字检索（spuName 或 id 精确）
        Object keyObj = params.get("key");
        if (keyObj != null) {
            String key = String.valueOf(keyObj).trim();
            if (!key.isEmpty()) {
                wrapper.and(w -> w.eq("id", key).or().like("spu_name", key));
            }
        }

        // 分类ID：只传一个值
        Object categoryIdObj = params.get("categoryId");
        if (categoryIdObj != null) {
            Long categoryId = parseLongSafe(String.valueOf(categoryIdObj));
            if (categoryId != null && categoryId > 0) {
                wrapper.eq("category_id", categoryId);
            }
        }

        // 品牌
        Object brandObj = params.get("brandId");
        if (brandObj != null) {
            Long brandId = parseLongSafe(String.valueOf(brandObj));
            if (brandId != null && brandId > 0) wrapper.eq("brand_id", brandId);
        }

        // 状态 publish_status
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
        //1、保存spu基本信息 pms_spu_info
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

        //2、保存Spu的描述图片 pms_spu_info_desc
        List<String> decript = vo.getDecript();
        if (!CollectionUtils.isEmpty(decript)) {
            SpuInfoDescEntity spuInfoDescEntity = new SpuInfoDescEntity();
            spuInfoDescEntity.setSpuId(spuId);
            spuInfoDescEntity.setDecript(String.join(",", decript));
            spuInfoDescDao.insert(spuInfoDescEntity);
        }

        //3、保存spu的图片集 pms_spu_images
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
            // 保存SPU图片
            if (!CollectionUtils.isEmpty(spuImagesEntities)) {
                for (SpuImagesEntity entity : spuImagesEntities) {
                    spuImagesDao.insert(entity);
                }
            }
        }

        //4、保存spu的规格参数;pms_product_attr_value
        List<SpuSaveVo.BaseAttrs> baseAttrs = vo.getBaseAttrs();
        if (!CollectionUtils.isEmpty(baseAttrs)) {
            List<ProductAttrValueEntity> productAttrValueEntities = new ArrayList<>();
            for (int i = 0; i < baseAttrs.size(); i++) {
                SpuSaveVo.BaseAttrs baseAttr = baseAttrs.get(i);
                ProductAttrValueEntity productAttrValueEntity = new ProductAttrValueEntity();
                productAttrValueEntity.setSpuId(spuId);
                productAttrValueEntity.setAttrId(baseAttr.getAttrId());
                // 查询属性名称
                AttrEntity attrEntity = attrDao.selectById(baseAttr.getAttrId());
                String attrName = attrEntity != null ? attrEntity.getAttrName() : "";
                productAttrValueEntity.setAttrName(attrName);
                productAttrValueEntity.setAttrValue(baseAttr.getAttrValues());
                productAttrValueEntity.setAttrSort(i);
                productAttrValueEntity.setQuickShow(baseAttr.getShowDesc());
                productAttrValueEntities.add(productAttrValueEntity);
            }
            // 保存SPU规格参数
            for (ProductAttrValueEntity entity : productAttrValueEntities) {
                productAttrValueDao.insert(entity);
            }
        }

        //5、保存spu的积分信息; gulimall_sms->sms_spu_bounds
        SpuSaveVo.Bounds bounds = vo.getBounds();
        if (bounds != null) {
            Map<String, String> boundsParams = new HashMap<>();
            boundsParams.put("spuId", String.valueOf(spuId));
            boundsParams.put("buy_bounds", String.valueOf(bounds.getBuyBounds()));
            boundsParams.put("grow_bounds", String.valueOf(bounds.getGrowBounds()));
            R boundsResult = smsFeignService.saveSpuBounds(boundsParams);
            if (boundsResult == null || boundsResult.getCode() != 0) {
                throw new RuntimeException("保存SPU积分信息失败: " + (boundsResult != null ? boundsResult.getMsg() : "远程服务调用失败"));
            }
        }

        //5、保存当前spu对应的所有sku信息;
        List<SpuSaveVo.Skus> skus = vo.getSkus();
        if (skus != null && !skus.isEmpty()) {
            for (SpuSaveVo.Skus sku : skus) {
                //5.1)、sku的基本信息; pms_sku_info
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

                //5.2)、sku的图片信息; pms_sku_images
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
                        // 保存SKU图片
                        for (SkuImagesEntity entity : skuImagesEntities) {
                            skuImagesDao.insert(entity);
                        }
                    }
                }

                //5.3)、sku的销售属性信息:pms_sku_sale_attr_value
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
                    // 保存SKU销售属性
                    for (SkuSaleAttrValueEntity entity : skuSaleAttrValueEntities) {
                        skuSaleAttrValueDao.insert(entity);
                    }
                }

                //5.4)、sku的优惠、满减等信息: gulimall_sms->sms_sku_ladder\sms_sku_full_reduction
                // 保存SKU阶梯价格
                if (sku.getFullCount() != null && sku.getFullCount() > 0 && sku.getDiscount() != null) {
                    Map<String, String> ladderParams = new HashMap<>();
                    ladderParams.put("skuId", String.valueOf(sku.getSkuId())); // 需要先保存SKU获取ID
                    ladderParams.put("full_count", String.valueOf(sku.getFullCount()));
                    ladderParams.put("discount", String.valueOf(sku.getDiscount()));
                    ladderParams.put("price", String.valueOf(sku.getPrice()));
                    ladderParams.put("add_other", String.valueOf(sku.getCountStatus()));
                    R ladderResult = smsFeignService.saveSkuLadder(ladderParams);
                    if (ladderResult == null || ladderResult.getCode() != 0) {
                        throw new RuntimeException("保存SKU阶梯价格失败: " + (ladderResult != null ? ladderResult.getMsg() : "远程服务调用失败"));
                    }
                }

                // 保存SKU满减信息
                if (sku.getFullPrice() != null && sku.getFullPrice().compareTo(java.math.BigDecimal.ZERO) > 0
                        && sku.getReducePrice() != null && sku.getReducePrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                    Map<String, String> fullReductionParams = new HashMap<>();
                    fullReductionParams.put("skuId", String.valueOf(sku.getSkuId())); // 需要先保存SKU获取ID
                    fullReductionParams.put("full_price", String.valueOf(sku.getFullPrice()));
                    fullReductionParams.put("reduce_price", String.valueOf(sku.getReducePrice()));
                    fullReductionParams.put("add_other", String.valueOf(sku.getPriceStatus()));
                    R fullReductionResult = smsFeignService.saveSkuFullReduction(fullReductionParams);
                    if (fullReductionResult == null || fullReductionResult.getCode() != 0) {
                        throw new RuntimeException("保存SKU满减信息失败: " + (fullReductionResult != null ? fullReductionResult.getMsg() : "远程服务调用失败"));
                    }
                }
                
                // 保存SKU会员价格
                List<SpuSaveVo.MemberPrice> memberPrices = sku.getMemberPrice();
                if (memberPrices != null && !memberPrices.isEmpty()) {
                    for (SpuSaveVo.MemberPrice memberPrice : memberPrices) {
                        if (memberPrice.getPrice() != null && memberPrice.getPrice().compareTo(java.math.BigDecimal.ZERO) > 0) {
                            Map<String, String> memberPriceParams = new HashMap<>();
                            memberPriceParams.put("skuId", String.valueOf(sku.getSkuId()));
                            memberPriceParams.put("memberLevelId", String.valueOf(memberPrice.getId()));
                            memberPriceParams.put("memberLevelName", memberPrice.getName());
                            memberPriceParams.put("memberPrice", String.valueOf(memberPrice.getPrice()));
                            memberPriceParams.put("addOther", "1"); // 默认可叠加
                            R memberPriceResult = smsFeignService.saveSkuMemberPrice(memberPriceParams);
                            if (memberPriceResult == null || memberPriceResult.getCode() != 0) {
                                throw new RuntimeException("保存SKU会员价格失败: " + (memberPriceResult != null ? memberPriceResult.getMsg() : "远程服务调用失败"));
                            }
                        }
                    }
                }
            }
        }
    }

}