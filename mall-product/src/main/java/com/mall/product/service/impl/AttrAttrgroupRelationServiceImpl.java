package com.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.product.dao.AttrAttrgroupRelationDao;
import com.mall.product.entity.AttrAttrgroupRelationEntity;
import com.mall.product.entity.AttrEntity;
import com.mall.product.service.AttrAttrgroupRelationService;
import com.mall.product.service.AttrService;
import com.mall.product.vo.AttrAttrgroupRelationVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service("attrAttrgroupRelationService")
public class AttrAttrgroupRelationServiceImpl extends ServiceImpl<AttrAttrgroupRelationDao, AttrAttrgroupRelationEntity> implements AttrAttrgroupRelationService {

    @Autowired
    AttrService attrService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<AttrAttrgroupRelationEntity> page = this.page(
                new Query<AttrAttrgroupRelationEntity>().getPage(params),
                new QueryWrapper<>()
        );

        return new PageUtils(page);
    }

    @Override
    public List<AttrAttrgroupRelationVO> getAttrsByGroupId(Long groupId) {
        QueryWrapper<AttrAttrgroupRelationEntity> wrapper = new QueryWrapper();
        wrapper.eq("attr_group_id", groupId);
        List<AttrAttrgroupRelationEntity> relationEntities = this.list(wrapper);
        if (CollectionUtils.isEmpty(relationEntities)) {
            return null;
        }
        List<AttrAttrgroupRelationVO> relationVOS = relationEntities.stream().map(entity -> {
            AttrAttrgroupRelationVO vo = new AttrAttrgroupRelationVO();
            BeanUtils.copyProperties(entity, vo);
            AttrEntity attrEntity = attrService.getById(entity.getAttrId());
            if (attrEntity != null) {
                vo.setAttr(attrEntity);
                vo.setAttrName(attrEntity.getAttrName());
            }
            return vo;
        }).collect(Collectors.toList());
        return relationVOS;
    }

    @Override
    public void removeRelation(Long attrId, Long groupId) {
        LambdaQueryWrapper<AttrAttrgroupRelationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AttrAttrgroupRelationEntity::getAttrId, attrId)
                .eq(AttrAttrgroupRelationEntity::getAttrGroupId, groupId);
        this.remove(wrapper);
    }

    @Override
    public void saveBatch(List<AttrAttrgroupRelationEntity> relations) {
        super.saveBatch(relations);
    }

}
