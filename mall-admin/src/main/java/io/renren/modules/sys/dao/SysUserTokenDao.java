

package io.renren.modules.sys.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.renren.modules.sys.entity.SysUserTokenEntity;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface SysUserTokenDao extends BaseMapper<SysUserTokenEntity> {

    SysUserTokenEntity queryByToken(String token);
	
}
