package com.mall.member.vo;

import lombok.Data;

/**
 * 登录/会话返回的用户脱敏视图对象
 */
@Data
public class MemberRespVo {
    private Long id;
    private String username;
    private String nickname;
    private String mobile;
    private Long levelId;
    private String icon;
}

