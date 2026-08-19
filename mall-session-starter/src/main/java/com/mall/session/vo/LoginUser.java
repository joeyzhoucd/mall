package com.mall.session.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 已登录用户的会话态视图，由 auth 服务写入共享 session，cart/order/product 等服务读取。
 * 不依赖任何具体业务服务的内部类，避免编译期跨服务耦合。
 */
@Data
public class LoginUser implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String nickname;
    private String mobile;
    private Long levelId;
    private String icon;
}
