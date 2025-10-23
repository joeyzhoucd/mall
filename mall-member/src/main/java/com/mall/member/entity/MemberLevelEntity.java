package com.mall.member.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;


@Data
@TableName("ums_member_level")
public class MemberLevelEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
    private Long id;
	
	private String name;
	
	private Integer growthPoint;
	
	private Integer defaultStatus;
	
	private BigDecimal freeFreightPoint;
	
	private Integer commentGrowthPoint;
	
	private Integer priviledgeFreeFreight;
	
	private Integer priviledgeMemberPrice;
	
	private Integer priviledgeBirthday;
	
	private String note;

}
