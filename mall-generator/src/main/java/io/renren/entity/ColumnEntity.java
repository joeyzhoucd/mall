package io.renren.entity;

/**
 * Column entity for code generation
 */
public class ColumnEntity {
	// Column name
    private String columnName;
    // Column data type
    private String dataType;
    // Column comment
    private String comments;
    
    // Attribute name (first letter uppercase) e.g. user_name => UserName
    private String attrName;
    // Attribute name (first letter lowercase) e.g. user_name => userName
    private String attrname;
    // Attribute type
    private String attrType;
    // auto_increment
    private String extra;
    
	public String getColumnName() {
		return columnName;
	}
	public void setColumnName(String columnName) {
		this.columnName = columnName;
	}
	public String getDataType() {
		return dataType;
	}
	public void setDataType(String dataType) {
		this.dataType = dataType;
	}
	public String getComments() {
		return comments;
	}
	public void setComments(String comments) {
		this.comments = comments;
	}
	public String getAttrname() {
		return attrname;
	}
	public void setAttrname(String attrname) {
		this.attrname = attrname;
	}
	public String getAttrName() {
		return attrName;
	}
	public void setAttrName(String attrName) {
		this.attrName = attrName;
	}
	public String getAttrType() {
		return attrType;
	}
	public void setAttrType(String attrType) {
		this.attrType = attrType;
	}
	public String getExtra() {
		return extra;
	}
	public void setExtra(String extra) {
		this.extra = extra;
	}
}