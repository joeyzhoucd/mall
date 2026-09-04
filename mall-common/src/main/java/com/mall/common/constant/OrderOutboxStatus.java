package com.mall.common.constant;

public class OrderOutboxStatus {
    public static final int PENDING = OutboxMessageStatus.PENDING;
    public static final int SENDING = OutboxMessageStatus.SENDING;
    public static final int SENT = OutboxMessageStatus.SENT;
    public static final int FAILED = OutboxMessageStatus.FAILED;
    public static final int DEAD = OutboxMessageStatus.DEAD;

    private OrderOutboxStatus() {
    }

    public static boolean canDispatch(Integer status) {
        return OutboxMessageStatus.canDispatch(status);
    }
}
