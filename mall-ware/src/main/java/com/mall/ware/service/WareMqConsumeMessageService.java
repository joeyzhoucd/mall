package com.mall.ware.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.ware.entity.WareMqConsumeMessageEntity;

import java.util.Map;

public interface WareMqConsumeMessageService extends IService<WareMqConsumeMessageEntity> {

    boolean consumeOnce(String consumerGroup, String messageKey, String businessType, Runnable handler);

    /**
     * 消费幂等记录的分页查询。<b>只读</b>，理由同
     * OrderMqConsumeMessageService.queryPage：这张表就是幂等判据本身，
     * 手工删一行等于让那条消息重新执行一遍（重复扣库存），
     * 而界面上只表现为「清理了一条脏数据」。
     */
    PageUtils queryPage(Map<String, Object> params);

    /** 各状态各有多少条。给后台页面顶部的汇总条用，只包含实际出现过的状态。 */
    Map<Integer, Long> statusCounts();
}
