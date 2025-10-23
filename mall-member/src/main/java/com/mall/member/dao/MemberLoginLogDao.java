package com.mall.member.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.member.entity.MemberLoginLogEntity;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface MemberLoginLogDao extends BaseMapper<MemberLoginLogEntity> {
	
}
