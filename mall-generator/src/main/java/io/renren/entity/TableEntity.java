package io.renren.entity;

import java.util.List;

/**
 * è¡¨æ•°æ®
 * 
 * @author chenshun
 * @email sunlightcs@gmail.com
 * @date 2016å¹´12æœˆ20æ—¥ ä¸Šåˆ12:02:55
 */
public class TableEntity {
	//è¡¨çš„åç§°
	private String tableName;
	//è¡¨çš„å¤‡æ³¨
	private String comments;
	//è¡¨çš„ä¸»é”®
	private ColumnEntity pk;
	//è¡¨çš„åˆ—å(ä¸åŒ…å«ä¸»é”®)
	private List<ColumnEntity> columns;
	
	//ç±»å(ç¬¬ä¸€ä¸ªå­—æ¯å¤§å†™)ï¼Œå¦‚ï¼šsys_user => SysUser
	private String className;
	//ç±»å(ç¬¬ä¸€ä¸ªå­—æ¯å°å†™)ï¼Œå¦‚ï¼šsys_user => sysUser
	private String classname;
	
	public String getTableName() {
		return tableName;
	}
	public void setTableName(String tableName) {
		this.tableName = tableName;
	}
	public String getComments() {
		return comments;
	}
	public void setComments(String comments) {
		this.comments = comments;
	}
	public ColumnEntity getPk() {
		return pk;
	}
	public void setPk(ColumnEntity pk) {
		this.pk = pk;
	}
	public List<ColumnEntity> getColumns() {
		return columns;
	}
	public void setColumns(List<ColumnEntity> columns) {
		this.columns = columns;
	}
	public String getClassName() {
		return className;
	}
	public void setClassName(String className) {
		this.className = className;
	}
	public String getClassname() {
		return classname;
	}
	public void setClassname(String classname) {
		this.classname = classname;
	}
}
