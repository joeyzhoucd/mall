

package io.renren.modules.sys.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.renren.modules.sys.entity.SysUserEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;


@Mapper
public interface SysUserDao extends BaseMapper<SysUserEntity> {
	
	
	List<String> queryAllPerms(Long userId);
	
	
	List<Long> queryAllMenuId(Long userId);
	
	
	SysUserEntity queryByUserName(String username);

}
