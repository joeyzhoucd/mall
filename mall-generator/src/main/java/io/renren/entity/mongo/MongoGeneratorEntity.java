package io.renren.entity.mongo;


import io.renren.entity.TableEntity;

import java.util.List;
import java.util.Map;

/**
 * mysqlä¸€å¼ è¡¨åªéœ€è¦ä¸€ä¸ªè¡¨ä¿¡æ¯å’Œåˆ—åä¿¡æ¯
 * ä½†æ˜¯mongoä¸€å¼ è¡¨å¯èƒ½éœ€è¦å¤šä¸ªå®žä½“ç±»  æ‰€ä»¥å•ç‹¬ç”¨ä¸€ä¸ªbeanå°è£…
 *
 * @author gxz
 * @date 2020/5/10 0:14
 */
public class MongoGeneratorEntity {
    /***è¡¨ä¿¡æ¯**/
    private Map<String, String> tableInfo;
    /***ä¸»ç±»çš„åˆ—åä¿¡æ¯**/
    private List<Map<String, String>> columns;


    public TableEntity toTableEntity() {
        TableEntity tableEntity = new TableEntity();
        Map<String, String> tableInfo = this.tableInfo;
        tableEntity.setTableName(tableInfo.get("tableName"));
        tableEntity.setComments("");
        return tableEntity;
    }


    public Map<String, String> getTableInfo() {
        return tableInfo;
    }

    public MongoGeneratorEntity setTableInfo(Map<String, String> tableInfo) {
        this.tableInfo = tableInfo;
        return this;
    }

    public List<Map<String, String>> getColumns() {
        return columns;
    }

    public MongoGeneratorEntity setColumns(List<Map<String, String>> columns) {
        this.columns = columns;
        return this;
    }

}
