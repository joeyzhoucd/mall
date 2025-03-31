package com.joeyzhoucd.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.product.entity.SkuBaseAttrValueEntity;

import java.util.Map;

/**
 * sku基本属性&值
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
public interface SkuBaseAttrValueService extends IService<SkuBaseAttrValueEntity> {

    PageUtils queryPage(Map<String, Object> params);
}

