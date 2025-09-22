package com.joeyzhoucd.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.product.entity.AttrEntity;
import com.joeyzhoucd.product.vo.AttrSaveRequestVO;

import java.util.List;
import java.util.Map;

/**
 * 商品属性
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
public interface AttrService extends IService<AttrEntity> {

    PageUtils queryPage(Map<String, Object> params);

    PageUtils querySpecAttrPage(Map<String, Object> params);

    List<AttrEntity> queryUnRelatedAttr(Long attrgroupId);

    void saveBaseAttr(AttrSaveRequestVO req);
}

