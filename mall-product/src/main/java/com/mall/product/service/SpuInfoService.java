package com.mall.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.product.entity.SpuInfoEntity;
import com.mall.product.vo.SpuSaveVo;

import java.util.Map;

/**
 * spuä¿¡æ¯
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
public interface SpuInfoService extends IService<SpuInfoEntity> {

    PageUtils queryPage(Map<String, Object> params);

    /**
     * ä¿å­˜SPUä¿¡æ¯
     */
    void saveSpuInfo(SpuSaveVo spuSaveVo);
}

