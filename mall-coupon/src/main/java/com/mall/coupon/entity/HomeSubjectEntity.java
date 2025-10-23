package com.mall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;


@Data
@TableName("sms_home_subject")
public class HomeSubjectEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private String name;
	
	private String title;
	
	private String subTitle;
	
	private Integer status;
	
	private String url;
	
	private Integer sort;
	
	private String img;

}
