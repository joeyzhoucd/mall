package io.renren.adaptor;

import io.renren.entity.mongo.MongoDefinition;
import io.renren.entity.mongo.Type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * mongoé€‚é…å™¨
 *
 * @author: gxz gongxuanzhang@foxmail.com
 **/
public class MongoTableInfoAdaptor {

    /**
     * æŸ¥è¯¢è¡¨ä¿¡æ¯çš„æ—¶å€™ mongoåªèƒ½èŽ·å¾—è¡¨å å…¶ä»–åªèƒ½æ‰‹åŠ¨å¡«å†™
     *
     * @param names è¡¨å
     */
    public static List<Map<String, String>> tableInfo(List<String> names) {
        List<Map<String, String>> result = new ArrayList<>(names.size());
        for (String name : names) {
            result.add(tableInfo(name));
        }
        return result;
    }

    public static Map<String, String> tableInfo(String name) {
        Map<String, String> tableInfo = new HashMap<>(4 * 4 / 3 + 1);
        tableInfo.put("engine", "mongoæ— å¼•æ“Ž");
        tableInfo.put("createTime", "mongoæ— æ³•æŸ¥è¯¢åˆ›å»ºæ—¶é—´");
        tableInfo.put("tableComment", "mongoæ— å¤‡æ³¨");
        tableInfo.put("tableName", name);
        return tableInfo;
    }

    /**
     * åœ¨æŸ¥è¯¢åˆ—åçš„æ—¶å€™ éœ€è¦å°†è§£æžå‡ºçš„mongoä¿¡æ¯é€‚é…æˆå…³ç³»åž‹æ•°æ®åº“æ‰€éœ€è¦çš„ä¿¡æ¯å½¢å¼
     * æ­¤æ–¹æ³•åªé’ˆå¯¹ä¸»Bean
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
            // mongoé»˜è®¤ä¸»é”®æ˜¯_id
            String columnKey = propertyName.equals(mongoKey) ? "PRI" : "";
            map.put("columnKey", columnKey);
            result.add(map);
        }
        return result;
    }


}
