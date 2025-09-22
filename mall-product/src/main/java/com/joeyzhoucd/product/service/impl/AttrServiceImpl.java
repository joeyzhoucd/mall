package com.joeyzhoucd.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.joeyzhoucd.common.utils.PageUtils;
import com.joeyzhoucd.common.utils.Query;
import com.joeyzhoucd.product.dao.AttrAttrgroupRelationDao;
import com.joeyzhoucd.product.dao.AttrDao;
import com.joeyzhoucd.product.dao.CategoryDao;
import com.joeyzhoucd.product.entity.AttrAttrgroupRelationEntity;
import com.joeyzhoucd.product.entity.AttrEntity;
import com.joeyzhoucd.product.entity.AttrGroupEntity;
import com.joeyzhoucd.product.entity.CategoryEntity;
import com.joeyzhoucd.product.service.AttrGroupService;
import com.joeyzhoucd.product.service.AttrService;
import com.joeyzhoucd.product.vo.AttrResponseVO;
import com.joeyzhoucd.product.vo.AttrSaveRequestVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service("attrService")
public class AttrServiceImpl extends ServiceImpl<AttrDao, AttrEntity> implements AttrService {

    private static final Logger log = LoggerFactory.getLogger(AttrServiceImpl.class);

    @Autowired
    AttrGroupService attrGroupService;

    @Autowired
    CategoryDao categoryDao;

    @Autowired
    AttrAttrgroupRelationDao attrAttrgroupRelationDao;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<AttrEntity> page = this.page(
                new Query<AttrEntity>().getPage(params),
                new QueryWrapper<>()
        );

        return new PageUtils(page);
    }

    @Override
    public PageUtils querySpecAttrPage(Map<String, Object> params) {
        params.put("attr_type", 1);
        return queryAttrPage(params);
    }

    public PageUtils queryAttrPage(Map<String, Object> params) {
        Object categoryId = params.getOrDefault("categoryId", 0L);
        QueryWrapper<AttrEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("category_id", categoryId);
        Integer type = (Integer) params.get("attr_type");
        wrapper.eq(type != null, "attr_type", type);
        String key = (String) params.get("key");
        if (StringUtils.hasText(key)) {
            wrapper.and(w -> w.eq("attr_id", key).or().like("attr_name", key));
        }
        IPage<AttrEntity> page = this.page(new Query<AttrEntity>().getPage(params), wrapper);
        IPage<AttrResponseVO> VOPage = page.convert(this::convertToVO);
        if (!CollectionUtils.isEmpty(VOPage.getRecords())) {
            VOPage.getRecords().stream().forEach(entity -> {
                CategoryEntity categoryEntity = categoryDao.selectById(entity.getCategoryId());
                if (categoryEntity != null) {
                    entity.setCategoryName(categoryEntity.getName());
                }
                log.info("queryAttrPage() entity:{}", entity);
                AttrAttrgroupRelationEntity attrAttrgroupRelation = attrAttrgroupRelationDao.selectOne(new QueryWrapper<AttrAttrgroupRelationEntity>().eq("attr_id", entity.getAttrId()));
                log.info("queryAttrPage() attrAttrgroupRelation:{}", attrAttrgroupRelation);
                if (attrAttrgroupRelation != null) {
                    AttrGroupEntity attrGroupEntity = attrGroupService.getById(attrAttrgroupRelation.getAttrGroupId());
                    if (attrGroupEntity != null) {
                        entity.setAttrGroupId(attrGroupEntity.getAttrGroupId());
                        entity.setAttrGroupName(attrGroupEntity.getAttrGroupName());
                    }
                }
            });
        }

        return new PageUtils(VOPage);
    }

    private AttrResponseVO convertToVO(AttrEntity entity) {
        AttrResponseVO vo = new AttrResponseVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    @Override
    public List<AttrEntity> queryUnRelatedAttr(Long attrgroupId) {
        AttrGroupEntity attrGroup = attrGroupService.getById(attrgroupId);
        Assert.notNull(attrGroup, "当前属性分组不存在!");
        Long categoryId = attrGroup.getCategoryId();
        // 2. 找出当前分类下，所有已经被“其它属性组”引用过的属性 id
        List<Long> usedAttrIds = attrAttrgroupRelationDao.selectList(
                        new LambdaQueryWrapper<AttrAttrgroupRelationEntity>()
                                .ne(AttrAttrgroupRelationEntity::getAttrGroupId, attrgroupId)
                ).stream()
                .map(AttrAttrgroupRelationEntity::getAttrId)
                .distinct()
                .collect(Collectors.toList());

        // 3. 构建查询条件
        LambdaQueryWrapper<AttrEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AttrEntity::getCategoryId, categoryId)     // 当前分类
                .eq(AttrEntity::getAttrType, 1);          // 基本属性

        if (!CollectionUtils.isEmpty(usedAttrIds)) {
            wrapper.notIn(AttrEntity::getAttrId, usedAttrIds); // 未被其它组引用
        }

        return this.list(wrapper);
    }

    @Transactional
    @Override
    public void saveBaseAttr(AttrSaveRequestVO req) {
        // 1. 保存属性
        AttrEntity entity = new AttrEntity();
        entity.setAttrName(req.getAttrName());
        entity.setSearchType(req.getSearchType());
        entity.setIcon(req.getIcon());
        entity.setValueSelect(req.getValueSelect());
        entity.setAttrType(1);// 默认基本属性
        entity.setEnable(1L);
        entity.setCategoryId(Long.valueOf(req.getCategoryId()));
        entity.setShowDesc(req.getShowDesc());

        this.save(entity);

        // 2. 如果是基本属性并且传了 attrGroupId，则保存中间表
        if (req.getAttrGroupId() != null) {
            AttrAttrgroupRelationEntity relation = new AttrAttrgroupRelationEntity();
            relation.setAttrId(entity.getAttrId());
            relation.setAttrGroupId(Long.valueOf(req.getAttrGroupId()));
            relation.setAttrSort(0);
            attrAttrgroupRelationDao.insert(relation);
        }
    }
}