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

import java.util.Date;
import java.util.List;
import java.util.Map;


@Service("wareOrderTaskDetailService")
public class WareOrderTaskDetailServiceImpl extends ServiceImpl<WareOrderTaskDetailDao, WareOrderTaskDetailEntity> implements WareOrderTaskDetailService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<WareOrderTaskDetailEntity> page = this.page(
                new Query<WareOrderTaskDetailEntity>().getPage(params),
                new QueryWrapper<WareOrderTaskDetailEntity>().orderByDesc("id")
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
    public List<WareOrderTaskDetailEntity> listRetryingDetails(Integer lockStatus, Integer retryLimit, Date lockedBefore) {
        return this.list(retryingQuery(lockStatus, retryLimit, lockedBefore));
    }

    /**
     * 明细表没有时间列，按所属任务的 create_time 判断锁了多久。
     * <p>截止时间由调用方在 Java 里算好、作为参数传入，<b>不要改成 SQL 里的 NOW()</b>：
     * create_time 是 mall-ware 用 {@code new Date()} 经 serverTimezone=Asia/Shanghai 写的上海墙钟，
     * 而 MySQL 服务器是 UTC —— NOW() - INTERVAL 10 MINUTE 会差出 8 小时。参数走同一个驱动换算，两边一致。
     */
    static QueryWrapper<WareOrderTaskDetailEntity> retryingQuery(Integer lockStatus, Integer retryLimit, Date lockedBefore) {
        QueryWrapper<WareOrderTaskDetailEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("lock_status", lockStatus);
        wrapper.and(w -> w.isNull("retry_count").or().lt("retry_count", retryLimit));
        wrapper.apply("task_id IN (SELECT id FROM wms_ware_order_task WHERE create_time < {0})", lockedBefore);
        return wrapper;
    }

    @Override
    public List<WareOrderTaskDetailEntity> listByLockStatus(Integer lockStatus) {
        return this.list(new QueryWrapper<WareOrderTaskDetailEntity>().eq("lock_status", lockStatus));
    }

}
