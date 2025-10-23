package com.mall.member.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;


@Data
@TableName("ums_member_receive_address")
public class MemberReceiveAddressEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private Long memberId;
	
	private String name;
	
	private String phone;
	
	private String postCode;
	
	private String province;
	
	private String city;
	
	private String region;
	
	private String detailAddress;
	
	private String areacode;
	
	private Integer defaultStatus;

}
