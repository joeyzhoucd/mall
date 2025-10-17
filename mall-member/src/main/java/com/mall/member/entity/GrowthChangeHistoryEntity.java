package com.mall.member.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * æˆé•¿å€¼å˜åŒ–åŽ†å²è®°å½•
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:25:02
 */
@Data
@TableName("ums_growth_change_history")
public class GrowthChangeHistoryEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * member_id
	 */
	private Long memberId;
	/**
	 * create_time
	 */
	private Date createTime;
	/**
	 * æ”¹å˜çš„å€¼ï¼ˆæ­£è´Ÿè®¡æ•°ï¼‰
	 */
	private Integer changeCount;
	/**
	 * å¤‡æ³¨
	 */
	private String note;
	/**
	 * ç§¯åˆ†æ¥æº[0-è´­ç‰©ï¼Œ1-ç®¡ç†å‘˜ä¿®æ”¹]
	 */
	private Integer sourceType;

}
