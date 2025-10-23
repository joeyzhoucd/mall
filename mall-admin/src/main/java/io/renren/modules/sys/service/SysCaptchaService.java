

package io.renren.modules.sys.service;

import com.baomidou.mybatisplus.extension.service.IService;
import io.renren.modules.sys.entity.SysCaptchaEntity;

import java.awt.image.BufferedImage;


public interface SysCaptchaService extends IService<SysCaptchaEntity> {

    
    BufferedImage getCaptcha(String uuid);

    
    boolean validate(String uuid, String code);
}
