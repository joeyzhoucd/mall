package com.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.product.dao.AttrAttrgroupRelationDao;
import com.mall.product.dao.AttrDao;
import com.mall.product.dao.CategoryDao;
import com.mall.product.entity.AttrAttrgroupRelationEntity;
import com.mall.product.entity.AttrEntity;
import com.mall.product.entity.AttrGroupEntity;
import com.mall.product.entity.CategoryEntity;
import com.mall.product.service.AttrGroupService;
import com.mall.product.service.AttrService;
import com.mall.product.vo.AttrResponseVO;
import com.mall.product.vo.AttrSaveRequestVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;



@Service("attrService")
public class AttrServiceImpl extends ServiceImpl<AttrDao, AttrEntity> implements AttrService {

    @Autowired
    private AttrGroupService attrGroupService;

    @Autowired
    private CategoryDao categoryDao;

    @Autowired
    private AttrAttrgroupRelationDao attrAttrgroupRelationDao;


    @Override
    public PageUtils querySpecAttrPage(Map<String, Object> params) {
        params.put("attr_type", 1);
        return queryAttrPage(params);
    }

    public PageUtils queryAttrPage(Map<String, Object> params) {
        QueryWrapper<AttrEntity> wrapper = new QueryWrapper<>();

        // ------------------------------------------------------------------
        // 【categoryId 缺失或为 0 表示"不限分类"，绝不能无条件加等值条件】
        // ------------------------------------------------------------------
        // 原来是：
        //     Object categoryId = params.getOrDefault("categoryId", 0L);
        //     wrapper.eq("category_id", categoryId);
        //
        // 前端的规格属性/销售属性页打开时不带 categoryId（那一页没有分类筛选），
        // 于是 categoryId 取默认值 0，拼出 WHERE category_id = 0 ——
        // 而【没有任何属性的 category_id 是 0】（2026-09-10 实测：
        // pms_attr 里 category_id 的取值范围是 3..35，等于 0 的有 0 条）。
        // 结果是这两个页面的列表【永远是空的】，而接口返回 code 0、totalCount 0，
        // 看起来像"就是没有数据"，实际库里有 81 条规格属性和 54 条销售属性。
        //
        // 注意紧下面那行 attr_type 用的就是带条件的重载 —— 同一个方法里
        // 一处写对一处写错，而写错的那处没有任何报错。
        //
        // 0 表示不限是 gulimall 沿用下来的约定（前端筛选框的"全部"传 0）。
        Long categoryId = FilterParams.positiveLongOrNull(params.get("categoryId"));
        wrapper.eq(categoryId != null, "category_id", categoryId);

        Integer type = (Integer) params.get("attr_type");
        wrapper.eq(type != null, "attr_type", type);
        String key = (String) params.get("key");
        if (StringUtils.hasText(key)) {
            wrapper.and(w -> w.eq("attr_id", key).or().like("attr_name", key));
        }
        IPage<AttrEntity> page = this.page(new Query<AttrEntity>().getPage(params), wrapper);
        IPage<AttrResponseVO> VOPage = page.convert(this::convertToVO);
        if (!CollectionUtils.isEmpty(VOPage.getRecords())) {
            VOPage.getRecords().stream().forEach(vo -> {
                CategoryEntity categoryEntity = categoryDao.selectById(vo.getCategoryId());
                if (categoryEntity != null) {
                    vo.setCategoryName(categoryEntity.getName());
                }
                AttrAttrgroupRelationEntity attrAttrgroupRelation = attrAttrgroupRelationDao.selectOne(new QueryWrapper<AttrAttrgroupRelationEntity>().eq("attr_id", vo.getAttrId()));
                if (attrAttrgroupRelation != null) {
                    AttrGroupEntity attrGroupEntity = attrGroupService.getById(attrAttrgroupRelation.getAttrGroupId());
                    if (attrGroupEntity != null) {
                        vo.setAttrGroupId(attrGroupEntity.getAttrGroupId());
                        vo.setAttrGroupName(attrGroupEntity.getAttrGroupName());
                    } else {
                        // Clean up invalid relations
                        attrAttrgroupRelationDao.delete(
                                new QueryWrapper<AttrAttrgroupRelationEntity>()
                                        .eq("attr_id", vo.getAttrId())
                        );
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
        Assert.notNull(attrGroup, "Attribute group not found!");
        Long categoryId = attrGroup.getCategoryId();

        // 2. Get all used attributes in the same category and exclude them
        List<Long> usedAttrIds = attrAttrgroupRelationDao.selectList(
                        new LambdaQueryWrapper<AttrAttrgroupRelationEntity>()
                ).stream()
                .map(AttrAttrgroupRelationEntity::getAttrId)
                .distinct()
                .collect(Collectors.toList());

        // 3. Build query conditions
        LambdaQueryWrapper<AttrEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AttrEntity::getCategoryId, categoryId)     // Same category
                .eq(AttrEntity::getAttrType, 1);          // Specification attributes

        if (!CollectionUtils.isEmpty(usedAttrIds)) {
            wrapper.notIn(AttrEntity::getAttrId, usedAttrIds); // Exclude used ones
        }

        return this.list(wrapper);
    }


    @Transactional
    @Override
    public void saveBaseAttr(AttrSaveRequestVO req) {
        AttrEntity entity = buildAttrEntity(req, 1); // Specification
        this.save(entity);

        // Handle attribute group relations
        handleAttrGroupRelation(req, entity.getAttrId());
    }

    @Transactional
    @Override
    public void updateBaseAttr(AttrSaveRequestVO req) {
        AttrEntity entity = buildAttrEntity(req, 1); // Specification
        this.updateById(entity);

        // Handle attribute group relations
        handleAttrGroupRelation(req, req.getAttrId());
    }

    // ==================== Sale Attributes ====================

    @Override
    public PageUtils querySaleAttrPage(Map<String, Object> params) {
        params.put("attr_type", 0);
        return queryAttrPage(params);
    }


    @Transactional
    @Override
    public void saveSaleAttr(AttrSaveRequestVO req) {
        AttrEntity entity = buildAttrEntity(req, 0); // Sale attributes
        this.save(entity);
        // Sale attributes don't need group relations
    }

    @Transactional
    @Override
    public void updateSaleAttr(AttrSaveRequestVO req) {
        AttrEntity entity = buildAttrEntity(req, 0); // Sale attributes
        this.updateById(entity);
        // Sale attributes don't need group relations
    }

    
    private AttrEntity buildAttrEntity(AttrSaveRequestVO req, Integer attrType) {
        AttrEntity entity = new AttrEntity();
        entity.setAttrId(req.getAttrId());
        entity.setAttrName(req.getAttrName());
        entity.setSearchType(req.getSearchType());
        entity.setIcon(req.getIcon());
        entity.setValueSelect(req.getValueSelect());
        entity.setAttrType(attrType);
        entity.setEnable(1L);
        entity.setCategoryId(Long.valueOf(req.getCategoryId()));
        entity.setShowDesc(req.getShowDesc());
        return entity;
    }

    
    private void handleAttrGroupRelation(AttrSaveRequestVO req, Long attrId) {
        // If attrGroupId is provided, create new relation
        if (req.getAttrGroupId() != null && !req.getAttrGroupId().trim().isEmpty()) {
            Long attrGroupId = Long.valueOf(req.getAttrGroupId());
            // Validate group exists
            AttrGroupEntity attrGroupEntity = attrGroupService.getById(attrGroupId);
            if (attrGroupEntity != null) {
                // Delete existing relations
                attrAttrgroupRelationDao.delete(
                        new QueryWrapper<AttrAttrgroupRelationEntity>()
                                .eq("attr_id", attrId)
                );

                // Create new relation
                AttrAttrgroupRelationEntity relation = new AttrAttrgroupRelationEntity();
                relation.setAttrId(attrId);
                relation.setAttrGroupId(attrGroupId);
                relation.setAttrSort(0);
                attrAttrgroupRelationDao.insert(relation);
            }
        }
    }

    // ==================== Delete Operations ====================

    @Transactional
    @Override
    public void deleteAttrWithRelations(Long attrId) {
        // 1. Delete attribute group relations
        attrAttrgroupRelationDao.delete(
                new QueryWrapper<AttrAttrgroupRelationEntity>()
                        .eq("attr_id", attrId)
        );

        // 2. Delete attribute
        this.removeById(attrId);
    }

    @Transactional
    @Override
    public void deleteAttrsWithRelations(Long[] attrIds) {
        // 1. Batch delete attribute group relations
        attrAttrgroupRelationDao.delete(
                new QueryWrapper<AttrAttrgroupRelationEntity>()
                        .in("attr_id", Arrays.asList(attrIds))
        );

        // 2. Batch delete attributes
        this.removeByIds(Arrays.asList(attrIds));
    }
}