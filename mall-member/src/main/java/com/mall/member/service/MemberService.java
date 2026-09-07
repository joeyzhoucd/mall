package com.mall.member.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.member.entity.MemberEntity;
import com.mall.member.vo.MemberLoginVo;
import com.mall.member.vo.MemberRegistVo;

import java.util.Map;

public interface MemberService extends IService<MemberEntity> {

    /**
     * 后台会员列表。返回的是 {@link com.mall.member.vo.MemberAdminVo}，
     * <b>不是 MemberEntity</b>。
     *
     * <h3>为什么这里只有一个分页方法</h3>
     * 原本这个方法返回的是实体（生成器模板），而实体上带着 password（BCrypt 哈希）、
     * accessToken（第三方登录令牌）和 socialUid。2026-09-06 补后台入口时，
     * 没有新增一个"安全版本"、把原来那个留着 —— 而是把这个改成返回 VO。
     * 留着两个方法的话，将来有人补一个接口时挑中错的那个，
     * 不会有任何报错、也不会有人复查，响应里就多了一列哈希。
     * <p>
     * 补充：改之前确认过全仓库没有任何调用方，所以这不是破坏性改动。
     */
    PageUtils queryPage(Map<String, Object> params);

    /**
     * 后台会员详情。联系方式<b>不脱敏</b>（列表脱敏，详情给全量），
     * 但同样不含 password / accessToken / socialUid。
     *
     * @return 查不到时返回 null，由调用方决定怎么表达"不存在"
     */
    com.mall.member.vo.MemberAdminVo getAdminDetail(Long id);

    void register(MemberRegistVo vo);

    MemberEntity login(MemberLoginVo vo);

    MemberEntity login(com.mall.member.vo.SocialUser socialUser);
}
