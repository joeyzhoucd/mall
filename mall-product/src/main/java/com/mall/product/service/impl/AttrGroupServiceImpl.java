package com.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.product.dao.AttrAttrgroupRelationDao;
import com.mall.product.dao.AttrDao;
import com.mall.product.dao.AttrGroupDao;
import com.mall.product.dao.CategoryDao;
import com.mall.product.entity.AttrAttrgroupRelationEntity;
import com.mall.product.entity.AttrEntity;
import com.mall.product.entity.AttrGroupEntity;
import com.mall.product.entity.CategoryEntity;
import com.mall.product.service.AttrGroupService;
import org.apache.commons.lang3.StringUtils;
import com.mall.product.vo.AttrGroupResponseVO;
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

    @Autowired
    CategoryDao categoryDao;

    /**
     * 分页查询属性分组。
     *
     * <h3>补上了两件事</h3>
     * <b>1. key 检索</b>：原实现只认 categoryId，前端传 key 过去会被静默忽略 ——
     * 搜索框看起来能用，但结果永远是全量。和 BrandServiceImpl 之前一模一样的坑。
     *
     * <b>2. 确定的排序</b>：原实现没有任何 ORDER BY。MySQL 不保证无序查询的行顺序，
     * 而分页的每一页都是一次独立查询，于是行会在页与页之间重复或漏掉。
     * 排序落到唯一列 attr_group_id 上，全序才是确定的
     * （只按 sort 排不够 —— sort 相同的行之间顺序依然未定义）。
     *
     * 这个问题在本仓库是<b>系统性</b>的：55 个分页查询里有 50 个排序不确定，
     * 因为生成器模板从来没有生成过 ORDER BY。这里只修顺手用到的这个。
     */
    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        LambdaQueryWrapper<AttrGroupEntity> lqw = Wrappers.lambdaQuery();

        Object categoryId = params.get("categoryId");
        lqw.eq(categoryId != null, AttrGroupEntity::getCategoryId, categoryId);

        String key = (String) params.get("key");
        if (StringUtils.isNotBlank(key)) {
            lqw.and(w -> w.eq(AttrGroupEntity::getAttrGroupId, key)
                    .or().like(AttrGroupEntity::getAttrGroupName, key));
        }

        lqw.orderByAsc(AttrGroupEntity::getSort).orderByAsc(AttrGroupEntity::getAttrGroupId);

        IPage<AttrGroupEntity> page = this.page(new Query<AttrGroupEntity>().getPage(params), lqw);
        IPage<AttrGroupResponseVO> voPage = page.convert(entity -> {
            AttrGroupResponseVO vo = new AttrGroupResponseVO();
            BeanUtils.copyProperties(entity, vo);
            return vo;
        });
        fillCategoryNames(voPage.getRecords());
        return new PageUtils(voPage);
    }

    /**
     * 给这一页的分组补上分类名。
     *
     * <h3>【一次批量查询，不是逐行 selectById】</h3>
     * 界面上原来显示的是 {@code #3} 这种原始分类 id，因为这个接口只返回
     * {@code categoryId}。前端为此留了注释，说"显示 id 而不是去逐行查名字" ——
     * 那个取舍在"N+1"和"显示 id"之间选了后者，是合理的，但还有第三个选项：
     * <b>把这一页用到的分类一次全查回来</b>。
     * <p>
     * 所以这里的成本是 1 次按主键的 IN 查询，和页大小无关。
     * 同模块的 {@code AttrServiceImpl.queryAttrPage} 是在循环里逐行
     * {@code categoryDao.selectById}，那才是真正的 N+1，<b>不要照抄那个写法</b>。
     * <p>
     * 缓存刻意不加：分类名极少变，但加一层缓存就多一处失效逻辑，
     * 而收益只是省掉一次主键查询 —— 不值那个复杂度。
     * <p>
     * 分类不存在时 {@code categoryName} 留 null，前端回落显示 id。
     * "分组挂在已删除的分类下"是脏数据，但它不该表现成"这一行什么都没有"。
     */
    private void fillCategoryNames(List<AttrGroupResponseVO> records) {
        if (CollectionUtils.isEmpty(records)) {
            return;
        }
        List<Long> categoryIds = records.stream()
                .map(AttrGroupResponseVO::getCategoryId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (categoryIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameById = categoryDao.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(CategoryEntity::getCatId, CategoryEntity::getName,
                        // 主键查询不可能撞键，给个合并函数只是为了 toMap 不在意外情况下抛
                        (a, b) -> a));
        records.forEach(vo -> vo.setCategoryName(nameById.get(vo.getCategoryId())));
    }

    @Override
    public List<AttrGroupWithAttrVO> getAttrGroupWithAttrs(Long categoryId) {
        // Query attr groups by category
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