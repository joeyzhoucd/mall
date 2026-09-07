package com.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.cache.MultiLevelCacheClient;
import com.mall.common.cache.MultiLevelCacheOptions;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.product.cache.ProductHotCacheInvalidator;
import com.mall.product.dao.*;
import com.mall.product.entity.*;
import com.mall.product.service.SkuInfoService;
import com.mall.product.service.SpuInfoDescService;
import com.mall.product.vo.SkuInfoVo;
import com.mall.product.vo.SkuItemSaleAttrVo;
import com.mall.product.vo.SkuItemVo;
import com.mall.product.vo.SpuItemAttrGroupVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;


@Service("skuInfoService")
public class SkuInfoServiceImpl extends ServiceImpl<SkuInfoDao, SkuInfoEntity> implements SkuInfoService {

    private static final TypeReference<SkuInfoEntity> SKU_INFO_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<SkuItemVo> SKU_ITEM_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<SkuImagesEntity>> SKU_IMAGES_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<SkuItemSaleAttrVo>> SPU_SALE_ATTRS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<SpuInfoDescEntity> SPU_DESC_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<SpuItemAttrGroupVo>> SPU_ATTR_GROUPS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Long>> SPU_SKU_IDS_TYPE = new TypeReference<>() {
    };
    private static final MultiLevelCacheOptions SKU_INFO_CACHE_OPTIONS = new MultiLevelCacheOptions(
            Duration.ofSeconds(30),
            Duration.ofMinutes(10),
            Duration.ofSeconds(30),
            true,
            Duration.ofSeconds(5),
            Duration.ofMillis(300),
            Duration.ofMillis(20),
            0.1);
    private static final MultiLevelCacheOptions SKU_ITEM_CACHE_OPTIONS = new MultiLevelCacheOptions(
            Duration.ofSeconds(20),
            Duration.ofMinutes(5),
            Duration.ofSeconds(30),
            true,
            Duration.ofSeconds(5),
            Duration.ofMillis(300),
            Duration.ofMillis(20),
            0.1);
    private static final MultiLevelCacheOptions PRODUCT_COMPONENT_CACHE_OPTIONS = new MultiLevelCacheOptions(
            Duration.ofSeconds(30),
            Duration.ofMinutes(30),
            Duration.ofSeconds(30),
            true,
            Duration.ofSeconds(5),
            Duration.ofMillis(300),
            Duration.ofMillis(20),
            0.1);

    @Autowired
    private CategoryDao categoryDao;
    
    @Autowired
    private BrandDao brandDao;
    
    @Autowired
    private SkuImagesDao skuImagesDao;
    
    @Autowired
    private SkuSaleAttrValueDao skuSaleAttrValueDao;

    @Autowired
    private SpuInfoDescService spuInfoDescService;

    @Autowired
    private AttrGroupDao attrGroupDao;

    @Autowired
    private MultiLevelCacheClient multiLevelCacheClient;

    @Autowired
    private ProductHotCacheInvalidator productHotCacheInvalidator;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<SkuInfoEntity> wrapper = new QueryWrapper<>();

        // Search by skuId or skuName
        Object keyObj = params.get("key");
        if (keyObj != null) {
            String key = String.valueOf(keyObj).trim();
            if (!key.isEmpty()) {
                wrapper.and(w -> w.eq("sku_id", key).or().like("sku_name", key));
            }
        }

        // Filter by category and brand
        Object categoryIdObj = params.get("categoryId");
        if (categoryIdObj != null) {
            try {
                Long categoryId = Long.valueOf(String.valueOf(categoryIdObj));
                wrapper.eq("category_id", categoryId);
            } catch (Exception ignored) {
            }
        }
        Object brandIdObj = params.get("brandId");
        if (brandIdObj != null) {
            try {
                Long brandId = Long.valueOf(String.valueOf(brandIdObj));
                wrapper.eq("brand_id", brandId);
            } catch (Exception ignored) {
            }
        }

