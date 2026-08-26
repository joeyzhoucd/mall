package com.mall.common.xss;

import com.mall.common.exception.RRException;
import org.apache.commons.lang3.StringUtils;

/**
 * SQL injection filter
 */
public class SQLFilter {

    /**
     * Filter SQL injection
     */
    public static String sqlInject(String str){
        if(StringUtils.isBlank(str)){
            return null;
        }
        // Remove '|"|;|\ characters
        str = StringUtils.replace(str, "'", "");
        str = StringUtils.replace(str, "\"", "");
        str = StringUtils.replace(str, ";", "");
        str = StringUtils.replace(str, "\\", "");

        // Convert to lowercase
        str = str.toLowerCase();

        // SQL keywords
        String[] keywords = {"master", "truncate", "insert", "select", "delete", "update", "declare", "alter", "drop"};

        // Check if contains SQL keywords
        for(String keyword : keywords){
            if(str.indexOf(keyword) != -1){
                throw new RRException("SQL injection detected");
            }
        }

        return str;
    }
}