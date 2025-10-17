/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.common.xss;

import io.renren.common.exception.RRException;
import org.apache.commons.lang.StringUtils;

/**
 * SQLè¿‡æ»¤
 *
 * @author Mark sunlightcs@gmail.com
 */
public class SQLFilter {

    /**
     * SQLæ³¨å…¥è¿‡æ»¤
     * @param str  å¾…éªŒè¯çš„å­—ç¬¦ä¸²
     */
    public static String sqlInject(String str){
        if(StringUtils.isBlank(str)){
            return null;
        }
        //åŽ»æŽ‰'|"|;|\å­—ç¬¦
        str = StringUtils.replace(str, "'", "");
        str = StringUtils.replace(str, "\"", "");
        str = StringUtils.replace(str, ";", "");
        str = StringUtils.replace(str, "\\", "");

        //è½¬æ¢æˆå°å†™
        str = str.toLowerCase();

        //éžæ³•å­—ç¬¦
        String[] keywords = {"master", "truncate", "insert", "select", "delete", "update", "declare", "alter", "drop"};

        //åˆ¤æ–­æ˜¯å¦åŒ…å«éžæ³•å­—ç¬¦
        for(String keyword : keywords){
            if(str.indexOf(keyword) != -1){
                throw new RRException("åŒ…å«éžæ³•å­—ç¬¦");
            }
        }

        return str;
    }
}
