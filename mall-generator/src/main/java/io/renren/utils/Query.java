package io.renren.utils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Query parameters for pagination
 */
public class Query extends LinkedHashMap<String, Object> {
	private static final long serialVersionUID = 1L;
	// Current page number
    private int page;
    // Page size
    private int limit;

    /**
     * Constructor
     */
    public Query(Map<String, Object> params){
        this.putAll(params);

        // Parse page parameters
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