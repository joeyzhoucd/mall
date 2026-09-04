package com.mall.common.constant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

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
        assertFalse(OrderStatus.canTransit(99, OrderStatus.PAYED));
    }

    @Test
    void exposesTerminalStates() {
        assertTrue(OrderStatus.isTerminal(OrderStatus.CLOSED));
        assertTrue(OrderStatus.isTerminal(OrderStatus.SERVICED));
        assertFalse(OrderStatus.isTerminal(OrderStatus.NEW));
        assertFalse(OrderStatus.isTerminal(null));
    }

    @Test
    void exposesCompleteStatusDefinitionsAndTransitionTable() {
        assertThat(OrderStatus.definitions())
                .extracting(OrderStatus.Definition::code)
                .containsExactly(
                        OrderStatus.NEW,
                        OrderStatus.PAYED,
                        OrderStatus.SENT,
                        OrderStatus.RECEIVED,
                        OrderStatus.CLOSED,
                        OrderStatus.SERVICING,
                        OrderStatus.SERVICED);

        assertThat(OrderStatus.transitionTable())
                .containsEntry(OrderStatus.NEW, java.util.List.of(OrderStatus.PAYED, OrderStatus.CLOSED))
                .containsEntry(OrderStatus.PAYED, java.util.List.of(OrderStatus.SENT, OrderStatus.SERVICING))
                .containsEntry(OrderStatus.CLOSED, java.util.List.of())
                .containsEntry(OrderStatus.SERVICED, java.util.List.of());
        assertThat(OrderStatus.valueOfCode(OrderStatus.SENT)).isEqualTo("SENT");
        assertThat(OrderStatus.valueOfCode(99)).isNull();
        assertThat(OrderStatus.allowedTargets(OrderStatus.SENT))
                .containsExactly(OrderStatus.RECEIVED, OrderStatus.SERVICING);
    }
}
