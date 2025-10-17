/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.sys.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * èœå•ç®¡ç†
 *
 * @author Mark sunlightcs@gmail.com
 */
@Data
@TableName("sys_menu")
public class SysMenuEntity implements Serializable,Comparable<SysMenuEntity> {
	private static final long serialVersionUID = 1L;
	
	/**
	 * èœå•ID
	 */
	@TableId
	private Long menuId;

	/**
	 * çˆ¶èœå•IDï¼Œä¸€çº§èœå•ä¸º0
	 */
	private Long parentId;
	
	/**
	 * çˆ¶èœå•åç§°
	 */
	@TableField(exist=false)
	private String parentName;

	/**
	 * èœå•åç§°
	 */
	private String name;

	/**
	 * èœå•URL
	 */
	private String url;

	/**
	 * æŽˆæƒ(å¤šä¸ªç”¨é€—å·åˆ†éš”ï¼Œå¦‚ï¼šuser:list,user:create)
	 */
	private String perms;

	/**
	 * ç±»åž‹     0ï¼šç›®å½•   1ï¼šèœå•   2ï¼šæŒ‰é’®
	 */
	private Integer type;

	/**
	 * èœå•å›¾æ ‡
	 */
	private String icon;

	/**
	 * æŽ’åº
	 */
	private Integer orderNum;
	
	/**
	 * ztreeå±žæ€§
	 */
	@TableField(exist=false)
	private Boolean open;

	@TableField(exist=false)
	private List<SysMenuEntity> list=new ArrayList<>();

	@Override
	public int compareTo(SysMenuEntity o) {
		return this.getOrderNum()-o.getOrderNum();
	}
}
