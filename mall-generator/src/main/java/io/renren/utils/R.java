package io.renren.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * è¿”å›žæ•°æ®
 * 
 * @author chenshun
 * @email sunlightcs@gmail.com
 * @date 2016å¹´10æœˆ27æ—¥ ä¸‹åˆ9:59:27
 */
public class R extends HashMap<String, Object> {
	private static final long serialVersionUID = 1L;
	
	public R() {
		put("code", 0);
	}
	
	public static R error() {
		return error(500, "æœªçŸ¥å¼‚å¸¸ï¼Œè¯·è”ç³»ç®¡ç†å‘˜");
	}
	
	public static R error(String msg) {
		return error(500, msg);
	}
	
	public static R error(int code, String msg) {
		R r = new R();
		r.put("code", code);
		r.put("msg", msg);
		return r;
	}

	public static R ok(String msg) {
		R r = new R();
		r.put("msg", msg);
		return r;
	}
	
	public static R ok(Map<String, Object> map) {
		R r = new R();
		r.putAll(map);
		return r;
	}
	
	public static R ok() {
		return new R();
	}

	public R put(String key, Object value) {
		super.put(key, value);
		return this;
	}
}
