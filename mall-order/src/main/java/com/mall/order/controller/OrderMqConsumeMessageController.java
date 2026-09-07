package com.mall.order.controller;

import com.mall.common.utils.R;
import com.mall.order.service.OrderMqConsumeMessageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 订单侧的 MQ 消费幂等记录（oms_mq_consume_message）。
 *
 * <h3>只有读，没有写 —— 这是刻意的</h3>
 * 这张表不是日志，它<b>是幂等判断的依据本身</b>。
 * OrderMqConsumeMessageServiceImpl.consumeOnce 的第一步就是往这里插一条
 * （靠 uk_order_mq_consume_key 唯一键抢占），插不进去就说明这条消息
 * 已经被处理过，直接跳过。
 * <p>
 * 所以后台如果能删一行，代价是那条消息<b>会被完整地重新执行一遍</b>：
 * 订单侧对应重复关单、重复建秒杀单。而操作者在界面上看到的只是
 * 「删掉了一条状态为失败的记录」—— 后果和操作看起来完全不相称。
 * <p>
 * 想让一条失败的消息重跑，正确的入口是死信队列那一侧
 * （MqDlqService.replay），它把消息重新投回源交换机，走完整的消费链路，
 * 幂等判断照常生效，也会留下痕迹。
 *
 * <h3>路径为什么是 /order/mq-consume 而不是生成器风格的 /order/ordermqconsumemessage</h3>
 * 这个控制器是手写的（这张表原本没有任何对外入口），
 * 路径按"运维视角"取名而不是按表名 —— 后台菜单上写的是「消费幂等」。
 * 网关侧对应 /api/order/**（2026-09-06 新加，见 gateway 的 admin_order_route）。
 */
@RestController
@RequestMapping("/order/mq-consume")
public class OrderMqConsumeMessageController {

    private final OrderMqConsumeMessageService orderMqConsumeMessageService;

    public OrderMqConsumeMessageController(OrderMqConsumeMessageService orderMqConsumeMessageService) {
        this.orderMqConsumeMessageService = orderMqConsumeMessageService;
    }

    /**
     * 分页查询。筛选见 MqAdminQuery.consume：
     * {@code status} / {@code consumerGroup} / {@code businessType} /
     * {@code messageKey}（精确），{@code key}（message_key 模糊）。
     */
    @GetMapping("/list")
    public R list(@RequestParam Map<String, Object> params) {
        return R.ok().put("page", orderMqConsumeMessageService.queryPage(params));
    }

    /** 各状态条数。给页面顶部汇总条用，让「有 N 条失败」不需要主动去翻才能看见。 */
    @GetMapping("/stats")
    public R stats() {
        return R.ok().put("counts", orderMqConsumeMessageService.statusCounts());
    }
}
