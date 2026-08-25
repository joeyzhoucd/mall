package com.mall.common.constant;

public class SeckillMessageStatus {
    public static final int PENDING = 0;
    public static final int SENT = 1;
    public static final int SEND_FAILED = 2;
    public static final int ORDER_CREATED = 3;
    /** 抢到但一直没选地址，超过对账任务的宽限期，判定放弃、释放库存。 */
    public static final int EXPIRED = 4;

    private SeckillMessageStatus() {
    }
}
