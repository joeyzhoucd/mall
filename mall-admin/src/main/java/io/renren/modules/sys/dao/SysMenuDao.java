/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.sys.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.renren.modules.sys.entity.SysMenuEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * èœå•ç®¡ç†
 *
 * @author Mark sunlightcs@gmail.com
 */
@Mapper
public interface SysMenuDao extends BaseMapper<SysMenuEntity> {
	
	/**
	 * æ ¹æ®çˆ¶èœå•ï¼ŒæŸ¥è¯¢å­èœå•
	 * @param parentId çˆ¶èœå•ID
	 */
	List<SysMenuEntity> queryListParentId(Long parentId);
	
	/**
	 * èŽ·å–ä¸åŒ…å«æŒ‰é’®çš„èœå•åˆ—è¡¨
	 */
	List<SysMenuEntity> queryNotButtonList();

}
