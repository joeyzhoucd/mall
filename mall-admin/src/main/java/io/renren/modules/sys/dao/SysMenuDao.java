

package io.renren.modules.sys.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.renren.modules.sys.entity.SysMenuEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;


@Mapper
public interface SysMenuDao extends BaseMapper<SysMenuEntity> {
	
	
	List<SysMenuEntity> queryListParentId(Long parentId);
	
	
	List<SysMenuEntity> queryNotButtonList();

}
