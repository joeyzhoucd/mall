package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;


@Data
@TableName("sms_home_subject_spu")
public class HomeSubjectSpuEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private String name;
	
	private Long subjectId;
	
	private Long spuId;
	
	private Integer sort;

}
