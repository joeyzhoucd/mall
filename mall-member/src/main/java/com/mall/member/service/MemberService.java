package com.mall.member.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.member.entity.MemberEntity;

import java.util.Map;


public interface MemberService extends IService<MemberEntity> {

    PageUtils queryPage(Map<String, Object> params);
}
