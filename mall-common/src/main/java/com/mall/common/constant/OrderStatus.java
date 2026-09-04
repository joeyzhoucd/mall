package com.mall.common.constant;

public class OrderStatus {
    public static final int NEW = 0;
    public static final int PAYED = 1;
    public static final int SENT = 2;
    public static final int RECEIVED = 3;
    public static final int CLOSED = 4;
    public static final int SERVICING = 5;
    public static final int SERVICED = 6;

    public static final int WAITING_PAY = NEW;
    public static final int WAITING_DELIVERY = PAYED;
    public static final int SHIPPED = SENT;
    public static final int COMPLETED = RECEIVED;
    public static final int CANCELLED = CLOSED;
    public static final int REFUNDING = SERVICING;
    public static final int REFUNDED = SERVICED;

    private OrderStatus() {
    }

    public static boolean canTransit(Integer from, Integer to) {
        if (from == null || to == null || from.equals(to)) {
            return false;
        }
        switch (from) {
            case NEW:
                return to == PAYED || to == CLOSED;
            case PAYED:
                return to == SENT || to == SERVICING;
            case SENT:
                return to == RECEIVED || to == SERVICING;
            case RECEIVED:
                return to == SERVICING;
            case SERVICING:
                return to == SERVICED || to == PAYED || to == SENT || to == RECEIVED;
            default:
                return false;
        }
    }

    public static boolean isTerminal(Integer status) {
        return status != null && (status == CLOSED || status == SERVICED);
    }
}

