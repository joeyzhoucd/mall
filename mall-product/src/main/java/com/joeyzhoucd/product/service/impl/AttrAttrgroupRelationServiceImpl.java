package com.joeyzhoucd.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.Query;
import com.joeyzhoucd.product.dao.AttrAttrgroupRelationDao;
import com.joeyzhoucd.product.entity.AttrAttrgroupRelationEntity;
import com.joeyzhoucd.product.entity.AttrEntity;
import com.joeyzhoucd.product.service.AttrAttrgroupRelationService;
import com.joeyzhoucd.product.service.AttrService;
import com.joeyzhoucd.product.vo.AttrAttrgroupRelationVO;
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
            vo.setAttr(attrEntity);
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
        this.saveBatch(relations);
    }

}