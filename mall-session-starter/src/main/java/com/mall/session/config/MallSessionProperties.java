package com.mall.session.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "mall.session")
public class MallSessionProperties {
    /**
    * 开关：开启才生效
    */
    private boolean enabled = false;
    /**
     * 跨域共享的一级域名
     */
    private String domain = "mall.com";
    /**
     * Cookie 名称
     */
    private String cookieName = "MALLSESSION";
}

