

package io.renren.modules.app.service;


import com.baomidou.mybatisplus.extension.service.IService;
import io.renren.modules.app.entity.UserEntity;
import io.renren.modules.app.form.LoginForm;


public interface UserService extends IService<UserEntity> {

	UserEntity queryByMobile(String mobile);

	
	long login(LoginForm form);
}
