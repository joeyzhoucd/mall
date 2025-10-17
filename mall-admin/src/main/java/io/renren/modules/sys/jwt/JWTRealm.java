/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.sys.jwt;

import io.renren.modules.sys.entity.SysUserEntity;
import io.renren.modules.sys.entity.SysUserTokenEntity;
import io.renren.modules.sys.service.ShiroService;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * è®¤è¯
 *
 * @author Mark sunlightcs@gmail.com
 */
@Component
public class JWTRealm extends AuthorizingRealm {
    @Autowired
    private ShiroService shiroService;

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof JWTToken;
    }

    /**
     * æŽˆæƒ(éªŒè¯æƒé™æ—¶è°ƒç”¨)
     */
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        SysUserEntity user = (SysUserEntity)principals.getPrimaryPrincipal();
        Long userId = user.getUserId();

        //ç”¨æˆ·æƒé™åˆ—è¡¨
        Set<String> permsSet = shiroService.getUserPermissions(userId);

        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        info.setStringPermissions(permsSet);
        return info;
    }

    /**
     * è®¤è¯(ç™»å½•æ—¶è°ƒç”¨)
     */
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        String accessToken = (String) token.getPrincipal();

        //æ ¹æ®accessTokenï¼ŒæŸ¥è¯¢ç”¨æˆ·ä¿¡æ¯
        SysUserTokenEntity tokenEntity = shiroService.queryByToken(accessToken);
        //tokenå¤±æ•ˆ
        if(tokenEntity == null || tokenEntity.getExpireTime().getTime() < System.currentTimeMillis()){
            throw new IncorrectCredentialsException("tokenå¤±æ•ˆï¼Œè¯·é‡æ–°ç™»å½•");
        }

        //æŸ¥è¯¢ç”¨æˆ·ä¿¡æ¯
        SysUserEntity user = shiroService.queryUser(tokenEntity.getUserId());
        //è´¦å·é”å®š
        if(user.getStatus() == 0){
            throw new LockedAccountException("è´¦å·å·²è¢«é”å®š,è¯·è”ç³»ç®¡ç†å‘˜");
        }

        SimpleAuthenticationInfo info = new SimpleAuthenticationInfo(user, accessToken, getName());
        return info;
    }
}
