package com.joeyzhoucd.member.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.joeyzhoucd.member.entity.MemberEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会员
 * 
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:25:02
 */
@Mapper
public interface MemberDao extends BaseMapper<MemberEntity> {
	
}
