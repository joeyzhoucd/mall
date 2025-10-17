package com.mall.member.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * ç§¯åˆ†å˜åŒ–åŽ†å²è®°å½•
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:25:02
 */
@Data
@TableName("ums_integration_change_history")
public class IntegrationChangeHistoryEntity implements Serializable {
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
	 * å˜åŒ–çš„å€¼
	 */
	private Integer changeCount;
	/**
	 * å¤‡æ³¨
	 */
	private String note;
	/**
	 * æ¥æº[0->è´­ç‰©ï¼›1->ç®¡ç†å‘˜ä¿®æ”¹;2->æ´»åŠ¨]
	 */
	private Integer sourceTyoe;

}
