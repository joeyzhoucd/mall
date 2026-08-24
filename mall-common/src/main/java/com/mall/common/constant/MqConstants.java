package com.mall.common.constant;

public class MqConstants {
    public static final String ORDER_EVENT_EXCHANGE = "order-event-exchange";
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ORDER_RELEASE_QUEUE = "order.release.order.queue";
    public static final String ORDER_CREATE_ROUTING_KEY = "order.create.order";
    public static final String ORDER_RELEASE_ROUTING_KEY = "order.release.order";

    public static final String STOCK_RELEASE_EXCHANGE = "stock-release-exchange";
    public static final String STOCK_RELEASE_QUEUE = "stock.release.stock.queue";
    public static final String STOCK_DEDUCT_QUEUE = "stock.deduct.queue";
    public static final String STOCK_FAIL_QUEUE = "stock.fail.queue";
    public static final String STOCK_RELEASE_ROUTING_KEY = "stock.release";
    public static final String STOCK_DEDUCT_ROUTING_KEY = "stock.deduct";
    public static final String STOCK_FAIL_ROUTING_KEY = "stock.fail";

    public static final String SECKILL_EVENT_EXCHANGE = "seckill-event-exchange";
    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order.create";

    private MqConstants() {
    }
}

