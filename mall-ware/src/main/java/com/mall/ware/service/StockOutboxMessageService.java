package com.mall.ware.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.ware.entity.StockOutboxMessageEntity;

import java.util.Map;

public interface StockOutboxMessageService extends IService<StockOutboxMessageEntity> {

    StockOutboxMessageEntity enqueue(String messageKey,
                                     String businessType,
                                     String businessKey,
                                     String exchange,
                                     String routingKey,
                                     Object payload);

    int publishReadyMessages();

    boolean resend(Long id);

    boolean markDead(Long id, String reason);

    void markSent(Long id);

    void markFailed(Long id, String reason);

    PageUtils queryPage(Map<String, Object> params);

    /**
     * 各状态各有多少条。给后台页面顶部的汇总条用。
     *
     * <p>返回 状态码 → 条数，<b>只包含实际出现过的状态</b>。
     * 和 OrderOutboxMessageService.statusCounts 是同一个约定。
     */
    Map<Integer, Long> statusCounts();
}
