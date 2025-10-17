package com.mall.member.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * ä¼šå‘˜ç»Ÿè®¡ä¿¡æ¯
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:25:02
 */
@Data
@TableName("ums_member_statistics_info")
public class MemberStatisticsInfoEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * ä¼šå‘˜id
	 */
	private Long memberId;
	/**
	 * ç´¯è®¡æ¶ˆè´¹é‡‘é¢
	 */
	private BigDecimal consumeAmount;
	/**
	 * ç´¯è®¡ä¼˜æƒ é‡‘é¢
	 */
	private BigDecimal couponAmount;
	/**
	 * è®¢å•æ•°é‡
	 */
	private Integer orderCount;
	/**
	 * ä¼˜æƒ åˆ¸æ•°é‡
	 */
	private Integer couponCount;
	/**
	 * è¯„ä»·æ•°
	 */
	private Integer commentCount;
	/**
	 * é€€è´§æ•°é‡
	 */
	private Integer returnOrderCount;
	/**
	 * ç™»å½•æ¬¡æ•°
	 */
	private Integer loginCount;
	/**
	 * å…³æ³¨æ•°é‡
	 */
	private Integer attendCount;
	/**
	 * ç²‰ä¸æ•°é‡
	 */
	private Integer fansCount;
	/**
	 * æ”¶è—çš„å•†å“æ•°é‡
	 */
	private Integer collectProductCount;
	/**
	 * æ”¶è—çš„ä¸“é¢˜æ´»åŠ¨æ•°é‡
	 */
	private Integer collectSubjectCount;
	/**
	 * æ”¶è—çš„è¯„è®ºæ•°é‡
	 */
	private Integer collectCommentCount;
	/**
	 * é‚€è¯·çš„æœ‹å‹æ•°é‡
	 */
	private Integer inviteFriendCount;

}
