package com.joeyzhoucd.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.product.entity.SpuInfoEntity;
import com.joeyzhoucd.product.vo.SpuSaveVo;

import java.util.Map;

/**
 * spu信息
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
public interface SpuInfoService extends IService<SpuInfoEntity> {

    PageUtils queryPage(Map<String, Object> params);

    /**
     * 保存SPU信息
     */
    void saveSpuInfo(SpuSaveVo spuSaveVo);
}

