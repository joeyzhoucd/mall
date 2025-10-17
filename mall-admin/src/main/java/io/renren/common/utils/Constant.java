/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 * <p>
 * https://www.renren.io
 * <p>
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.common.utils;

import io.renren.common.validator.group.AliyunGroup;
import io.renren.common.validator.group.QcloudGroup;
import io.renren.common.validator.group.QiniuGroup;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * å¸¸é‡
 *
 * @author Mark sunlightcs@gmail.com
 */
public class Constant {
    /**
     * è¶…çº§ç®¡ç†å‘˜ID
     */
    public static final int SUPER_ADMIN = 1;
    /**
     * å½“å‰é¡µç 
     */
    public static final String PAGE = "page";
    /**
     * æ¯é¡µæ˜¾ç¤ºè®°å½•æ•°
     */
    public static final String LIMIT = "limit";
    /**
     * æŽ’åºå­—æ®µ
     */
    public static final String ORDER_FIELD = "sidx";
    /**
     * æŽ’åºæ–¹å¼
     */
    public static final String ORDER = "order";
    /**
     * å‡åº
     */
    public static final String ASC = "asc";

    /**
     * èœå•ç±»åž‹
     *
     * @author chenshun
     * @email sunlightcs@gmail.com
     * @date 2016å¹´11æœˆ15æ—¥ ä¸‹åˆ1:24:29
     */
    public enum MenuType {
        /**
         * ç›®å½•
         */
        CATALOG(0),
        /**
         * èœå•
         */
        MENU(1),
        /**
         * æŒ‰é’®
         */
        BUTTON(2);

        private int value;

        MenuType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * å®šæ—¶ä»»åŠ¡çŠ¶æ€
     *
     * @author chenshun
     * @email sunlightcs@gmail.com
     * @date 2016å¹´12æœˆ3æ—¥ ä¸Šåˆ12:07:22
     */
    public enum ScheduleStatus {
        /**
         * æ­£å¸¸
         */
        NORMAL(0),
        /**
         * æš‚åœ
         */
        PAUSE(1);

        private int value;

        ScheduleStatus(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * äº‘æœåŠ¡å•†
     */
    public enum CloudService {
        /**
         * ä¸ƒç‰›äº‘
         */
        QINIU(1, QiniuGroup.class),
        /**
         * é˜¿é‡Œäº‘
         */
        ALIYUN(2, AliyunGroup.class),
        /**
         * è…¾è®¯äº‘
         */
        QCLOUD(3, QcloudGroup.class);

        private int value;

        private Class<?> validatorGroupClass;

        CloudService(int value, Class<?> validatorGroupClass) {
            this.value = value;
            this.validatorGroupClass = validatorGroupClass;
        }

        public int getValue() {
            return value;
        }

        public Class<?> getValidatorGroupClass() {
            return this.validatorGroupClass;
        }

        public static CloudService getByValue(Integer value) {
            Optional<CloudService> first = Stream.of(CloudService.values()).filter(cs -> value.equals(cs.value)).findFirst();
            if (!first.isPresent()) {
                throw new IllegalArgumentException("éžæ³•çš„æžšä¸¾å€¼:" + value);
            }
            return first.get();
        }
    }

}
