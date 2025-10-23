package com.mall.member.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;


@Data
@TableName("ums_member_collect_subject")
public class MemberCollectSubjectEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private Long subjectId;
	
	private String subjectName;
	
	private String subjectImg;
	
	private String subjectUrll;

}
