package com.mall.member.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;


@Data
@TableName("ums_growth_change_history")
public class GrowthChangeHistoryEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private Long memberId;
	
	private Date createTime;
	
	private Integer changeCount;
	
	private String note;
	
	private Integer sourceType;

}
