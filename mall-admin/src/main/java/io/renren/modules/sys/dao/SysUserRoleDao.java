

package io.renren.modules.sys.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.renren.modules.sys.entity.SysUserRoleEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;


@Mapper
public interface SysUserRoleDao extends BaseMapper<SysUserRoleEntity> {
	
	
	List<Long> queryRoleIdList(Long userId);


	
	int deleteBatch(Long[] roleIds);
}
