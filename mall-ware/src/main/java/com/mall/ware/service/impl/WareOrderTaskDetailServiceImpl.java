package com.mall.ware.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.ware.dao.WareOrderTaskDetailDao;
import com.mall.ware.entity.WareOrderTaskDetailEntity;
import com.mall.ware.service.WareOrderTaskDetailService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;


@Service("wareOrderTaskDetailService")
public class WareOrderTaskDetailServiceImpl extends ServiceImpl<WareOrderTaskDetailDao, WareOrderTaskDetailEntity> implements WareOrderTaskDetailService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<WareOrderTaskDetailEntity> page = this.page(
                new Query<WareOrderTaskDetailEntity>().getPage(params),
                new QueryWrapper<>()
        );

        return new PageUtils(page);
    }

    @Override
    public WareOrderTaskDetailEntity getByTaskIdAndSkuId(Long taskId, Long skuId) {
        return this.getOne(new QueryWrapper<WareOrderTaskDetailEntity>()
                .eq("task_id", taskId)
                .eq("sku_id", skuId));
    }

    @Override
    public List<WareOrderTaskDetailEntity> listRetryingDetails(Integer lockStatus, Integer retryLimit) {
        QueryWrapper<WareOrderTaskDetailEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("lock_status", lockStatus);
        wrapper.and(w -> w.isNull("retry_count").or().lt("retry_count", retryLimit));
        return this.list(wrapper);
    }

    @Override
    public List<WareOrderTaskDetailEntity> listByLockStatus(Integer lockStatus) {
        return this.list(new QueryWrapper<WareOrderTaskDetailEntity>().eq("lock_status", lockStatus));
    }

}
