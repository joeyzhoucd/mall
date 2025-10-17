package io.renren.utils;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * æ—¥æœŸå¤„ç†
 * 
 * @author chenshun
 * @email sunlightcs@gmail.com
 * @date 2016å¹´12æœˆ21æ—¥ ä¸‹åˆ12:53:33
 */
public class DateUtils {
	/** æ—¶é—´æ ¼å¼(yyyy-MM-dd) */
	public final static String DATE_PATTERN = "yyyy-MM-dd";
	/** æ—¶é—´æ ¼å¼(yyyy-MM-dd HH:mm:ss) */
	public final static String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
	
	public static String format(Date date) {
        return format(date, DATE_PATTERN);
    }

    public static String format(Date date, String pattern) {
        if(date != null){
            SimpleDateFormat df = new SimpleDateFormat(pattern);
            return df.format(date);
        }
        return null;
    }
}
