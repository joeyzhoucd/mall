

package io.renren.modules.sys.service;

import com.baomidou.mybatisplus.extension.service.IService;
import io.renren.common.utils.R;
import io.renren.modules.sys.entity.SysUserTokenEntity;


public interface SysUserTokenService extends IService<SysUserTokenEntity> {

	
	R createToken(long userId);

	
	void logout(long userId);

}
