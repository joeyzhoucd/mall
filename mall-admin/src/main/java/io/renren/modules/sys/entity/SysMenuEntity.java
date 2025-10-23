

package io.renren.modules.sys.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;


@Data
@TableName("sys_menu")
public class SysMenuEntity implements Serializable,Comparable<SysMenuEntity> {
	private static final long serialVersionUID = 1L;
	
	
	@TableId
	private Long menuId;

	
	private Long parentId;
	
	
	@TableField(exist=false)
	private String parentName;

	
	private String name;

	
	private String url;

	
	private String perms;

	
	private Integer type;

	
	private String icon;

	
	private Integer orderNum;
	
	
	@TableField(exist=false)
	private Boolean open;

	@TableField(exist=false)
	private List<SysMenuEntity> list=new ArrayList<>();

	@Override
	public int compareTo(SysMenuEntity o) {
		return this.getOrderNum()-o.getOrderNum();
	}
}
