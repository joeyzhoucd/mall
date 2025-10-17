/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.common.utils;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.renren.common.xss.SQLFilter;
import org.apache.commons.lang.StringUtils;

import java.util.Map;

/**
 * æŸ¥è¯¢å‚æ•°
 *
 * @author Mark sunlightcs@gmail.com
 */
public class Query<T> {

    public IPage<T> getPage(Map<String, Object> params) {
        return this.getPage(params, null, false);
    }

    public IPage<T> getPage(Map<String, Object> params, String defaultOrderField, boolean isAsc) {
        //åˆ†é¡µå‚æ•°
        long curPage = 1;
        long limit = 10;

        if(params.get(Constant.PAGE) != null){
            curPage = Long.parseLong((String)params.get(Constant.PAGE));
        }
        if(params.get(Constant.LIMIT) != null){
            limit = Long.parseLong((String)params.get(Constant.LIMIT));
        }

        //åˆ†é¡µå¯¹è±¡
        Page<T> page = new Page<>(curPage, limit);

        //åˆ†é¡µå‚æ•°
        params.put(Constant.PAGE, page);

        //æŽ’åºå­—æ®µ
        //é˜²æ­¢SQLæ³¨å…¥ï¼ˆå› ä¸ºsidxã€orderæ˜¯é€šè¿‡æ‹¼æŽ¥SQLå®žçŽ°æŽ’åºçš„ï¼Œä¼šæœ‰SQLæ³¨å…¥é£Žé™©ï¼‰
        String orderField = SQLFilter.sqlInject((String)params.get(Constant.ORDER_FIELD));
        String order = (String)params.get(Constant.ORDER);


        //å‰ç«¯å­—æ®µæŽ’åº
        if(StringUtils.isNotEmpty(orderField) && StringUtils.isNotEmpty(order)){
            if(Constant.ASC.equalsIgnoreCase(order)) {
                return  page.addOrder(OrderItem.asc(orderField));
            }else {
                return page.addOrder(OrderItem.desc(orderField));
            }
        }

        //æ²¡æœ‰æŽ’åºå­—æ®µï¼Œåˆ™ä¸æŽ’åº
        if(StringUtils.isBlank(defaultOrderField)){
            return page;
        }

        //é»˜è®¤æŽ’åº
        if(isAsc) {
            page.addOrder(OrderItem.asc(defaultOrderField));
        }else {
            page.addOrder(OrderItem.desc(defaultOrderField));
        }

        return page;
    }
}
