package com.mall.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RUtils {

    private RUtils() {
    }

    public static boolean isOk(R r) {
        return r != null && r.getCode() != null && r.getCode() == 0;
    }

    public static Integer getCode(R r) {
        if (r == null) {
            return null;
        }
        return r.getCode();
    }

    public static Integer getInteger(R r, String key) {
        if (!isOk(r)) {
            return null;
        }
        Object value = r.get(key);
        return value instanceof Integer ? (Integer) value : null;
    }

    public static <T> T getData(R r, String key, ObjectMapper mapper, TypeReference<T> typeReference) {
        if (!isOk(r) || mapper == null) {
            return null;
        }
        Object value = r.get(key);
        if (value == null) {
            return null;
        }
        return mapper.convertValue(value, typeReference);
    }
}

