package io.renren.datasource.config;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Dynamic context holder
 */
public class DynamicContextHolder {
    @SuppressWarnings("unchecked")
    private static final ThreadLocal<Deque<String>> CONTEXT_HOLDER = new ThreadLocal() {
        @Override
        protected Object initialValue() {
            return new ArrayDeque();
        }
    };

    /**
     * Peek current data source
     */
    public static String peek() {
        return CONTEXT_HOLDER.get().peek();
    }

    /**
     * Push data source
     */
    public static void push(String dataSource) {
        CONTEXT_HOLDER.get().push(dataSource);
    }

    /**
     * Poll data source
     */
    public static void poll() {
        Deque<String> deque = CONTEXT_HOLDER.get();
        deque.poll();
        if (deque.isEmpty()) {
            CONTEXT_HOLDER.remove();
        }
    }

}