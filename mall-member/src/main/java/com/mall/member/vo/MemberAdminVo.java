package com.mall.member.vo;

import com.mall.common.utils.PiiMask;
import com.mall.member.entity.MemberEntity;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 后台会员列表/详情的返回对象。
 *
 * <h3>为什么不直接返回 MemberEntity</h3>
 * MemberEntity 上有三个<b>绝对不能出后端</b>的字段：
 * <ul>
 *   <li>{@code password} —— BCrypt 哈希。哈希不等于安全：它是离线爆破的输入，
 *       拿到就可以慢慢跑字典，而用户在别处很可能用同一个密码。</li>
 *   <li>{@code accessToken} —— 第三方登录的访问令牌，拿到就能<b>以该用户身份</b>
 *       调那个平台的接口。这个比密码哈希更直接：它不需要破解。</li>
 *   <li>{@code socialUid} —— 跨平台的身份关联标识。</li>
 * </ul>
 *
 * <h3>为什么是"显式列出要什么"而不是"排除不要什么"</h3>
 * 用 {@code @JsonIgnore} 标在实体上，或者在返回前 {@code setPassword(null)}，
 * 都是<b>黑名单</b>：将来给 MemberEntity 加一个字段（比如 {@code idCardNo}），
 * 它会自动出现在响应里，而没有任何地方会报错、也不会有人想起来要去改这里。
 * 白名单反过来 —— 新字段默认不出现，想让它出现必须有人显式加一行。
 * 这个方向上的默认值决定了"忘记"的后果是什么。
 *
 * <h3>脱敏只在列表</h3>
 * {@link #ofListItem} 会把手机号和邮箱打码，{@link #ofDetail} 不会。
 * 见 {@link PiiMask} 里关于"这不是合规控制"的说明。
 */
@Data
public class MemberAdminVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long levelId;
    /** 等级名。ums_member_level 里查不到时为 null，前端应当回落到显示 levelId。 */
    private String levelName;
    private String username;
    private String nickname;
    private String mobile;
    private String email;
    /** 头像 URL。实体里这个字段叫 header，不叫 icon 或 avatar。 */
    private String header;
    private Integer gender;
    private Date birth;
    private String city;
    private String job;
    private String sign;
    private Integer sourceType;
    private Integer integration;
    private Integer growth;
    /**
     * 启用状态。
     *
     * <p><b>当前线上 103 个会员这一列全是 NULL</b>（2026-09-06 实测）——
     * 注册流程从来没有给它赋过值。所以前端不能把 null 当成"正常/启用"来显示，
     * 那是在替一个从未被设置过的字段编造含义。
     */
    private Integer status;
    private Date createTime;

    /** 列表项：手机号和邮箱脱敏。 */
    public static MemberAdminVo ofListItem(MemberEntity entity, String levelName) {
        MemberAdminVo vo = base(entity, levelName);
        vo.setMobile(PiiMask.mobile(entity.getMobile()));
        vo.setEmail(PiiMask.email(entity.getEmail()));
        return vo;
    }

    /** 详情：联系方式给全量。 */
    public static MemberAdminVo ofDetail(MemberEntity entity, String levelName) {
        MemberAdminVo vo = base(entity, levelName);
        vo.setMobile(entity.getMobile());
        vo.setEmail(entity.getEmail());
        return vo;
    }

    private static MemberAdminVo base(MemberEntity entity, String levelName) {
        MemberAdminVo vo = new MemberAdminVo();
        vo.setId(entity.getId());
        vo.setLevelId(entity.getLevelId());
        vo.setLevelName(levelName);
        vo.setUsername(entity.getUsername());
        vo.setNickname(entity.getNickname());
        vo.setHeader(entity.getHeader());
        vo.setGender(entity.getGender());
        vo.setBirth(entity.getBirth());
        vo.setCity(entity.getCity());
        vo.setJob(entity.getJob());
        vo.setSign(entity.getSign());
        vo.setSourceType(entity.getSourceType());
        vo.setIntegration(entity.getIntegration());
        vo.setGrowth(entity.getGrowth());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        // password / socialUid / accessToken / expiresIn 刻意不复制。
        // 改动这里之前先读一遍类注释里关于白名单的那一段。
        return vo;
    }
}
