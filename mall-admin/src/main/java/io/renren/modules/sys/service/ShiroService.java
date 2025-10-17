/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.sys.service;

import io.renren.modules.sys.entity.SysUserEntity;
import io.renren.modules.sys.entity.SysUserTokenEntity;

import java.util.Set;

/**
 * shiroç›¸å…³æŽ¥å£
 *
 * @author Mark sunlightcs@gmail.com
 */
public interface ShiroService {
    /**
     * èŽ·å–ç”¨æˆ·æƒé™åˆ—è¡¨
     */
    Set<String> getUserPermissions(long userId);

    SysUserTokenEntity queryByToken(String token);

    /**
     * æ ¹æ®ç”¨æˆ·IDï¼ŒæŸ¥è¯¢ç”¨æˆ·
     * @param userId
     */
    SysUserEntity queryUser(Long userId);
}
