

package io.renren.modules.sys.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.renren.modules.sys.entity.SysRoleEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;


@Mapper
public interface SysRoleDao extends BaseMapper<SysRoleEntity> {
	
	
	List<Long> queryRoleIdList(Long createUserId);
}
