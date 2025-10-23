package com.mall.ware.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.ware.dao.WareInfoDao;
import com.mall.ware.entity.WareInfoEntity;
import com.mall.ware.service.WareInfoService;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service("wareInfoService")
public class WareInfoServiceImpl extends ServiceImpl<WareInfoDao, WareInfoEntity> implements WareInfoService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<WareInfoEntity> wrapper = new QueryWrapper<>();

        // Search by key
        String key = (String) params.get("key");
        if (StringUtils.isNotBlank(key)) {
            wrapper.and(w -> w.eq("id", key).or().like("name", key).or().like("address", key));
        }

        wrapper.orderByDesc("id"); // Sort by ID descending

        IPage<WareInfoEntity> page = this.page(
                new Query<WareInfoEntity>().getPage(params),
                wrapper
        );

        return new PageUtils(page);
    }
}