        // 分页必须有确定的排序，否则每一页都是一次独立的无序查询，
        // 行会在页与页之间重复或漏掉 —— 数据少于一页时完全看不出来。
        // sku_id 是主键，用它才能保证全序确定。
        // （同类问题在本仓库是系统性的：55 个分页查询里 50 个没有确定排序。）
        wrapper.orderByDesc("sku_id");

        IPage<SkuInfoEntity> page = this.page(new Query<SkuInfoEntity>().getPage(params), wrapper);
        return new PageUtils(page);
    }

    @Override
    public PageUtils queryPageWithDetails(Map<String, Object> params) {
        // Query SKU list
        PageUtils pageUtils = queryPage(params);
        List<SkuInfoEntity> skuList = (List<SkuInfoEntity>) pageUtils.getList();
        
        if (skuList == null || skuList.isEmpty()) {
            return pageUtils;
        }
        
        // Convert to VO and fill details
        List<SkuInfoVo> voList = new ArrayList<>();
        for (SkuInfoEntity sku : skuList) {
            SkuInfoVo vo = new SkuInfoVo();
            BeanUtils.copyProperties(sku, vo);
            
            // Fill category name
            if (sku.getCategoryId() != null) {
                CategoryEntity category = categoryDao.selectById(sku.getCategoryId());
                if (category != null) {
                    vo.setCategoryName(category.getName());
                }
            }
            
            // Fill brand name
            if (sku.getBrandId() != null) {
                BrandEntity brand = brandDao.selectById(sku.getBrandId());
                if (brand != null) {
                    vo.setBrandName(brand.getName());
                }
            }
            
            // Fill SKU images
            QueryWrapper<SkuImagesEntity> imageWrapper = new QueryWrapper<>();
            imageWrapper.eq("sku_id", sku.getSkuId()).orderByAsc("img_sort");
            List<SkuImagesEntity> images = skuImagesDao.selectList(imageWrapper);
            if (images != null && !images.isEmpty()) {
                List<SkuInfoVo.SkuImageVo> imageVos = new ArrayList<>();
                for (SkuImagesEntity image : images) {
                    SkuInfoVo.SkuImageVo imageVo = new SkuInfoVo.SkuImageVo();
                    BeanUtils.copyProperties(image, imageVo);
                    imageVos.add(imageVo);
                }
                vo.setImages(imageVos);
            }
            
            // Fill sale attributes
            QueryWrapper<SkuSaleAttrValueEntity> saleAttrWrapper = new QueryWrapper<>();
            saleAttrWrapper.eq("sku_id", sku.getSkuId()).orderByAsc("attr_sort");
            List<SkuSaleAttrValueEntity> saleAttrs = skuSaleAttrValueDao.selectList(saleAttrWrapper);
            if (saleAttrs != null && !saleAttrs.isEmpty()) {
                List<SkuInfoVo.SkuSaleAttrVo> saleAttrVos = new ArrayList<>();
                for (SkuSaleAttrValueEntity saleAttr : saleAttrs) {
                    SkuInfoVo.SkuSaleAttrVo saleAttrVo = new SkuInfoVo.SkuSaleAttrVo();
                    BeanUtils.copyProperties(saleAttr, saleAttrVo);
                    saleAttrVos.add(saleAttrVo);
                }
                vo.setSaleAttrs(saleAttrVos);
            }
            
            voList.add(vo);
        }
        
        // Return new page with details
        PageUtils result = new PageUtils(voList, (int) pageUtils.getTotalCount(), (int) pageUtils.getPageSize(), (int) pageUtils.getCurrPage());
        return result;
    }

    @Override
    public SkuItemVo item(Long skuId) {
        if (skuId == null) {
            throw new IllegalArgumentException("skuId cannot be null");
        }
        return multiLevelCacheClient.get(ProductHotCacheInvalidator.SKU_ITEM_CACHE_NAME,
                ProductHotCacheInvalidator.key(skuId),
                SKU_ITEM_TYPE,
                () -> loadSkuItemFromDb(skuId),
                SKU_ITEM_CACHE_OPTIONS);
    }

    private SkuItemVo loadSkuItemFromDb(Long skuId) {
        SkuItemVo skuItemVo = new SkuItemVo();

        SkuInfoEntity info = getBySkuId(skuId);
        skuItemVo.setInfo(info);
        if (info == null) {
            return skuItemVo;
        }

        CompletableFuture<Void> saleAttrFuture = CompletableFuture.runAsync(() -> {
            // 3. SPU Sale Attr Combination
            skuItemVo.setSaleAttr(getSaleAttrsBySpuIdCached(info.getSpuId()));
        });

        CompletableFuture<Void> descFuture = CompletableFuture.runAsync(() -> {
            // 4. SPU Description
            skuItemVo.setDesc(getSpuDescCached(info.getSpuId()));
        });

        CompletableFuture<Void> baseAttrFuture = CompletableFuture.runAsync(() -> {
            // 5. SPU Group Attrs
            skuItemVo.setGroupAttrs(getAttrGroupWithAttrsCached(info.getSpuId(), info.getCategoryId()));
        });

        CompletableFuture<Void> imageFuture = CompletableFuture.runAsync(() -> {
            // 2. SKU Images
            skuItemVo.setImages(getSkuImagesCached(skuId));
        });

        // Wait for all
        try {
            CompletableFuture.allOf(saleAttrFuture, descFuture, baseAttrFuture, imageFuture).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("load sku item failed: " + skuId, e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("load sku item failed: " + skuId, e);
        }

        return skuItemVo;
    }

    @Override
    public SkuInfoEntity getBySkuId(Long skuId) {
        if (skuId == null) {
            return null;
        }
        return multiLevelCacheClient.get(ProductHotCacheInvalidator.SKU_INFO_CACHE_NAME,
                ProductHotCacheInvalidator.key(skuId),
                SKU_INFO_TYPE,
                () -> baseMapper.selectById(skuId),
                SKU_INFO_CACHE_OPTIONS);
    }

    @Override
    public List<Long> listSkuIdsBySpuId(Long spuId) {
        if (spuId == null) {
            return List.of();
        }
        return multiLevelCacheClient.get(ProductHotCacheInvalidator.SPU_SKU_IDS_CACHE_NAME,
                ProductHotCacheInvalidator.key(spuId),
                SPU_SKU_IDS_TYPE,
                () -> baseMapper.selectList(new QueryWrapper<SkuInfoEntity>()
                                .select("sku_id")
                                .eq("spu_id", spuId)
                                .orderByAsc("sku_id"))
                        .stream()
                        .map(SkuInfoEntity::getSkuId)
                        .collect(Collectors.toList()),
                PRODUCT_COMPONENT_CACHE_OPTIONS);
    }

    @Override
    public SkuInfoEntity getById(Serializable id) {
        if (id instanceof Long skuId) {
            return getBySkuId(skuId);
        }
        return super.getById(id);
    }

    private List<SkuImagesEntity> getSkuImagesCached(Long skuId) {
        return multiLevelCacheClient.get(ProductHotCacheInvalidator.SKU_IMAGES_CACHE_NAME,
                ProductHotCacheInvalidator.key(skuId),
                SKU_IMAGES_TYPE,
                () -> skuImagesDao.selectList(new QueryWrapper<SkuImagesEntity>()
                        .eq("sku_id", skuId)
                        .orderByAsc("img_sort")),
                PRODUCT_COMPONENT_CACHE_OPTIONS);
    }

    private List<SkuItemSaleAttrVo> getSaleAttrsBySpuIdCached(Long spuId) {
        return multiLevelCacheClient.get(ProductHotCacheInvalidator.SPU_SALE_ATTRS_CACHE_NAME,
                ProductHotCacheInvalidator.key(spuId),
                SPU_SALE_ATTRS_TYPE,
                () -> skuSaleAttrValueDao.getSaleAttrsBySpuId(spuId),
                PRODUCT_COMPONENT_CACHE_OPTIONS);
    }

    private SpuInfoDescEntity getSpuDescCached(Long spuId) {
        return multiLevelCacheClient.get(ProductHotCacheInvalidator.SPU_DESC_CACHE_NAME,
                ProductHotCacheInvalidator.key(spuId),
                SPU_DESC_TYPE,
                () -> spuInfoDescService.getById(spuId),
                PRODUCT_COMPONENT_CACHE_OPTIONS);
    }

    private List<SpuItemAttrGroupVo> getAttrGroupWithAttrsCached(Long spuId, Long categoryId) {
        return multiLevelCacheClient.get(ProductHotCacheInvalidator.SPU_ATTR_GROUPS_CACHE_NAME,
                ProductHotCacheInvalidator.key(spuId),
                SPU_ATTR_GROUPS_TYPE,
                () -> attrGroupDao.getAttrGroupWithAttrsBySpuId(spuId, categoryId),
                PRODUCT_COMPONENT_CACHE_OPTIONS);
    }

    @Override
    public boolean updateById(SkuInfoEntity entity) {
        boolean result = super.updateById(entity);
        if (result && entity != null) {
            productHotCacheInvalidator.evictSkuAfterCommit(entity.getSkuId());
        }
        return result;
    }

    @Override
    public boolean removeByIds(Collection<?> list) {
        List<Long> skuIds = normalizeIds(list);
        if (skuIds.isEmpty()) {
            return false;
        }
        List<Long> spuIds = baseMapper.selectBatchIds(skuIds).stream()
                .map(SkuInfoEntity::getSpuId)
                .collect(Collectors.toList());
        boolean result = super.removeByIds(list);
        if (result) {
            productHotCacheInvalidator.evictSkusAfterCommit(skuIds);
            productHotCacheInvalidator.evictSpusAfterCommit(spuIds);
        }
        return result;
    }

    @Override
    @Transactional
    public void removeSkus(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return;
        }
        // 先子后父。这三张表之间【没有外键约束】，数据库不会替我们把顺序和完整性兜住，
        // 漏一张就留下一批指向已删 SKU 的孤儿行；而自增主键被复用时，
        // 新 SKU 会凭空继承上一个 SKU 的图片和销售属性。
        skuImagesDao.delete(new QueryWrapper<SkuImagesEntity>().in("sku_id", skuIds));
        skuSaleAttrValueDao.delete(new QueryWrapper<SkuSaleAttrValueEntity>().in("sku_id", skuIds));
        this.removeByIds(skuIds);
    }

    @Override
    public void updateBasicInfo(SkuInfoEntity sku) {
        if (sku == null || sku.getSkuId() == null) {
            throw new IllegalArgumentException("skuId 不能为空");
        }
        // 白名单：只把这四个字段抄进一个干净对象再更新，请求体里的其它字段一律不生效。
        // 理由见 SkuInfoService#updateBasicInfo 的注释（防越权写入，不是防 null 覆盖）。
        SkuInfoEntity patch = new SkuInfoEntity();
        patch.setSkuId(sku.getSkuId());
        patch.setSkuName(sku.getSkuName());
        patch.setSkuTitle(sku.getSkuTitle());
        patch.setSkuSubtitle(sku.getSkuSubtitle());
        patch.setPrice(sku.getPrice());
        this.updateById(patch);
    }

    @Override
    public int batchPublish(List<Long> skuIds, Integer publishStatus) {
        if (skuIds == null || skuIds.isEmpty() || publishStatus == null) {
            return 0;
        }
        // 只接受 0/1。不校验的话传个 2 会写进去，之后所有「= 1 才算在售」的判断
        // 都会把它当成下架，而管理员在界面上看到的是「操作成功」。
        if (publishStatus != 0 && publishStatus != 1) {
            throw new IllegalArgumentException("publishStatus 只能是 0（下架）或 1（上架），收到: " + publishStatus);
        }
        SkuInfoEntity patch = new SkuInfoEntity();
        patch.setPublishStatus(publishStatus);
        int updated = this.baseMapper.update(patch, new QueryWrapper<SkuInfoEntity>().in("sku_id", skuIds));
        if (updated > 0) {
            productHotCacheInvalidator.evictSkusAfterCommit(skuIds);
        }
        return updated;
    }

    private List<Long> normalizeIds(Collection<?> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null)
                .map(id -> Long.valueOf(String.valueOf(id)))
                .distinct()
                .collect(Collectors.toList());
    }

}
