package com.mall.product.vo;

import com.mall.product.entity.SkuImagesEntity;
import com.mall.product.entity.SkuInfoEntity;
import com.mall.product.entity.SpuInfoDescEntity;
import lombok.Data;

import java.util.List;

@Data
public class SkuItemVo {
    // 1. SKU Basic Info
    private SkuInfoEntity info;

    // 2. SKU Images
    private List<SkuImagesEntity> images;

    // 3. SPU Sales Attributes Combination
    private List<SkuItemSaleAttrVo> saleAttr;

    // 4. SPU Description (Intro)
    private SpuInfoDescEntity desc;

    // 5. SPU Specification Parameters Group
    private List<SpuItemAttrGroupVo> groupAttrs;

    // 6. Stock Status
    private boolean hasStock = true;
}

