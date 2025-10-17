/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.oss.cloud;

import io.renren.common.utils.DateUtils;
import org.apache.commons.lang.StringUtils;

import java.io.InputStream;
import java.util.Date;
import java.util.UUID;

/**
 * äº‘å­˜å‚¨(æ”¯æŒä¸ƒç‰›ã€é˜¿é‡Œäº‘ã€è…¾è®¯äº‘ã€åˆæ‹äº‘)
 *
 * @author Mark sunlightcs@gmail.com
 */
public abstract class CloudStorageService {
    /** äº‘å­˜å‚¨é…ç½®ä¿¡æ¯ */
    CloudStorageConfig config;

    /**
     * æ–‡ä»¶è·¯å¾„
     * @param prefix å‰ç¼€
     * @param suffix åŽç¼€
     * @return è¿”å›žä¸Šä¼ è·¯å¾„
     */
    public String getPath(String prefix, String suffix) {
        //ç”Ÿæˆuuid
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        //æ–‡ä»¶è·¯å¾„
        String path = DateUtils.format(new Date(), "yyyyMMdd") + "/" + uuid;

        if(StringUtils.isNotBlank(prefix)){
            path = prefix + "/" + path;
        }

        return path + suffix;
    }

    /**
     * æ–‡ä»¶ä¸Šä¼ 
     * @param data    æ–‡ä»¶å­—èŠ‚æ•°ç»„
     * @param path    æ–‡ä»¶è·¯å¾„ï¼ŒåŒ…å«æ–‡ä»¶å
     * @return        è¿”å›žhttpåœ°å€
     */
    public abstract String upload(byte[] data, String path);

    /**
     * æ–‡ä»¶ä¸Šä¼ 
     * @param data     æ–‡ä»¶å­—èŠ‚æ•°ç»„
     * @param suffix   åŽç¼€
     * @return         è¿”å›žhttpåœ°å€
     */
    public abstract String uploadSuffix(byte[] data, String suffix);

    /**
     * æ–‡ä»¶ä¸Šä¼ 
     * @param inputStream   å­—èŠ‚æµ
     * @param path          æ–‡ä»¶è·¯å¾„ï¼ŒåŒ…å«æ–‡ä»¶å
     * @return              è¿”å›žhttpåœ°å€
     */
    public abstract String upload(InputStream inputStream, String path);

    /**
     * æ–‡ä»¶ä¸Šä¼ 
     * @param inputStream  å­—èŠ‚æµ
     * @param suffix       åŽç¼€
     * @return             è¿”å›žhttpåœ°å€
     */
    public abstract String uploadSuffix(InputStream inputStream, String suffix);

}
