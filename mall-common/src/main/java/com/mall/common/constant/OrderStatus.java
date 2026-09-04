package com.mall.common.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    private static final Map<Integer, State> STATES = Arrays.stream(State.values())
            .collect(Collectors.toUnmodifiableMap(State::code, state -> state));

    private static final Map<Integer, Set<Integer>> TRANSITION_TABLE = buildTransitionTable();

    private OrderStatus() {
    }

    public enum State {
        NEW(OrderStatus.NEW, "NEW", "Waiting pay", false),
        PAYED(OrderStatus.PAYED, "PAYED", "Waiting delivery", false),
        SENT(OrderStatus.SENT, "SENT", "Shipped", false),
        RECEIVED(OrderStatus.RECEIVED, "RECEIVED", "Completed", false),
        CLOSED(OrderStatus.CLOSED, "CLOSED", "Closed", true),
        SERVICING(OrderStatus.SERVICING, "SERVICING", "After-sale processing", false),
        SERVICED(OrderStatus.SERVICED, "SERVICED", "After-sale completed", true);

        private final int code;
        private final String value;
        private final String label;
        private final boolean terminal;

        State(int code, String value, String label, boolean terminal) {
            this.code = code;
            this.value = value;
            this.label = label;
            this.terminal = terminal;
        }

        public int code() {
            return code;
        }

        public String value() {
            return value;
        }

        public String label() {
            return label;
        }

        public boolean terminal() {
            return terminal;
        }
    }

    public record Definition(int code, String value, String label, boolean terminal) {
    }

    public record Transition(int from, int to) {
    }

    public static List<Definition> definitions() {
        return Arrays.stream(State.values())
                .map(state -> new Definition(state.code(), state.value(), state.label(), state.terminal()))
                .toList();
    }

    public static Map<Integer, List<Integer>> transitionTable() {
        LinkedHashMap<Integer, List<Integer>> result = new LinkedHashMap<>();
        TRANSITION_TABLE.forEach((from, targets) -> result.put(from, List.copyOf(targets)));
        return Collections.unmodifiableMap(result);
    }

    public static List<Transition> transitions() {
        return TRANSITION_TABLE.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(to -> new Transition(entry.getKey(), to)))
                .toList();
    }

    public static List<Integer> allowedTargets(Integer from) {
        if (from == null) {
            return Collections.emptyList();
        }
        return List.copyOf(TRANSITION_TABLE.getOrDefault(from, Collections.emptySet()));
    }

    public static boolean canTransit(Integer from, Integer to) {
        if (from == null || to == null) {
            return false;
        }
        return TRANSITION_TABLE.getOrDefault(from, Collections.emptySet()).contains(to);
    }

    public static boolean isTerminal(Integer status) {
        if (status == null) {
            return false;
        }
        State state = STATES.get(status);
        return state != null && state.terminal();
    }

    public static boolean isKnown(Integer status) {
        if (status == null) {
            return false;
        }
        return STATES.containsKey(status);
    }

    public static String valueOfCode(Integer status) {
        if (status == null) {
            return null;
        }
        State state = STATES.get(status);
        return state == null ? null : state.value();
    }

    private static Map<Integer, Set<Integer>> buildTransitionTable() {
        LinkedHashMap<Integer, Set<Integer>> table = new LinkedHashMap<>();
        table.put(NEW, orderedSet(PAYED, CLOSED));
        table.put(PAYED, orderedSet(SENT, SERVICING));
        table.put(SENT, orderedSet(RECEIVED, SERVICING));
        table.put(RECEIVED, orderedSet(SERVICING));
        table.put(CLOSED, Collections.emptySet());
        table.put(SERVICING, orderedSet(SERVICED, PAYED, SENT, RECEIVED));
        table.put(SERVICED, Collections.emptySet());
        return Collections.unmodifiableMap(table);
    }

    private static Set<Integer> orderedSet(Integer... statuses) {
        LinkedHashSet<Integer> result = new LinkedHashSet<>(Arrays.asList(statuses));
        return Collections.unmodifiableSet(result);
    }
}

