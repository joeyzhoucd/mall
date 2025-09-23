package com.joeyzhoucd.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.product.entity.AttrAttrgroupRelationEntity;
import com.joeyzhoucd.product.vo.AttrAttrgroupRelationVO;

import java.util.List;
import java.util.Map;

/**
 * 属性&属性分组关联
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
public interface AttrAttrgroupRelationService extends IService<AttrAttrgroupRelationEntity> {

    PageUtils queryPage(Map<String, Object> params);

    List<AttrAttrgroupRelationVO> getAttrsByGroupId(Long groupId);

    void removeRelation(Long attrId, Long groupId);

    void saveBatch(List<AttrAttrgroupRelationEntity> relations);

}

