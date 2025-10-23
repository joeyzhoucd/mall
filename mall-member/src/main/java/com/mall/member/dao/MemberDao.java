package com.mall.member.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.member.entity.MemberEntity;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface MemberDao extends BaseMapper<MemberEntity> {
	
}
