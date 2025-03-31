package com.joeyzhoucd.member.dao;

import com.joeyzhoucd.member.entity.MemberLoginLogEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员登录记录
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:25:02
 */
@Mapper
public interface MemberLoginLogDao extends BaseMapper<MemberLoginLogEntity> {
	
}
