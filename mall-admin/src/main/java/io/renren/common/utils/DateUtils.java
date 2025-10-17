/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.common.utils;

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * æ—¥æœŸå¤„ç†
 *
 * @author Mark sunlightcs@gmail.com
 */
public class DateUtils {
	/** æ—¶é—´æ ¼å¼(yyyy-MM-dd) */
	public final static String DATE_PATTERN = "yyyy-MM-dd";
	/** æ—¶é—´æ ¼å¼(yyyy-MM-dd HH:mm:ss) */
	public final static String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

    /**
     * æ—¥æœŸæ ¼å¼åŒ– æ—¥æœŸæ ¼å¼ä¸ºï¼šyyyy-MM-dd
     * @param date  æ—¥æœŸ
     * @return  è¿”å›žyyyy-MM-ddæ ¼å¼æ—¥æœŸ
     */
	public static String format(Date date) {
        return format(date, DATE_PATTERN);
    }

    /**
     * æ—¥æœŸæ ¼å¼åŒ– æ—¥æœŸæ ¼å¼ä¸ºï¼šyyyy-MM-dd
     * @param date  æ—¥æœŸ
     * @param pattern  æ ¼å¼ï¼Œå¦‚ï¼šDateUtils.DATE_TIME_PATTERN
     * @return  è¿”å›žyyyy-MM-ddæ ¼å¼æ—¥æœŸ
     */
    public static String format(Date date, String pattern) {
        if(date != null){
            SimpleDateFormat df = new SimpleDateFormat(pattern);
            return df.format(date);
        }
        return null;
    }

    /**
     * å­—ç¬¦ä¸²è½¬æ¢æˆæ—¥æœŸ
     * @param strDate æ—¥æœŸå­—ç¬¦ä¸²
     * @param pattern æ—¥æœŸçš„æ ¼å¼ï¼Œå¦‚ï¼šDateUtils.DATE_TIME_PATTERN
     */
    public static Date stringToDate(String strDate, String pattern) {
        if (StringUtils.isBlank(strDate)){
            return null;
        }

        DateTimeFormatter fmt = DateTimeFormat.forPattern(pattern);
        return fmt.parseLocalDateTime(strDate).toDate();
    }

    /**
     * æ ¹æ®å‘¨æ•°ï¼ŒèŽ·å–å¼€å§‹æ—¥æœŸã€ç»“æŸæ—¥æœŸ
     * @param week  å‘¨æœŸ  0æœ¬å‘¨ï¼Œ-1ä¸Šå‘¨ï¼Œ-2ä¸Šä¸Šå‘¨ï¼Œ1ä¸‹å‘¨ï¼Œ2ä¸‹ä¸‹å‘¨
     * @return  è¿”å›ždate[0]å¼€å§‹æ—¥æœŸã€date[1]ç»“æŸæ—¥æœŸ
     */
    public static Date[] getWeekStartAndEnd(int week) {
        DateTime dateTime = new DateTime();
        LocalDate date = new LocalDate(dateTime.plusWeeks(week));

        date = date.dayOfWeek().withMinimumValue();
        Date beginDate = date.toDate();
        Date endDate = date.plusDays(6).toDate();
        return new Date[]{beginDate, endDate};
    }

    /**
     * å¯¹æ—¥æœŸçš„ã€ç§’ã€‘è¿›è¡ŒåŠ /å‡
     *
     * @param date æ—¥æœŸ
     * @param seconds ç§’æ•°ï¼Œè´Ÿæ•°ä¸ºå‡
     * @return åŠ /å‡å‡ ç§’åŽçš„æ—¥æœŸ
     */
    public static Date addDateSeconds(Date date, int seconds) {
        DateTime dateTime = new DateTime(date);
        return dateTime.plusSeconds(seconds).toDate();
    }

    /**
     * å¯¹æ—¥æœŸçš„ã€åˆ†é’Ÿã€‘è¿›è¡ŒåŠ /å‡
     *
     * @param date æ—¥æœŸ
     * @param minutes åˆ†é’Ÿæ•°ï¼Œè´Ÿæ•°ä¸ºå‡
     * @return åŠ /å‡å‡ åˆ†é’ŸåŽçš„æ—¥æœŸ
     */
    public static Date addDateMinutes(Date date, int minutes) {
        DateTime dateTime = new DateTime(date);
        return dateTime.plusMinutes(minutes).toDate();
    }

    /**
     * å¯¹æ—¥æœŸçš„ã€å°æ—¶ã€‘è¿›è¡ŒåŠ /å‡
     *
     * @param date æ—¥æœŸ
     * @param hours å°æ—¶æ•°ï¼Œè´Ÿæ•°ä¸ºå‡
     * @return åŠ /å‡å‡ å°æ—¶åŽçš„æ—¥æœŸ
     */
    public static Date addDateHours(Date date, int hours) {
        DateTime dateTime = new DateTime(date);
        return dateTime.plusHours(hours).toDate();
    }

    /**
     * å¯¹æ—¥æœŸçš„ã€å¤©ã€‘è¿›è¡ŒåŠ /å‡
     *
     * @param date æ—¥æœŸ
     * @param days å¤©æ•°ï¼Œè´Ÿæ•°ä¸ºå‡
     * @return åŠ /å‡å‡ å¤©åŽçš„æ—¥æœŸ
     */
    public static Date addDateDays(Date date, int days) {
        DateTime dateTime = new DateTime(date);
        return dateTime.plusDays(days).toDate();
    }

    /**
     * å¯¹æ—¥æœŸçš„ã€å‘¨ã€‘è¿›è¡ŒåŠ /å‡
     *
     * @param date æ—¥æœŸ
     * @param weeks å‘¨æ•°ï¼Œè´Ÿæ•°ä¸ºå‡
     * @return åŠ /å‡å‡ å‘¨åŽçš„æ—¥æœŸ
     */
    public static Date addDateWeeks(Date date, int weeks) {
        DateTime dateTime = new DateTime(date);
        return dateTime.plusWeeks(weeks).toDate();
    }

    /**
     * å¯¹æ—¥æœŸçš„ã€æœˆã€‘è¿›è¡ŒåŠ /å‡
     *
     * @param date æ—¥æœŸ
     * @param months æœˆæ•°ï¼Œè´Ÿæ•°ä¸ºå‡
     * @return åŠ /å‡å‡ æœˆåŽçš„æ—¥æœŸ
     */
    public static Date addDateMonths(Date date, int months) {
        DateTime dateTime = new DateTime(date);
        return dateTime.plusMonths(months).toDate();
    }

    /**
     * å¯¹æ—¥æœŸçš„ã€å¹´ã€‘è¿›è¡ŒåŠ /å‡
     *
     * @param date æ—¥æœŸ
     * @param years å¹´æ•°ï¼Œè´Ÿæ•°ä¸ºå‡
     * @return åŠ /å‡å‡ å¹´åŽçš„æ—¥æœŸ
     */
    public static Date addDateYears(Date date, int years) {
        DateTime dateTime = new DateTime(date);
        return dateTime.plusYears(years).toDate();
    }
}
