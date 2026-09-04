package com.mall.common.constant;

public class MqConstants {
    public static final String CONSUMER_DEAD_LETTER_EXCHANGE = "mall-consumer-dlx";

    public static final String ORDER_EVENT_EXCHANGE = "order-event-exchange";
    public static final String ORDER_DELAY_QUEUE = "order.delay.queue";
    public static final String ORDER_RELEASE_QUEUE = "order.release.order.queue";
    public static final String ORDER_RELEASE_DLQ = "order.release.order.dlq";
    public static final String ORDER_CREATE_ROUTING_KEY = "order.create.order";
    public static final String ORDER_RELEASE_ROUTING_KEY = "order.release.order";
    public static final String ORDER_RELEASE_DLQ_ROUTING_KEY = "dlq.order.release.order";

    public static final String STOCK_RELEASE_EXCHANGE = "stock-release-exchange";
    public static final String STOCK_RELEASE_QUEUE = "stock.release.stock.queue";
    public static final String STOCK_DEDUCT_QUEUE = "stock.deduct.queue";
    public static final String STOCK_FAIL_QUEUE = "stock.fail.queue";
    public static final String STOCK_RELEASE_DLQ = "stock.release.stock.dlq";
    public static final String STOCK_DEDUCT_DLQ = "stock.deduct.dlq";
    public static final String STOCK_FAIL_DLQ = "stock.fail.dlq";
    public static final String STOCK_RELEASE_ROUTING_KEY = "stock.release";
    public static final String STOCK_DEDUCT_ROUTING_KEY = "stock.deduct";
    public static final String STOCK_FAIL_ROUTING_KEY = "stock.fail";
    public static final String STOCK_RELEASE_DLQ_ROUTING_KEY = "dlq.stock.release";
    public static final String STOCK_DEDUCT_DLQ_ROUTING_KEY = "dlq.stock.deduct";
    public static final String STOCK_FAIL_DLQ_ROUTING_KEY = "dlq.stock.fail";

    public static final String SECKILL_EVENT_EXCHANGE = "seckill-event-exchange";
    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ORDER_DLQ = "seckill.order.dlq";
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order.create";
    public static final String SECKILL_ORDER_DLQ_ROUTING_KEY = "dlq.seckill.order";

    private MqConstants() {
    }
}

