package com.mall.member.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.member.entity.MemberEntity;
import com.mall.member.vo.MemberLoginVo;
import com.mall.member.vo.MemberRegistVo;

import java.util.Map;

public interface MemberService extends IService<MemberEntity> {

    PageUtils queryPage(Map<String, Object> params);

    void register(MemberRegistVo vo);

    MemberEntity login(MemberLoginVo vo);

    MemberEntity login(com.mall.member.vo.SocialUser socialUser);
}
