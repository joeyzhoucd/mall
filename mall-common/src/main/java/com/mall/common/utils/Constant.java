package com.mall.common.utils;

import com.mall.common.validator.group.AliyunGroup;
import com.mall.common.validator.group.QcloudGroup;
import com.mall.common.validator.group.QiniuGroup;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * System constants
 */
public class Constant {
    
    public static final int SUPER_ADMIN = 1;
    
    public static final String PAGE = "page";
    
    public static final String LIMIT = "limit";
    
    public static final String ORDER_FIELD = "sidx";
    
    public static final String ORDER = "order";
    
    public static final String ASC = "asc";

    /**
     * Menu type enum
     */
    public enum MenuType {
        // Catalog
        CATALOG(0),
        // Menu
        MENU(1),
        // Button
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
     * Schedule status enum
     */
    public enum ScheduleStatus {
        // Normal
        NORMAL(0),
        // Pause
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
     * Cloud service enum
     */
    public enum CloudService {
        // Qiniu
        QINIU(1, QiniuGroup.class),
        // Aliyun
        ALIYUN(2, AliyunGroup.class),
        // Qcloud
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
                throw new IllegalArgumentException("Invalid cloud service type: " + value);
            }
            return first.get();
        }
    }

}