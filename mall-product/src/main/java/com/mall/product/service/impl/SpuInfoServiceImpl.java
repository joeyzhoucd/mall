package com.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.common.utils.R;
import com.mall.product.dao.*;
import com.mall.product.entity.*;
import com.mall.product.feign.SearchFeignService;
import com.mall.product.feign.SmsFeignService;
import com.mall.product.service.*;
import com.mall.product.vo.SkuEsModelVo;
import com.mall.product.vo.SpuSaveVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * SPU information service implementation
 */
@Service("spuInfoService")
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoDao, SpuInfoEntity> implements SpuInfoService {

    @Autowired
    private SmsFeignService smsFeignService;

    @Autowired
    private SpuInfoDescDao spuInfoDescDao;

    @Autowired
    private SpuImagesService spuImagesService;

    @Autowired
    private ProductAttrValueService productAttrValueService;

    @Autowired
    private ProductAttrValueDao productAttrValueDao;

    @Autowired
    private SkuInfoDao skuInfoDao;

    @Autowired
    private SkuImagesService skuImagesService;

    @Autowired
    private SkuImagesDao skuImagesDao;

    @Autowired
    private SkuSaleAttrValueService skuSaleAttrValueService;

    @Autowired
    private SkuSaleAttrValueDao skuSaleAttrValueDao;

    @Autowired
    private AttrDao attrDao;

    @Autowired
    private BrandDao brandDao;

    @Autowired
    private CategoryDao categoryDao;

    @Autowired
    private SearchFeignService searchFeignService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<SpuInfoEntity> wrapper = new QueryWrapper<>();

        // Search by key: spuName or id
        Object keyObj = params.get("key");
        if (keyObj != null) {
            String key = String.valueOf(keyObj).trim();
            if (!key.isEmpty()) {
                wrapper.and(w -> w.eq("id", key).or().like("spu_name", key));
            }
        }

        // Filter by category ID
        Object categoryIdObj = params.get("categoryId");
        if (categoryIdObj != null) {
            Long categoryId = parseLongSafe(String.valueOf(categoryIdObj));
            if (categoryId != null && categoryId > 0) {
                wrapper.eq("category_id", categoryId);
            }
        }

        // Filter by brand ID
        Object brandObj = params.get("brandId");
        if (brandObj != null) {
            Long brandId = parseLongSafe(String.valueOf(brandObj));
            if (brandId != null && brandId > 0) {
                wrapper.eq("brand_id", brandId);
            }
        }

        // Filter by publish status
        Object statusObj = params.get("status");
        if (statusObj != null) {
            Integer status = parseIntSafe(String.valueOf(statusObj));
            if (status != null) {
                wrapper.eq("publish_status", status);
            }
        }

        wrapper.orderByDesc("create_time");

        IPage<SpuInfoEntity> page = this.page(new Query<SpuInfoEntity>().getPage(params), wrapper);
        return new PageUtils(page);
    }

    private Long parseLongSafe(String s) {
        try {
            return Long.valueOf(s);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseIntSafe(String s) {
        try {
            return Integer.valueOf(s);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSpuInfo(SpuSaveVo vo) {
        // 1. Save SPU basic information
        SpuInfoEntity spuInfoEntity = buildSpuInfoEntity(vo);
        this.save(spuInfoEntity);
        Long spuId = spuInfoEntity.getId();

        // 2. Save SPU description images
        saveSpuInfoDesc(spuId, vo.getDecript());

        // 3. Save SPU images collection
        saveSpuImages(spuId, vo.getImages());

        // 4. Save SPU specification parameters
        saveSpuBaseAttrs(spuId, vo.getBaseAttrs());

        // 5. Save SPU points information
        saveSpuBounds(spuId, vo.getBounds());

        // 6. Save all SKU information for current SPU
        saveSkus(spuId, vo.getCategoryId(), vo.getBrandId(), vo.getSkus());
    }

    /**
     * Build SPU info entity from VO
     */
    private SpuInfoEntity buildSpuInfoEntity(SpuSaveVo vo) {
        SpuInfoEntity spuInfoEntity = new SpuInfoEntity();
        spuInfoEntity.setSpuName(vo.getSpuName());
        spuInfoEntity.setSpuDescription(vo.getSpuDescription());
        spuInfoEntity.setCategoryId(vo.getCategoryId());
        spuInfoEntity.setBrandId(vo.getBrandId());
        spuInfoEntity.setWeight(vo.getWeight());
        spuInfoEntity.setPublishStatus(vo.getPublishStatus());
        Date now = new Date();
        spuInfoEntity.setCreateTime(now);
        spuInfoEntity.setUpdateTime(now);
        return spuInfoEntity;
    }

    /**
     * Save SPU description
     */
    private void saveSpuInfoDesc(Long spuId, List<String> decript) {
        if (!CollectionUtils.isEmpty(decript)) {
            SpuInfoDescEntity spuInfoDescEntity = new SpuInfoDescEntity();
            spuInfoDescEntity.setSpuId(spuId);
            spuInfoDescEntity.setDecript(String.join(",", decript));
            spuInfoDescDao.insert(spuInfoDescEntity);
        }
    }

    /**
     * Save SPU images in batch
     */
    private void saveSpuImages(Long spuId, List<String> images) {
        if (CollectionUtils.isEmpty(images)) {
            return;
        }

        List<SpuImagesEntity> spuImagesEntities = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            String imageUrl = images.get(i);
            if (StringUtils.isNotBlank(imageUrl)) {
                SpuImagesEntity spuImagesEntity = new SpuImagesEntity();
                spuImagesEntity.setSpuId(spuId);
                spuImagesEntity.setImgUrl(imageUrl);
                spuImagesEntity.setImgSort(i);
                spuImagesEntity.setDefaultImg(i == 0 ? 1 : 0);
                spuImagesEntities.add(spuImagesEntity);
            }
        }

        // Batch insert SPU images
        if (!CollectionUtils.isEmpty(spuImagesEntities)) {
            spuImagesService.saveBatch(spuImagesEntities);
        }
    }

    /**
     * Save SPU base attributes in batch with optimized batch query
     */
    private void saveSpuBaseAttrs(Long spuId, List<SpuSaveVo.BaseAttrs> baseAttrs) {
        if (CollectionUtils.isEmpty(baseAttrs)) {
            return;
        }

        // Collect all attribute IDs for batch query
        List<Long> attrIds = baseAttrs.stream()
                .map(SpuSaveVo.BaseAttrs::getAttrId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // Batch query all attributes
        Map<Long, String> attrNameMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(attrIds)) {
            List<AttrEntity> attrEntities = attrDao.selectBatchIds(attrIds);
            if (!CollectionUtils.isEmpty(attrEntities)) {
                attrNameMap = attrEntities.stream()
                        .collect(Collectors.toMap(AttrEntity::getAttrId, AttrEntity::getAttrName, (k1, k2) -> k1));
            }
        }

        // Build product attribute value entities
        List<ProductAttrValueEntity> productAttrValueEntities = new ArrayList<>();
        for (int i = 0; i < baseAttrs.size(); i++) {
            SpuSaveVo.BaseAttrs baseAttr = baseAttrs.get(i);
            ProductAttrValueEntity productAttrValueEntity = new ProductAttrValueEntity();
            productAttrValueEntity.setSpuId(spuId);
            productAttrValueEntity.setAttrId(baseAttr.getAttrId());
            productAttrValueEntity.setAttrName(attrNameMap.getOrDefault(baseAttr.getAttrId(), ""));
            productAttrValueEntity.setAttrValue(baseAttr.getAttrValues());
            productAttrValueEntity.setAttrSort(i);
            productAttrValueEntity.setQuickShow(baseAttr.getShowDesc());
            productAttrValueEntities.add(productAttrValueEntity);
        }

        // Batch insert SPU specification parameters
        if (!CollectionUtils.isEmpty(productAttrValueEntities)) {
            productAttrValueService.saveBatch(productAttrValueEntities);
        }
    }

    /**
     * Save SPU bounds information
     */
    private void saveSpuBounds(Long spuId, SpuSaveVo.Bounds bounds) {
        if (bounds == null) {
            return;
        }

        Map<String, String> boundsParams = new HashMap<>();
        boundsParams.put("spuId", String.valueOf(spuId));
        boundsParams.put("buy_bounds", String.valueOf(bounds.getBuyBounds()));
        boundsParams.put("grow_bounds", String.valueOf(bounds.getGrowBounds()));

        R boundsResult = smsFeignService.saveSpuBounds(boundsParams);
        validateRemoteCallResult(boundsResult, "Save SPU bounds failed");
    }

    /**
     * Save all SKUs for SPU
     */
    private void saveSkus(Long spuId, Long categoryId, Long brandId, List<SpuSaveVo.Skus> skus) {
        if (CollectionUtils.isEmpty(skus)) {
            return;
        }

        for (SpuSaveVo.Skus sku : skus) {
            // 5.1. Save SKU basic information
            Long skuId = saveSkuInfo(spuId, categoryId, brandId, sku);
            sku.setSkuId(skuId);

            // 5.2. Save SKU images
            saveSkuImages(skuId, sku.getImages());

            // 5.3. Save SKU sale attributes
            saveSkuSaleAttrs(skuId, sku.getAttr());

            // 5.4. Save SKU promotion information (ladder price, full reduction, member price)
            saveSkuPromotion(sku);
        }
    }

    /**
     * Save SKU basic information
     */
    private Long saveSkuInfo(Long spuId, Long categoryId, Long brandId, SpuSaveVo.Skus sku) {
        SkuInfoEntity skuInfoEntity = new SkuInfoEntity();
        skuInfoEntity.setSpuId(spuId);
        skuInfoEntity.setSkuName(sku.getSkuName());
        skuInfoEntity.setSkuDesc(sku.getSkuSubtitle());
        skuInfoEntity.setCategoryId(categoryId);
        skuInfoEntity.setBrandId(brandId);
        skuInfoEntity.setSkuTitle(sku.getSkuTitle());
        skuInfoEntity.setSkuSubtitle(sku.getSkuSubtitle());
        skuInfoEntity.setPrice(sku.getPrice());
        skuInfoDao.insert(skuInfoEntity);
        return skuInfoEntity.getSkuId();
    }

    /**
     * Save SKU images in batch
     */
    private void saveSkuImages(Long skuId, List<SpuSaveVo.Images> skuImages) {
        if (CollectionUtils.isEmpty(skuImages)) {
            return;
        }

        List<SkuImagesEntity> skuImagesEntities = new ArrayList<>();
        for (int i = 0; i < skuImages.size(); i++) {
            SpuSaveVo.Images skuImage = skuImages.get(i);
            if (skuImage != null && StringUtils.isNotBlank(skuImage.getImgUrl())) {
                SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                skuImagesEntity.setSkuId(skuId);
                skuImagesEntity.setImgUrl(skuImage.getImgUrl());
                skuImagesEntity.setImgSort(i);
                skuImagesEntity.setDefaultImg(skuImage.getDefaultImg());
                skuImagesEntities.add(skuImagesEntity);
            }
        }

        // Batch insert SKU images
        if (!CollectionUtils.isEmpty(skuImagesEntities)) {
            skuImagesService.saveBatch(skuImagesEntities);
        }
    }

    /**
     * Save SKU sale attributes in batch
     */
    private void saveSkuSaleAttrs(Long skuId, List<SpuSaveVo.Attr> skuAttrs) {
        if (CollectionUtils.isEmpty(skuAttrs)) {
            return;
        }

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

        // Batch insert SKU sale attributes
        if (!CollectionUtils.isEmpty(skuSaleAttrValueEntities)) {
            skuSaleAttrValueService.saveBatch(skuSaleAttrValueEntities);
        }
    }

    /**
     * Save SKU promotion information (ladder price, full reduction, member price)
     */
    private void saveSkuPromotion(SpuSaveVo.Skus sku) {
        // Save SKU ladder price
        saveSkuLadder(sku);

        // Save SKU full reduction
        saveSkuFullReduction(sku);

        // Save SKU member price
        saveSkuMemberPrice(sku);
    }

    /**
     * Save SKU ladder price
     */
    private void saveSkuLadder(SpuSaveVo.Skus sku) {
        if (sku.getFullCount() == null || sku.getFullCount() <= 0 || sku.getDiscount() == null) {
            return;
        }

        Map<String, String> ladderParams = new HashMap<>();
        ladderParams.put("skuId", String.valueOf(sku.getSkuId()));
        ladderParams.put("full_count", String.valueOf(sku.getFullCount()));
        ladderParams.put("discount", String.valueOf(sku.getDiscount()));
        ladderParams.put("price", String.valueOf(sku.getPrice()));
        ladderParams.put("add_other", String.valueOf(sku.getCountStatus()));

        R ladderResult = smsFeignService.saveSkuLadder(ladderParams);
        validateRemoteCallResult(ladderResult, "Save SKU ladder price failed");
    }

    /**
     * Save SKU full reduction
     */
    private void saveSkuFullReduction(SpuSaveVo.Skus sku) {
        if (sku.getFullPrice() == null || sku.getFullPrice().compareTo(BigDecimal.ZERO) <= 0
                || sku.getReducePrice() == null || sku.getReducePrice().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        Map<String, String> fullReductionParams = new HashMap<>();
        fullReductionParams.put("skuId", String.valueOf(sku.getSkuId()));
        fullReductionParams.put("full_price", String.valueOf(sku.getFullPrice()));
        fullReductionParams.put("reduce_price", String.valueOf(sku.getReducePrice()));
        fullReductionParams.put("add_other", String.valueOf(sku.getPriceStatus()));

        R fullReductionResult = smsFeignService.saveSkuFullReduction(fullReductionParams);
        validateRemoteCallResult(fullReductionResult, "Save SKU full reduction failed");
    }

    /**
     * Save SKU member price
     */
    private void saveSkuMemberPrice(SpuSaveVo.Skus sku) {
        List<SpuSaveVo.MemberPrice> memberPrices = sku.getMemberPrice();
        if (CollectionUtils.isEmpty(memberPrices)) {
            return;
        }

        for (SpuSaveVo.MemberPrice memberPrice : memberPrices) {
            if (memberPrice.getPrice() == null || memberPrice.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            Map<String, String> memberPriceParams = new HashMap<>();
            memberPriceParams.put("skuId", String.valueOf(sku.getSkuId()));
            memberPriceParams.put("memberLevelId", String.valueOf(memberPrice.getId()));
            memberPriceParams.put("memberLevelName", memberPrice.getName());
            memberPriceParams.put("memberPrice", String.valueOf(memberPrice.getPrice()));
            memberPriceParams.put("addOther", "1");

            R memberPriceResult = smsFeignService.saveSkuMemberPrice(memberPriceParams);
            validateRemoteCallResult(memberPriceResult, "Save SKU member price failed");
        }
    }

    /**
     * Validate remote service call result
     */
    private void validateRemoteCallResult(R result, String errorMessage) {
        if (result == null) {
            throw new RuntimeException(errorMessage + ": Network exception, save failed");
        }
        if (result.getCode() != 0) {
            throw new RuntimeException(errorMessage + ": " + result.getMsg());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void upSpu(Long spuId) {
        // 1. Query SPU information
        SpuInfoEntity spuInfo = this.getById(spuId);
        if (spuInfo == null) {
            throw new RuntimeException("SPU not found with id: " + spuId);
        }

        // 2. Query brand information
        BrandEntity brand = brandDao.selectById(spuInfo.getBrandId());

        // 3. Query category information
        CategoryEntity category = categoryDao.selectById(spuInfo.getCategoryId());

        // 4. Query all SKU information
        List<SkuInfoEntity> skuInfoList = skuInfoDao.selectList(
                new QueryWrapper<SkuInfoEntity>().eq("spu_id", spuId)
        );

        if (CollectionUtils.isEmpty(skuInfoList)) {
            throw new RuntimeException("No SKU found for SPU id: " + spuId);
        }

        List<Long> skuIds = skuInfoList.stream()
                .map(SkuInfoEntity::getSkuId)
                .collect(Collectors.toList());

        // 5. Query SKU images
        List<SkuImagesEntity> skuImagesList = skuImagesDao.selectList(
                new QueryWrapper<SkuImagesEntity>().in("sku_id", skuIds)
        );
        Map<Long, List<String>> skuImagesMap = skuImagesList.stream()
                .collect(Collectors.groupingBy(
                        SkuImagesEntity::getSkuId,
                        Collectors.mapping(SkuImagesEntity::getImgUrl, Collectors.toList())
                ));

        // 6. Query sale attributes
        List<SkuSaleAttrValueEntity> saleAttrs = skuSaleAttrValueDao.selectList(
                new QueryWrapper<SkuSaleAttrValueEntity>().in("sku_id", skuIds)
        );
        Map<Long, List<SkuEsModelVo.Attrs>> skuAttrsMap = saleAttrs.stream()
                .collect(Collectors.groupingBy(
                        SkuSaleAttrValueEntity::getSkuId,
                        Collectors.mapping(attr -> {
                            SkuEsModelVo.Attrs a = new SkuEsModelVo.Attrs();
                            a.setAttrId(attr.getAttrId());
                            a.setAttrName(attr.getAttrName());
                            a.setAttrValue(attr.getAttrValue());
                            return a;
                        }, Collectors.toList())
                ));

        // 7. Query base attributes (SPU attributes)
        List<ProductAttrValueEntity> baseAttrs = productAttrValueDao.selectList(
                new QueryWrapper<ProductAttrValueEntity>()
                        .eq("spu_id", spuId)
                        .eq("quick_show", 1)
        );
        List<SkuEsModelVo.Attrs> baseAttrsList = baseAttrs.stream()
                .map(attr -> {
                    SkuEsModelVo.Attrs a = new SkuEsModelVo.Attrs();
                    a.setAttrId(attr.getAttrId());
                    a.setAttrName(attr.getAttrName());
                    a.setAttrValue(attr.getAttrValue());
                    return a;
                })
                .collect(Collectors.toList());

        // 8. Assemble data
        List<SkuEsModelVo> skuEsModels = buildSkuEsModels(
                spuInfo, brand, category, skuInfoList, skuImagesMap, skuAttrsMap, baseAttrsList
        );

        // 9. Update SPU status to published
        spuInfo.setPublishStatus(1);
        spuInfo.setUpdateTime(new Date());
        this.updateById(spuInfo);

        // 10. Call search service to save to Elasticsearch
        R result = searchFeignService.productUp(new ArrayList<>(skuEsModels));
        if (result == null || result.getCode() != 0) {
            throw new RuntimeException("Product up failed: " + (result != null ? result.getMsg() : "Network exception"));
        }
    }

    /**
     * Build SKU ES models
     */
    private List<SkuEsModelVo> buildSkuEsModels(
            SpuInfoEntity spuInfo,
            BrandEntity brand,
            CategoryEntity category,
            List<SkuInfoEntity> skuInfoList,
            Map<Long, List<String>> skuImagesMap,
            Map<Long, List<SkuEsModelVo.Attrs>> skuAttrsMap,
            List<SkuEsModelVo.Attrs> baseAttrsList) {

        List<SkuEsModelVo> skuEsModels = new ArrayList<>();
        for (SkuInfoEntity skuInfo : skuInfoList) {
            SkuEsModelVo skuEsModel = new SkuEsModelVo();
            skuEsModel.setSkuId(skuInfo.getSkuId());
            skuEsModel.setSpuId(spuInfo.getId());
            skuEsModel.setSkuTitle(skuInfo.getSkuTitle());
            skuEsModel.setSkuPrice(skuInfo.getPrice());

            // Set default image
            List<String> images = skuImagesMap.get(skuInfo.getSkuId());
            if (!CollectionUtils.isEmpty(images)) {
                skuEsModel.setSkuImg(images.get(0));
            } else {
                skuEsModel.setSkuImg(skuInfo.getSkuDefaultImg());
            }

            skuEsModel.setSaleCount(skuInfo.getSaleCount() != null ? skuInfo.getSaleCount() : 0L);
            skuEsModel.setHasStock(true);
            skuEsModel.setHotScore(0L);

            skuEsModel.setBrandId(spuInfo.getBrandId());
            skuEsModel.setCategoryId(spuInfo.getCategoryId());
            skuEsModel.setBrandName(brand != null ? brand.getName() : "");
            skuEsModel.setBrandImg(brand != null ? brand.getLogo() : "");
            skuEsModel.setCategoryName(category != null ? category.getName() : "");

            // Set attributes
            List<SkuEsModelVo.Attrs> attrs = new ArrayList<>(baseAttrsList);
            List<SkuEsModelVo.Attrs> skuAttrs = skuAttrsMap.get(skuInfo.getSkuId());
            if (!CollectionUtils.isEmpty(skuAttrs)) {
                attrs.addAll(skuAttrs);
            }
            skuEsModel.setAttrs(attrs);

            skuEsModels.add(skuEsModel);
        }

        return skuEsModels;
    }
}