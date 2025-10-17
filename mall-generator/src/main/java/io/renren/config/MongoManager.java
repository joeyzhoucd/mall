package io.renren.config;


import io.renren.entity.mongo.MongoDefinition;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author gxz
 * @date 2020/5/10 12:05
 */
public class MongoManager {

    /***mongoæ‰«æå¾ˆæ¶ˆè€—æ€§èƒ½ å°¤å…¶æ˜¯å­ç±»çš„å°è£…  ä½¿ç”¨ç¼“å­˜**/
    private static Map<String, MongoDefinition> mongoCache = new ConcurrentHashMap<>();

    public static Map<String, MongoDefinition> getCache() {
        return mongoCache;
    }

    public static MongoDefinition getInfo(String tableName) {
        return mongoCache.getOrDefault(tableName, null);
    }

    public static MongoDefinition putInfo(String tableName, MongoDefinition mongoDefinition) {
        return mongoCache.put(tableName, mongoDefinition);
    }

    /**
     * å½“å‰é…ç½®æ˜¯å¦ä¸ºmongoå†…å®¹
     */
    public static boolean isMongo() {
        return DbConfig.isMongo();
    }


}
