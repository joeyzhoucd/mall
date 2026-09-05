package com.mall.order.service;

import com.baomidou.mybatisplus.spring.service.IService;
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

    boolean shipOrder(String orderSn, String deliveryCompany, String deliverySn);

    boolean receiveOrder(String orderSn);

    boolean startAfterSale(String orderSn, String note);

    boolean finishAfterSale(String orderSn, String note);

    OrderEntity getOrderBySn(String orderSn);

    /**
     * 后台用的订单详情：订单 + 明细 + 操作记录，一次取全。
     *
     * 三样在后台是一个东西 —— 打开订单要同时看到「买了什么」和「经历过什么」。
     * 分三次请求除了多两次往返，还<b>可能拼出不一致的画面</b>：
     * 三次之间订单状态变了（比如刚好被发货），界面上会出现
     * 「状态是待发货，但操作记录里已经有发货那一条」。
     *
     * @return 订单不存在时返回 {@code null}，由调用方决定怎么表达（404 还是业务错误）
     */
    com.mall.order.vo.OrderDetailVo getOrderDetail(String orderSn);

    void recordOperateHistory(com.mall.common.to.OrderOperateTo operateTo);

    /**
     * 秒杀异步建单：消费 seckill.order.queue 里的消息时调用，单品单件、秒杀价快照，
     * 复用普通下单同一套锁库存/超时自动关单逻辑。orderSn 用 "SK"+本地消息表id 拼出来，
     * 天然幂等——同一条消息重复投递时能查到订单已存在，直接跳过重建。
     */
    void createSeckillOrder(com.mall.common.to.SeckillOrderTo seckillOrderTo);
}
