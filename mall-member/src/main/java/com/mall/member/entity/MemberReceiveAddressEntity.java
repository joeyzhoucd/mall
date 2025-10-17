package com.mall.member.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * ä¼šå‘˜æ”¶è´§åœ°å€
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:25:02
 */
@Data
@TableName("ums_member_receive_address")
public class MemberReceiveAddressEntity implements Serializable {
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
	 * æ”¶è´§äººå§“å
	 */
	private String name;
	/**
	 * ç”µè¯
	 */
	private String phone;
	/**
	 * é‚®æ”¿ç¼–ç 
	 */
	private String postCode;
	/**
	 * çœä»½/ç›´è¾–å¸‚
	 */
	private String province;
	/**
	 * åŸŽå¸‚
	 */
	private String city;
	/**
	 * åŒº
	 */
	private String region;
	/**
	 * è¯¦ç»†åœ°å€(è¡—é“)
	 */
	private String detailAddress;
	/**
	 * çœå¸‚åŒºä»£ç 
	 */
	private String areacode;
	/**
	 * æ˜¯å¦é»˜è®¤
	 */
	private Integer defaultStatus;

}
