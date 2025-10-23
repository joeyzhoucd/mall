package io.renren.adaptor;

import io.renren.entity.mongo.MongoDefinition;
import io.renren.entity.mongo.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MongoDB table info adapter
 */
public class MongoTableInfoAdaptor {

    /**
     * Get table info for multiple names
     */
    public static List<Map<String, String>> tableInfo(List<String> names) {
        List<Map<String, String>> result = new ArrayList<>(names.size());
        for (String name : names) {
            result.add(tableInfo(name));
        }
        return result;
    }

    /**
     * Get table info for a single name
     */
    public static Map<String, String> tableInfo(String name) {
        Map<String, String> tableInfo = new HashMap<>(4 * 4 / 3 + 1);
        tableInfo.put("engine", "mongo");
        tableInfo.put("createTime", "mongo");
        tableInfo.put("tableComment", "mongo");
        tableInfo.put("tableName", name);
        return tableInfo;
    }

    /**
     * Get column info from MongoDB definition
     */
    public static List<Map<String, String>> columnInfo(MongoDefinition mongoDefinition) {
        List<MongoDefinition> child = mongoDefinition.getChild();
        List<Map<String, String>> result = new ArrayList<>(child.size());
        final String mongoKey = "_id";
        for (MongoDefinition definition : child) {
            Map<String, String> map = new HashMap<>(5 * 4 / 3 + 1);
            String type = Type.typeInfo(definition.getType());
            String propertyName = definition.getPropertyName();
            String extra = definition.isArray() ? "array" : "";
            map.put("extra", extra);
            map.put("columnComment", "");
            map.put("dataType", definition.hasChild() ? propertyName : type);
            map.put("columnName", propertyName);
            // MongoDB primary key is _id
            String columnKey = propertyName.equals(mongoKey) ? "PRI" : "";
            map.put("columnKey", columnKey);
            result.add(map);
        }
        return result;
    }
}