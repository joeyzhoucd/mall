package com.mall.order.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.order.entity.OrderMqConsumeMessageEntity;

import java.util.Map;

public interface OrderMqConsumeMessageService extends IService<OrderMqConsumeMessageEntity> {

    boolean consumeOnce(String consumerGroup, String messageKey, String businessType, Runnable handler);

    /**
     * 消费幂等记录的分页查询。<b>只读</b>。
     *
     * <h3>为什么后台对这张表没有任何写入口</h3>
     * 这张表是幂等的判据本身：一条记录存在且 status=SUCCESS，就意味着
     * 「这条消息已经处理过，再来一次要跳过」。手工删掉或改一行的后果是
     * 那条消息<b>会被重新执行一遍</b> —— 对应到业务上就是重复扣库存、
     * 重复建单。而界面上看起来只是「清理了一条脏数据」。
     * <p>
     * 真要重放，正确的入口是死信队列那一侧（MqDlqService.replay），
     * 它走的是完整的消费链路，幂等判断照样生效。
     */
    PageUtils queryPage(Map<String, Object> params);

    /** 各状态各有多少条。给后台页面顶部的汇总条用，只包含实际出现过的状态。 */
    Map<Integer, Long> statusCounts();
}
