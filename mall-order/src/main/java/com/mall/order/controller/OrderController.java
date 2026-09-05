package com.mall.order.controller;

import com.mall.common.constant.ErrorCode;
import com.mall.common.constant.OrderStatus;
import com.mall.common.utils.R;
import com.mall.order.entity.OrderEntity;
import com.mall.order.service.OrderService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
@RequestMapping("order/order")
public class OrderController {
    @Autowired
    private OrderService orderService;

    @GetMapping("/status/{orderSn}")
    public R getOrderStatus(@PathVariable("orderSn") String orderSn) {
        OrderEntity order = orderService.getOrderBySn(orderSn);
        if (order == null) {
            return R.error(ErrorCode.ORDER_NOT_FOUND);
        }
        return R.ok()
                .put("order", order)
                .put("status", order.getStatus())
                .put("statusName", OrderStatus.valueOfCode(order.getStatus()))
                .put("allowedTargets", OrderStatus.allowedTargets(order.getStatus()));
    }

    /**
     * 后台的订单列表。
     *
     * <h3>为什么现在才有</h3>
     * OrderService.queryPage 一直存在，但<b>控制器上没有对应的入口</b> ——
     * 同服务的其它控制器（订单项、退货申请、支付、退款）都还留着完整的生成器 CRUD，
     * 唯独主实体这个被拿掉了。所以订单管理不只是缺前端页面，
     * 连查询入口都得补。
     *
     * <h3>只读</h3>
     * 这里不提供 save / update / delete。订单不该被后台直接改字段：
     * 状态迁移有状态机（OrderStatus 的 TRANSITION_TABLE）管着，
     * 发货、收货、售后各有自己的业务入口，它们会同时写操作记录、
     * 发 outbox 消息、解锁库存。绕过它们直接改一行数据，
     * 那些副作用一个都不会发生，而界面上看起来是成功的。
     *
     * 支持的筛选：orderSn（精确）、key（收件人/会员名/电话，模糊）、
     * status、memberId、createTimeFrom / createTimeTo。
     */
    @GetMapping("/list")
    public R list(@RequestParam Map<String, Object> params) {
        return R.ok().put("page", orderService.queryPage(params));
    }

    /**
     * 后台的订单详情：订单 + 明细 + 操作记录，一次取全。
     *
     * 用 orderSn 而不是 id 定位：订单号是对外的那个标识
     * （客服和客户之间报的、支付回调带的都是它），
     * 而 id 只在库里有意义。后台按客户报来的单号查，走的自然是 orderSn。
     */
    @GetMapping("/detail/{orderSn}")
    public R detail(@PathVariable("orderSn") String orderSn) {
        com.mall.order.vo.OrderDetailVo vo = orderService.getOrderDetail(orderSn);
        if (vo == null) {
            // 业务错误而不是 500：查一个不存在的订单号是很正常的操作
            // （单号输错、订单已被清理），不该进 ERROR 日志、也不该污染错误率指标。
            return R.error("订单不存在：" + orderSn);
        }
        return R.ok().put("data", vo);
    }

    @GetMapping("/statuses")
    public R getOrderStatuses() {
        return R.ok()
                .put("statuses", OrderStatus.definitions())
                .put("transitionTable", OrderStatus.transitionTable())
                .put("transitions", OrderStatus.transitions());
    }

    @PostMapping("/ship")
    public R shipOrder(@RequestBody ShipOrderRequest request) {
        if (request == null || StringUtils.isAnyBlank(request.orderSn(), request.deliveryCompany(), request.deliverySn())) {
            return R.error(ErrorCode.REQUEST_FAILED);
        }
        return transitionResult(orderService.shipOrder(request.orderSn(), request.deliveryCompany(), request.deliverySn()));
    }

    @PostMapping("/receive")
    public R receiveOrder(@RequestBody OrderSnRequest request) {
        if (request == null || StringUtils.isBlank(request.orderSn())) {
            return R.error(ErrorCode.REQUEST_FAILED);
        }
        return transitionResult(orderService.receiveOrder(request.orderSn()));
    }

    @PostMapping("/complete")
    public R completeOrder(@RequestBody OrderSnRequest request) {
        return receiveOrder(request);
    }

    @PostMapping("/after-sale/start")
    public R startAfterSale(@RequestBody AfterSaleRequest request) {
        if (request == null || StringUtils.isBlank(request.orderSn())) {
            return R.error(ErrorCode.REQUEST_FAILED);
        }
        return transitionResult(orderService.startAfterSale(request.orderSn(), request.note()));
    }

    @PostMapping("/after-sale/finish")
    public R finishAfterSale(@RequestBody AfterSaleRequest request) {
        if (request == null || StringUtils.isBlank(request.orderSn())) {
            return R.error(ErrorCode.REQUEST_FAILED);
        }
        return transitionResult(orderService.finishAfterSale(request.orderSn(), request.note()));
    }

    @PostMapping("/operate")
    public R recordOperate(@RequestBody com.mall.common.to.OrderOperateTo operateTo) {
        orderService.recordOperateHistory(operateTo);
        return R.ok();
    }

    private R transitionResult(boolean updated) {
        return updated ? R.ok() : R.error(ErrorCode.ORDER_STATUS_TRANSITION_ILLEGAL);
    }

    public record ShipOrderRequest(String orderSn, String deliveryCompany, String deliverySn) {
    }

    public record OrderSnRequest(String orderSn) {
    }

    public record AfterSaleRequest(String orderSn, String note) {
    }
}
