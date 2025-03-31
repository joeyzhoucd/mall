package com.joeyzhoucd.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.product.entity.SpuImagesEntity;

import java.util.Map;

/**
 * spu图片
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
public interface SpuImagesService extends IService<SpuImagesEntity> {

    PageUtils queryPage(Map<String, Object> params);
}

