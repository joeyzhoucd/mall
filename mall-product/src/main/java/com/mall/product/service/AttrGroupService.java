package com.mall.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.product.entity.AttrGroupEntity;
import com.mall.product.vo.AttrGroupWithAttrVO;

import java.util.List;
import java.util.Map;


public interface AttrGroupService extends IService<AttrGroupEntity> {

    PageUtils queryPage(Map<String, Object> params);

    List<AttrGroupWithAttrVO> getAttrGroupWithAttrs(Long categoryId);
}
