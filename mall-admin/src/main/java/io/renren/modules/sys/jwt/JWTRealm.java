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
 * JWT realm for authentication and authorization
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
     * Authorization
     */
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        SysUserEntity user = (SysUserEntity)principals.getPrimaryPrincipal();
        Long userId = user.getUserId();

        // User permissions
        Set<String> permsSet = shiroService.getUserPermissions(userId);

        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        info.setStringPermissions(permsSet);
        return info;
    }

    /**
     * Authentication
     */
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        String accessToken = (String) token.getPrincipal();

        // Query user by accessToken
        SysUserTokenEntity tokenEntity = shiroService.queryByToken(accessToken);
        // Token expired
        if(tokenEntity == null || tokenEntity.getExpireTime().getTime() < System.currentTimeMillis()){
            throw new IncorrectCredentialsException("token expired, please login again");
        }

        // Query user info
        SysUserEntity user = shiroService.queryUser(tokenEntity.getUserId());
        // Account locked
        if(user.getStatus() == 0){
            throw new LockedAccountException("Account locked, please contact administrator");
        }

        SimpleAuthenticationInfo info = new SimpleAuthenticationInfo(user, accessToken, getName());
        return info;
    }
}