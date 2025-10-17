/**
 * Copyright (c) 2018 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.datasource.config;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * å¤šæ•°æ®æºä¸Šä¸‹æ–‡
 *
 * @author Mark sunlightcs@gmail.com
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
     * èŽ·å¾—å½“å‰çº¿ç¨‹æ•°æ®æº
     *
     * @return æ•°æ®æºåç§°
     */
    public static String peek() {
        return CONTEXT_HOLDER.get().peek();
    }

    /**
     * è®¾ç½®å½“å‰çº¿ç¨‹æ•°æ®æº
     *
     * @param dataSource æ•°æ®æºåç§°
     */
    public static void push(String dataSource) {
        CONTEXT_HOLDER.get().push(dataSource);
    }

    /**
     * æ¸…ç©ºå½“å‰çº¿ç¨‹æ•°æ®æº
     */
    public static void poll() {
        Deque<String> deque = CONTEXT_HOLDER.get();
        deque.poll();
        if (deque.isEmpty()) {
            CONTEXT_HOLDER.remove();
        }
    }

}
