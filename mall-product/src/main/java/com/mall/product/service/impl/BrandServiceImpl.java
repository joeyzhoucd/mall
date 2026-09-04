package com.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.product.dao.BrandDao;
import com.mall.product.entity.BrandEntity;
import com.mall.product.service.BrandService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Map;


@Service("brandService")
public class BrandServiceImpl extends ServiceImpl<BrandDao, BrandEntity> implements BrandService {

    /**
     * 分页查询品牌，支持关键字检索。
     *
     * <h3>这里原本有两个问题</h3>
     * <b>1. key 参数被完全忽略</b>——原实现传的是一个空的 QueryWrapper。
     * 前端传 key 过去不会报错，只是筛选悄悄不生效，返回的还是全量第一页。
     * 之所以一直没人发现，是因为旧后台的品牌页压根没有搜索框
     * （基线里 brand.vue 的动作列表没有「查询」）。
     *
     * <b>2. 没有 ORDER BY</b>——这个更要紧。MySQL 不保证无序查询的行顺序，
     * 所以「第 1 页」和「第 2 页」是两次独立的无序查询，
     * 同一行可能在两页里都出现，也可能一页都不出现。
     * 数据少于一页时完全看不出来，等数据一多就变成「翻页时莫名丢记录」，
     * 而那时候没人会想到是缺了一个 ORDER BY。
     *
     * 排序用 sort 升序 + brand_id 升序：sort 是这张表本来就有的排序列，
     * 而 brand_id 是唯一的，用它兜底才能保证顺序<b>完全确定</b>——
     * 只按 sort 排，sort 相同的行之间顺序仍然是未定义的。
     *
     * 检索字段跟 WareInfoServiceImpl 的既有写法一致：id 精确 + 名称模糊，
     * 另加 first_letter（品牌页按首字母找是常见操作）。
     */
    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<BrandEntity> page = this.page(
                new Query<BrandEntity>().getPage(params),
                buildQueryWrapper(params)
        );
        return new PageUtils(page);
    }

    /**
     * 单独抽出来是为了<b>可测</b>：queryPage 要连数据库，而这一段纯粹是条件拼装。
     * 对应的测试见 BrandQueryWrapperTest —— 它守的是上面注释里那两个坑，
     * 尤其是 ORDER BY：那种 bug 不报错、数据少时也看不出来，
     * 只有一条会失败的测试能拦住「顺手删掉排序」。
     */
    QueryWrapper<BrandEntity> buildQueryWrapper(Map<String, Object> params) {
        QueryWrapper<BrandEntity> wrapper = new QueryWrapper<>();

        String key = (String) params.get("key");
        if (StringUtils.isNotBlank(key)) {
            wrapper.and(w -> w.eq("brand_id", key)
                    .or().like("name", key)
                    .or().like("first_letter", key));
        }

        wrapper.orderByAsc("sort").orderByAsc("brand_id");

        return wrapper;
    }

}
