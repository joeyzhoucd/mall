package com.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.product.dao.*;
import com.mall.product.entity.*;
import com.mall.product.service.SkuInfoService;
import com.mall.product.service.SpuInfoDescService;
import com.mall.product.vo.SkuInfoVo;
import com.mall.product.vo.SkuItemSaleAttrVo;
import com.mall.product.vo.SkuItemVo;
import com.mall.product.vo.SpuItemAttrGroupVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.transaction.annotation.Transactional;


@Service("skuInfoService")
public class SkuInfoServiceImpl extends ServiceImpl<SkuInfoDao, SkuInfoEntity> implements SkuInfoService {

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
        SkuItemVo skuItemVo = new SkuItemVo();

        CompletableFuture<SkuInfoEntity> infoFuture = CompletableFuture.supplyAsync(() -> {
            // 1. SKU Info
            SkuInfoEntity info = getById(skuId);
            skuItemVo.setInfo(info);
            return info;
        });

        CompletableFuture<Void> saleAttrFuture = infoFuture.thenAcceptAsync((info) -> {
            // 3. SPU Sale Attr Combination
            List<SkuItemSaleAttrVo> saleAttr = skuSaleAttrValueDao.getSaleAttrsBySpuId(info.getSpuId());
            skuItemVo.setSaleAttr(saleAttr);
        });

        CompletableFuture<Void> descFuture = infoFuture.thenAcceptAsync((info) -> {
            // 4. SPU Description
            SpuInfoDescEntity desc = spuInfoDescService.getById(info.getSpuId());
            skuItemVo.setDesc(desc);
        });

        CompletableFuture<Void> baseAttrFuture = infoFuture.thenAcceptAsync((info) -> {
            // 5. SPU Group Attrs
            List<SpuItemAttrGroupVo> groupAttrs = attrGroupDao.getAttrGroupWithAttrsBySpuId(info.getSpuId(), info.getCategoryId());
            skuItemVo.setGroupAttrs(groupAttrs);
        });

        CompletableFuture<Void> imageFuture = CompletableFuture.runAsync(() -> {
            // 2. SKU Images
            List<SkuImagesEntity> images = skuImagesDao.selectList(new QueryWrapper<SkuImagesEntity>().eq("sku_id", skuId));
            skuItemVo.setImages(images);
        });

        // Wait for all
        try {
            CompletableFuture.allOf(saleAttrFuture, descFuture, baseAttrFuture, imageFuture).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }

        return skuItemVo;
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
        return this.baseMapper.update(patch, new QueryWrapper<SkuInfoEntity>().in("sku_id", skuIds));
    }

}
