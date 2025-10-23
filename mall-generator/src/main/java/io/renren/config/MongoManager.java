package io.renren.config;


import io.renren.entity.mongo.MongoDefinition;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class MongoManager {

    
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

    
    public static boolean isMongo() {
        return DbConfig.isMongo();
    }


}
