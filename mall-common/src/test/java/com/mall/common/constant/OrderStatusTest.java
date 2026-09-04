package com.mall.common.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStatusTest {

    @Test
    void allowsKnownForwardTransitions() {
        assertTrue(OrderStatus.canTransit(OrderStatus.NEW, OrderStatus.PAYED));
        assertTrue(OrderStatus.canTransit(OrderStatus.NEW, OrderStatus.CLOSED));
        assertTrue(OrderStatus.canTransit(OrderStatus.PAYED, OrderStatus.SENT));
        assertTrue(OrderStatus.canTransit(OrderStatus.SENT, OrderStatus.RECEIVED));
        assertTrue(OrderStatus.canTransit(OrderStatus.PAYED, OrderStatus.SERVICING));
        assertTrue(OrderStatus.canTransit(OrderStatus.SERVICING, OrderStatus.SERVICED));
    }

    @Test
    void rejectsTerminalAndBackwardTransitions() {
        assertFalse(OrderStatus.canTransit(OrderStatus.CLOSED, OrderStatus.PAYED));
        assertFalse(OrderStatus.canTransit(OrderStatus.SERVICED, OrderStatus.PAYED));
        assertFalse(OrderStatus.canTransit(OrderStatus.PAYED, OrderStatus.NEW));
        assertFalse(OrderStatus.canTransit(OrderStatus.RECEIVED, OrderStatus.SENT));
        assertFalse(OrderStatus.canTransit(OrderStatus.NEW, OrderStatus.NEW));
        assertFalse(OrderStatus.canTransit(null, OrderStatus.PAYED));
        assertFalse(OrderStatus.canTransit(OrderStatus.NEW, null));
    }

    @Test
    void exposesTerminalStates() {
        assertTrue(OrderStatus.isTerminal(OrderStatus.CLOSED));
        assertTrue(OrderStatus.isTerminal(OrderStatus.SERVICED));
        assertFalse(OrderStatus.isTerminal(OrderStatus.NEW));
        assertFalse(OrderStatus.isTerminal(null));
    }
}
