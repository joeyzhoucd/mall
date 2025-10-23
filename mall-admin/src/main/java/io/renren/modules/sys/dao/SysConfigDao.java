

package io.renren.modules.sys.dao;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.renren.modules.sys.entity.SysConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


@Mapper
public interface SysConfigDao extends BaseMapper<SysConfigEntity> {

	
	SysConfigEntity queryByKey(String paramKey);

	
	int updateValueByKey(@Param("paramKey") String paramKey, @Param("paramValue") String paramValue);
	
}
