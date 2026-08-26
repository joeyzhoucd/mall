package com.mall.product.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.product.entity.AttrAttrgroupRelationEntity;
import com.mall.product.vo.AttrAttrgroupRelationVO;

import java.util.List;
import java.util.Map;


public interface AttrAttrgroupRelationService extends IService<AttrAttrgroupRelationEntity> {

    PageUtils queryPage(Map<String, Object> params);

    List<AttrAttrgroupRelationVO> getAttrsByGroupId(Long groupId);

    void removeRelation(Long attrId, Long groupId);

    void saveBatch(List<AttrAttrgroupRelationEntity> relations);

}
