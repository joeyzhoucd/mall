package io.renren.modules.sys.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.renren.common.utils.R;
import io.renren.modules.sys.dao.SysUserTokenDao;
import io.renren.modules.sys.entity.SysUserTokenEntity;
import io.renren.modules.sys.jwt.TokenGenerator;
import io.renren.modules.sys.service.SysUserTokenService;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * System user token service implementation
 */
@Service("sysUserTokenService")
public class SysUserTokenServiceImpl extends ServiceImpl<SysUserTokenDao, SysUserTokenEntity> implements SysUserTokenService {
	// 12 hours expiration
	private final static int EXPIRE = 3600 * 12;

	@Override
	public R createToken(long userId) {
		// Generate token
		String token = TokenGenerator.generateValue();

		// Current time
		Date now = new Date();
		// Expire time
		Date expireTime = new Date(now.getTime() + EXPIRE * 1000);

		// Check if user has token
		SysUserTokenEntity tokenEntity = this.getById(userId);
		if(tokenEntity == null){
			tokenEntity = new SysUserTokenEntity();
			tokenEntity.setUserId(userId);
			tokenEntity.setToken(token);
			tokenEntity.setUpdateTime(now);
			tokenEntity.setExpireTime(expireTime);

			// Save token
			this.save(tokenEntity);
		}else{
			tokenEntity.setToken(token);
			tokenEntity.setUpdateTime(now);
			tokenEntity.setExpireTime(expireTime);

			// Update token
			this.updateById(tokenEntity);
		}

		R r = R.ok().put("token", token).put("expire", EXPIRE);

		return r;
	}

	@Override
	public void logout(long userId) {
		// Generate token
		String token = TokenGenerator.generateValue();

		// Update token
		SysUserTokenEntity tokenEntity = new SysUserTokenEntity();
		tokenEntity.setUserId(userId);
		tokenEntity.setToken(token);
		this.updateById(tokenEntity);
	}
}