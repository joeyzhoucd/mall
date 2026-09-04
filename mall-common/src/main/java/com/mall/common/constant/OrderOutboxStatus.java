package com.mall.common.constant;

public class OrderOutboxStatus {
    public static final int PENDING = 0;
    public static final int SENDING = 1;
    public static final int SENT = 2;
    public static final int FAILED = 3;
    public static final int DEAD = 4;

    private OrderOutboxStatus() {
    }

    public static boolean canDispatch(Integer status) {
        return status != null && (status == PENDING || status == FAILED);
    }
}
