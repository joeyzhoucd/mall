package io.renren.utils;


import java.util.LinkedHashMap;
import java.util.Map;

/**
 * æŸ¥è¯¢å‚æ•°
 *
 * @author chenshun
 * @email sunlightcs@gmail.com
 * @date 2017-03-14 23:15
 */
public class Query extends LinkedHashMap<String, Object> {
	private static final long serialVersionUID = 1L;
	//å½“å‰é¡µç 
    private int page;
    //æ¯é¡µæ¡æ•°
    private int limit;

    public Query(Map<String, Object> params){
        this.putAll(params);

        //åˆ†é¡µå‚æ•°
        this.page = Integer.parseInt(params.get("page").toString());
        this.limit = Integer.parseInt(params.get("limit").toString());
        this.put("offset", (page - 1) * limit);
        this.put("page", page);
        this.put("limit", limit);
    }


    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
