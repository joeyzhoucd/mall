package io.renren.entity.mongo;

import io.renren.adaptor.MongoTableInfoAdaptor;
import org.apache.commons.collections.CollectionUtils;

import java.io.Serializable;
import java.util.*;


/**
 * è§£æžè¡¨ä¹‹åŽå¾—åˆ°çš„ä¿¡æ¯å®žä½“
 * æ¢å¥è¯è¯´è¿™ä¸ªç±»å°±æ˜¯ä¸€å¼ mongoä¸€å¼ è¡¨çš„å†…å®¹
 *
 * @author gxz 514190950@qq.com
 */

public class MongoDefinition implements Serializable {
    /***å±žæ€§å**/
    private String propertyName;
    /***å±žæ€§ç±»åž‹ å¯¹åº”mongodb api $type   å¦‚æžœæ²¡æœ‰ç±»åž‹ è¡¨ç¤ºè¿™æ˜¯ä¸€ä¸ªé¡¶å±‚å®žä½“  è€Œä¸æ˜¯å†…åµŒå±žæ€§**/
    private Integer type;
    /***æ­¤å±žæ€§æ˜¯å¦æ˜¯æ•°ç»„**/
    private boolean array = false;
    /***å¦‚æžœæ­¤å±žæ€§æ˜¯å¯¹è±¡  é‚£ä¹ˆä»–ä»ç„¶æœ‰æ­¤ç±»åž‹çš„å­ç±»**/
    private List<MongoDefinition> child;


    public List<MongoGeneratorEntity> getChildrenInfo(String tableName) {
        List<MongoGeneratorEntity> result = new ArrayList<>();
        MongoGeneratorEntity info = new MongoGeneratorEntity();
        // è¡¨ä¿¡æ¯
        Map<String, String> tableInfo = MongoTableInfoAdaptor.tableInfo(tableName);
        // åˆ—åä¿¡æ¯
        List<Map<String, String>> columnsInfo = new ArrayList<>();
        info.setColumns(columnsInfo);
        info.setTableInfo(tableInfo);
        result.add(info);
        List<MongoDefinition> child = this.getChild();
        for (MongoDefinition mongoDefinition : child) {
            Map<String, String> columnInfo = new HashMap<>(5);
            columnInfo.put("columnName", mongoDefinition.getPropertyName());
            columnInfo.put("dataType", Type.typeInfo(mongoDefinition.getType()));
            columnInfo.put("extra", mongoDefinition.isArray() ? "array" : "");
            columnsInfo.add(columnInfo);
            if (mongoDefinition.hasChild()) {
                result.addAll(mongoDefinition.getChildrenInfo(mongoDefinition.getPropertyName()));
            }
        }
        return result;
    }

    public boolean hasChild() {
        final int objectType = 3;
        return type == null || Objects.equals(type, objectType) || CollectionUtils.isNotEmpty(child);
    }


    public boolean primaryBean() {
        return type == null;
    }


    public MongoDefinition setType(Integer type) {
        this.type = type;
        return this;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public MongoDefinition setPropertyName(String propertyName) {
        this.propertyName = propertyName;
        return this;
    }

    public Integer getType() {
        return type;
    }

    public boolean isArray() {
        return array;
    }

    public MongoDefinition setArray(boolean array) {
        this.array = array;
        return this;
    }

    public List<MongoDefinition> getChild() {
        return child;
    }

    public MongoDefinition setChild(List<MongoDefinition> child) {
        this.child = child;
        return this;
    }
}
