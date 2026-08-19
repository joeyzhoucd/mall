package com.mall.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.order.entity.OrderEntity;
import com.mall.order.vo.OrderConfirmVo;
import com.mall.order.vo.OrderSubmitVo;
import com.mall.order.vo.SubmitOrderResponseVo;

import java.util.Map;


public interface OrderService extends IService<OrderEntity> {

    PageUtils queryPage(Map<String, Object> params);

    OrderConfirmVo confirmOrder();

    SubmitOrderResponseVo submitOrder(OrderSubmitVo submitVo);

    void closeOrder(String orderSn);

    void payOrderSuccess(String orderSn);

    OrderEntity getOrderBySn(String orderSn);

    void recordOperateHistory(com.mall.common.to.OrderOperateTo operateTo);
}
