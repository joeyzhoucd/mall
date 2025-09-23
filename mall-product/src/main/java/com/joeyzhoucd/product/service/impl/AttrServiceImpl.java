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


/**
 * 商品属性服务实现类
 * 专门处理规格参数相关的业务逻辑
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
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
                        // 清理无效的关联关系
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
        Assert.notNull(attrGroup, "当前属性分组不存在!");
        Long categoryId = attrGroup.getCategoryId();

        // 2. 找出当前分类下，所有已经被任何属性组引用过的属性 id（包括当前分组）
        List<Long> usedAttrIds = attrAttrgroupRelationDao.selectList(
                        new LambdaQueryWrapper<AttrAttrgroupRelationEntity>()
                ).stream()
                .map(AttrAttrgroupRelationEntity::getAttrId)
                .distinct()
                .collect(Collectors.toList());

        // 3. 构建查询条件
        LambdaQueryWrapper<AttrEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AttrEntity::getCategoryId, categoryId)     // 当前分类
                .eq(AttrEntity::getAttrType, 1);          // 基本属性

        if (!CollectionUtils.isEmpty(usedAttrIds)) {
            wrapper.notIn(AttrEntity::getAttrId, usedAttrIds); // 未被任何组引用
        }

        return this.list(wrapper);
    }


    @Transactional
    @Override
    public void saveBaseAttr(AttrSaveRequestVO req) {
        AttrEntity entity = buildAttrEntity(req, 1); // 规格参数
        this.save(entity);

        // 处理分组关联关系
        handleAttrGroupRelation(req, entity.getAttrId());
    }

    @Transactional
    @Override
    public void updateBaseAttr(AttrSaveRequestVO req) {
        AttrEntity entity = buildAttrEntity(req, 1); // 规格参数
        this.updateById(entity);

        // 处理分组关联关系
        handleAttrGroupRelation(req, req.getAttrId());
    }

    // ==================== 销售属性相关方法实现 ====================

    @Override
    public PageUtils querySaleAttrPage(Map<String, Object> params) {
        params.put("attr_type", 0);
        return queryAttrPage(params);
    }

    @Transactional
    @Override
    public void saveSaleAttr(AttrSaveRequestVO req) {
        AttrEntity entity = buildAttrEntity(req, 0); // 销售属性
        this.save(entity);
        // 销售属性不需要处理分组关联关系
    }

    @Transactional
    @Override
    public void updateSaleAttr(AttrSaveRequestVO req) {
        AttrEntity entity = buildAttrEntity(req, 0); // 销售属性
        this.updateById(entity);
        // 销售属性不需要处理分组关联关系
    }

    /**
     * 构建属性实体对象
     */
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

    /**
     * 处理属性分组关联关系
     */
    private void handleAttrGroupRelation(AttrSaveRequestVO req, Long attrId) {
        // 只有当明确传递了attrGroupId时才更新关联关系
        if (req.getAttrGroupId() != null && !req.getAttrGroupId().trim().isEmpty()) {
            Long attrGroupId = Long.valueOf(req.getAttrGroupId());
            // 验证分组是否存在
            AttrGroupEntity attrGroupEntity = attrGroupService.getById(attrGroupId);
            if (attrGroupEntity != null) {
                // 先删除原有的关联关系
                attrAttrgroupRelationDao.delete(
                        new QueryWrapper<AttrAttrgroupRelationEntity>()
                                .eq("attr_id", attrId)
                );

                // 创建新的关联关系
                AttrAttrgroupRelationEntity relation = new AttrAttrgroupRelationEntity();
                relation.setAttrId(attrId);
                relation.setAttrGroupId(attrGroupId);
                relation.setAttrSort(0);
                attrAttrgroupRelationDao.insert(relation);
            }
        }
    }

    // ==================== 删除相关方法实现 ====================

    @Transactional
    @Override
    public void deleteAttrWithRelations(Long attrId) {
        // 1. 删除属性分组关联关系
        attrAttrgroupRelationDao.delete(
                new QueryWrapper<AttrAttrgroupRelationEntity>()
                        .eq("attr_id", attrId)
        );

        // 2. 删除属性本身
        this.removeById(attrId);
    }

    @Transactional
    @Override
    public void deleteAttrsWithRelations(Long[] attrIds) {
        // 1. 批量删除属性分组关联关系
        attrAttrgroupRelationDao.delete(
                new QueryWrapper<AttrAttrgroupRelationEntity>()
                        .in("attr_id", Arrays.asList(attrIds))
        );

        // 2. 批量删除属性本身
        this.removeByIds(Arrays.asList(attrIds));
    }
}