package io.renren.entity;

/**
 * åˆ—çš„å±žæ€§
 * 
 * @author chenshun
 * @email sunlightcs@gmail.com
 * @date 2016å¹´12æœˆ20æ—¥ ä¸Šåˆ12:01:45
 */
public class ColumnEntity {
	//åˆ—å
    private String columnName;
    //åˆ—åç±»åž‹
    private String dataType;
    //åˆ—åå¤‡æ³¨
    private String comments;
    
    //å±žæ€§åç§°(ç¬¬ä¸€ä¸ªå­—æ¯å¤§å†™)ï¼Œå¦‚ï¼šuser_name => UserName
    private String attrName;
    //å±žæ€§åç§°(ç¬¬ä¸€ä¸ªå­—æ¯å°å†™)ï¼Œå¦‚ï¼šuser_name => userName
    private String attrname;
    //å±žæ€§ç±»åž‹
    private String attrType;
    //auto_increment
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
