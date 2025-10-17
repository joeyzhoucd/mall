package com.mall.member.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * ä¼šå‘˜ç­‰çº§
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:25:02
 */
@Data
@TableName("ums_member_level")
public class MemberLevelEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
    private Long id;
	/**
	 * ç­‰çº§åç§°
	 */
	private String name;
	/**
	 * ç­‰çº§éœ€è¦çš„æˆé•¿å€¼
	 */
	private Integer growthPoint;
	/**
	 * æ˜¯å¦ä¸ºé»˜è®¤ç­‰çº§[0->ä¸æ˜¯ï¼›1->æ˜¯]
	 */
	private Integer defaultStatus;
	/**
	 * å…è¿è´¹æ ‡å‡†
	 */
	private BigDecimal freeFreightPoint;
	/**
	 * æ¯æ¬¡è¯„ä»·èŽ·å–çš„æˆé•¿å€¼
	 */
	private Integer commentGrowthPoint;
	/**
	 * æ˜¯å¦æœ‰å…é‚®ç‰¹æƒ
	 */
	private Integer priviledgeFreeFreight;
	/**
	 * æ˜¯å¦æœ‰ä¼šå‘˜ä»·æ ¼ç‰¹æƒ
	 */
	private Integer priviledgeMemberPrice;
	/**
	 * æ˜¯å¦æœ‰ç”Ÿæ—¥ç‰¹æƒ
	 */
	private Integer priviledgeBirthday;
	/**
	 * å¤‡æ³¨
	 */
	private String note;

}
