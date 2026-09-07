package com.mall.order.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.order.entity.OrderOutboxMessageEntity;

import java.util.Map;

public interface OrderOutboxMessageService extends IService<OrderOutboxMessageEntity> {

    OrderOutboxMessageEntity enqueue(String messageKey,
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
     * <p>返回的是 状态码 → 条数，<b>只包含实际出现过的状态</b>
     * （一条都没有的状态不会出现在 map 里，而不是给 0）——
     * 让调用方自己决定「没有」该显示成 0 还是不显示。
     */
    Map<Integer, Long> statusCounts();
}
