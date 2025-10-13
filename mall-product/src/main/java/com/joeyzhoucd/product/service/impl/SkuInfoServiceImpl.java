package com.joeyzhoucd.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.Query;
import com.joeyzhoucd.product.dao.*;
import com.joeyzhoucd.product.entity.*;
import com.joeyzhoucd.product.service.SkuInfoService;
import com.joeyzhoucd.product.vo.SkuInfoVo;
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

        // 关键字：skuId 或 skuName
        Object keyObj = params.get("key");
        if (keyObj != null) {
            String key = String.valueOf(keyObj).trim();
            if (!key.isEmpty()) {
                wrapper.and(w -> w.eq("sku_id", key).or().like("sku_name", key));
            }
        }

        // 分类与品牌（如果SKU表带有这些字段）
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
        // 先查询基础SKU信息
        PageUtils pageUtils = queryPage(params);
        List<SkuInfoEntity> skuList = (List<SkuInfoEntity>) pageUtils.getList();
        
        if (skuList == null || skuList.isEmpty()) {
            return pageUtils;
        }
        
        // 转换为VO并填充关联数据
        List<SkuInfoVo> voList = new ArrayList<>();
        for (SkuInfoEntity sku : skuList) {
            SkuInfoVo vo = new SkuInfoVo();
            BeanUtils.copyProperties(sku, vo);
            
            // 填充分类名称
            if (sku.getCategoryId() != null) {
                CategoryEntity category = categoryDao.selectById(sku.getCategoryId());
                if (category != null) {
                    vo.setCategoryName(category.getName());
                }
            }
            
            // 填充品牌名称
            if (sku.getBrandId() != null) {
                BrandEntity brand = brandDao.selectById(sku.getBrandId());
                if (brand != null) {
                    vo.setBrandName(brand.getName());
                }
            }
            
            // 填充SKU图片
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
            
            // 填充销售属性
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
        
        // 构建新的分页结果
        PageUtils result = new PageUtils(voList, (int) pageUtils.getTotalCount(), (int) pageUtils.getPageSize(), (int) pageUtils.getCurrPage());
        return result;
    }

}