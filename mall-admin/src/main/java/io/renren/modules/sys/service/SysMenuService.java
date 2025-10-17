/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.sys.service;


import com.baomidou.mybatisplus.extension.service.IService;
import io.renren.modules.sys.entity.SysMenuEntity;

import java.util.List;


/**
 * èœå•ç®¡ç†
 *
 * @author Mark sunlightcs@gmail.com
 */
public interface SysMenuService extends IService<SysMenuEntity> {

	/**
	 * æ ¹æ®çˆ¶èœå•ï¼ŒæŸ¥è¯¢å­èœå•
	 * @param parentId çˆ¶èœå•ID
	 * @param menuIdList  ç”¨æˆ·èœå•ID
	 */
	List<SysMenuEntity> queryListParentId(Long parentId, List<Long> menuIdList);

	/**
	 * æ ¹æ®çˆ¶èœå•ï¼ŒæŸ¥è¯¢å­èœå•
	 * @param parentId çˆ¶èœå•ID
	 */
	List<SysMenuEntity> queryListParentId(Long parentId);
	
	/**
	 * èŽ·å–ä¸åŒ…å«æŒ‰é’®çš„èœå•åˆ—è¡¨
	 */
	List<SysMenuEntity> queryNotButtonList();
	
	/**
	 * èŽ·å–ç”¨æˆ·èœå•åˆ—è¡¨
	 */
	List<SysMenuEntity> getUserMenuList(Long userId);

	/**
	 * åˆ é™¤
	 */
	void delete(Long menuId);
}
