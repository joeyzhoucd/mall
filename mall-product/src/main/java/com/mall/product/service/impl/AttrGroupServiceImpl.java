package com.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.product.dao.AttrAttrgroupRelationDao;
import com.mall.product.dao.AttrDao;
import com.mall.product.dao.AttrGroupDao;
import com.mall.product.entity.AttrAttrgroupRelationEntity;
import com.mall.product.entity.AttrEntity;
import com.mall.product.entity.AttrGroupEntity;
import com.mall.product.service.AttrGroupService;
import com.mall.product.vo.AttrGroupWithAttrVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service("attrGroupService")
public class AttrGroupServiceImpl extends ServiceImpl<AttrGroupDao, AttrGroupEntity> implements AttrGroupService {

    @Autowired
    AttrAttrgroupRelationDao attrAttrgroupRelationDao;

    @Autowired
    AttrDao attrDao;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        LambdaQueryWrapper<AttrGroupEntity> lqw = Wrappers.lambdaQuery();
        Object categoryId = params.get("categoryId");
        lqw.eq(categoryId != null, AttrGroupEntity::getCategoryId, categoryId);
        IPage<AttrGroupEntity> page = this.page(new Query<AttrGroupEntity>().getPage(params), lqw);
        return new PageUtils(page);
    }

    @Override
    public List<AttrGroupWithAttrVO> getAttrGroupWithAttrs(Long categoryId) {
        //é€šè¿‡categoryæ‰¾åˆ°attr group
        LambdaQueryWrapper<AttrGroupEntity> lqw = Wrappers.lambdaQuery();
        lqw.eq(categoryId != null, AttrGroupEntity::getCategoryId, categoryId);
        List<AttrGroupEntity> attrGroupEntities = this.list(lqw);
        if (CollectionUtils.isEmpty(attrGroupEntities)) {
            return null;
        }

        List<AttrGroupWithAttrVO> collect = attrGroupEntities.stream().map(attrGroupEntity -> {
            Long attrGroupId = attrGroupEntity.getAttrGroupId();
            LambdaQueryWrapper<AttrAttrgroupRelationEntity> qw = Wrappers.lambdaQuery();
            qw.eq(attrGroupId != null, AttrAttrgroupRelationEntity::getAttrGroupId, attrGroupId);
            List<AttrAttrgroupRelationEntity> attrgroupRelations = attrAttrgroupRelationDao.selectList(qw);
            List<Long> ids = new ArrayList<>();
            if (attrgroupRelations != null) {
                attrgroupRelations.stream().forEach(vo -> {
                    ids.add(vo.getAttrId());
                });
            }
            AttrGroupWithAttrVO vo = new AttrGroupWithAttrVO();
            BeanUtils.copyProperties(attrGroupEntity, vo);
            if (!CollectionUtils.isEmpty(ids)) {
                List<AttrEntity> attrEntities = attrDao.selectBatchIds(ids);
                vo.setAttrs(attrEntities);
            }
            return vo;
        }).collect(Collectors.toList());

        return collect;
    }

}
