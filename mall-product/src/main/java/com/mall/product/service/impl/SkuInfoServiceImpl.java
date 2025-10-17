package com.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.product.dao.*;
import com.mall.product.entity.*;
import com.mall.product.service.SkuInfoService;
import com.mall.product.vo.SkuInfoVo;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


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

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<SkuInfoEntity> wrapper = new QueryWrapper<>();

        // å…³é”®å­—ï¼šskuId æˆ– skuName
        Object keyObj = params.get("key");
        if (keyObj != null) {
            String key = String.valueOf(keyObj).trim();
            if (!key.isEmpty()) {
                wrapper.and(w -> w.eq("sku_id", key).or().like("sku_name", key));
            }
        }

        // åˆ†ç±»ä¸Žå“ç‰Œï¼ˆå¦‚æžœSKUè¡¨å¸¦æœ‰è¿™äº›å­—æ®µï¼‰
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
        // å…ˆæŸ¥è¯¢åŸºç¡€SKUä¿¡æ¯
        PageUtils pageUtils = queryPage(params);
        List<SkuInfoEntity> skuList = (List<SkuInfoEntity>) pageUtils.getList();
        
        if (skuList == null || skuList.isEmpty()) {
            return pageUtils;
        }
        
        // è½¬æ¢ä¸ºVOå¹¶å¡«å……å…³è”æ•°æ®
        List<SkuInfoVo> voList = new ArrayList<>();
        for (SkuInfoEntity sku : skuList) {
            SkuInfoVo vo = new SkuInfoVo();
            BeanUtils.copyProperties(sku, vo);
            
            // å¡«å……åˆ†ç±»åç§°
            if (sku.getCategoryId() != null) {
                CategoryEntity category = categoryDao.selectById(sku.getCategoryId());
                if (category != null) {
                    vo.setCategoryName(category.getName());
                }
            }
            
            // å¡«å……å“ç‰Œåç§°
            if (sku.getBrandId() != null) {
                BrandEntity brand = brandDao.selectById(sku.getBrandId());
                if (brand != null) {
                    vo.setBrandName(brand.getName());
                }
            }
            
            // å¡«å……SKUå›¾ç‰‡
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
            
            // å¡«å……é”€å”®å±žæ€§
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
        
        // æž„å»ºæ–°çš„åˆ†é¡µç»“æžœ
        PageUtils result = new PageUtils(voList, (int) pageUtils.getTotalCount(), (int) pageUtils.getPageSize(), (int) pageUtils.getCurrPage());
        return result;
    }

}
