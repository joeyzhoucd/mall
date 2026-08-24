package com.mall.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.order.entity.OrderEntity;
import com.mall.order.vo.MemberAddressVo;
import com.mall.order.vo.OrderConfirmVo;
import com.mall.order.vo.OrderSubmitVo;
import com.mall.order.vo.SubmitOrderResponseVo;

import java.util.Map;


public interface OrderService extends IService<OrderEntity> {

    PageUtils queryPage(Map<String, Object> params);

    OrderConfirmVo confirmOrder();

    boolean saveAddress(MemberAddressVo addressVo);

    SubmitOrderResponseVo submitOrder(OrderSubmitVo submitVo);

    void closeOrder(String orderSn);

    void payOrderSuccess(String orderSn);

    OrderEntity getOrderBySn(String orderSn);

    void recordOperateHistory(com.mall.common.to.OrderOperateTo operateTo);

    /**
     * 秒杀异步建单：消费 seckill.order.queue 里的消息时调用，单品单件、秒杀价快照，
     * 复用普通下单同一套锁库存/超时自动关单逻辑。orderSn 用 "SK"+本地消息表id 拼出来，
     * 天然幂等——同一条消息重复投递时能查到订单已存在，直接跳过重建。
     */
    void createSeckillOrder(com.mall.common.to.SeckillOrderTo seckillOrderTo);
}
