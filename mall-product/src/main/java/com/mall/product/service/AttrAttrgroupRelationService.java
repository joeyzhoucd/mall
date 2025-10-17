package com.mall.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.product.entity.AttrAttrgroupRelationEntity;
import com.mall.product.vo.AttrAttrgroupRelationVO;

import java.util.List;
import java.util.Map;

/**
 * å±žæ€§&å±žæ€§åˆ†ç»„å…³è”
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

