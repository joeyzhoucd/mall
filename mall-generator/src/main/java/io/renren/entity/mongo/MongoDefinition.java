package io.renren.entity.mongo;

import io.renren.adaptor.MongoTableInfoAdaptor;
import org.apache.commons.collections.CollectionUtils;

import java.io.Serializable;
import java.util.*;

/**
 * MongoDB definition entity
 */
public class MongoDefinition implements Serializable {
    
    private String propertyName;
    
    private Integer type;
    
    private boolean array = false;
    
    private List<MongoDefinition> child;

    /**
     * Get children info for code generation
     */
    public List<MongoGeneratorEntity> getChildrenInfo(String tableName) {
        List<MongoGeneratorEntity> result = new ArrayList<>();
        MongoGeneratorEntity info = new MongoGeneratorEntity();
        // Table info
        Map<String, String> tableInfo = MongoTableInfoAdaptor.tableInfo(tableName);
        // Column info
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

    /**
     * Check if has children
     */
    public boolean hasChild() {
        final int objectType = 3;
        return type == null || Objects.equals(type, objectType) || CollectionUtils.isNotEmpty(child);
    }

    /**
     * Check if is primary bean
     */
    public boolean primaryBean() {
        return type == null;
    }

    /**
     * Set type
     */
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