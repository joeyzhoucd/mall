package com.mall.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.member.dao.MemberDao;
import com.mall.member.entity.MemberEntity;
import com.mall.member.entity.MemberLevelEntity;
import com.mall.member.exception.PhoneExistException;
import com.mall.member.exception.UsernameExistException;
import com.mall.member.service.MemberLevelService;
import com.mall.member.service.MemberService;
import com.mall.member.vo.MemberAdminVo;
import com.mall.member.vo.MemberLoginVo;
import com.mall.member.vo.MemberRegistVo;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service("memberService")
public class MemberServiceImpl extends ServiceImpl<MemberDao, MemberEntity> implements MemberService {

    private final MemberLevelService memberLevelService;

    public MemberServiceImpl(MemberLevelService memberLevelService) {
        this.memberLevelService = memberLevelService;
    }

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<MemberEntity> page = this.page(
                new Query<MemberEntity>().getPage(params),
                buildQueryWrapper(params));

        Map<Long, String> levelNames = levelNames();
        List<MemberAdminVo> list = page.getRecords().stream()
                .map(entity -> MemberAdminVo.ofListItem(entity, levelNames.get(entity.getLevelId())))
                .collect(Collectors.toList());

        return new PageUtils(list, (int) page.getTotal(), (int) page.getSize(), (int) page.getCurrent());
    }

    @Override
    public MemberAdminVo getAdminDetail(Long id) {
        if (id == null) {
            return null;
        }
        MemberEntity entity = this.getById(id);
        if (entity == null) {
            return null;
        }
        return MemberAdminVo.ofDetail(entity, levelNames().get(entity.getLevelId()));
    }

    /**
     * 会员列表的筛选和排序。
     *
     * <p>抽成包内可见的独立方法是为了<b>能被测试直接调用</b>：
     * queryPage 要连数据库，而条件拼装是纯逻辑。
     * 上周做订单查询时在这里栽过一次 —— 测试里自己复刻了一遍拼装逻辑，
     * 结果把生产代码的 ORDER BY 整行删掉，8 条测试全部照常通过。
     * 见 MemberQueryPageTest 的说明。
     *
     * <h3>排序必须以唯一列 id 收尾</h3>
     * 只按 create_time 排是不够的：会员是批量导入的（当前 103 条里有 100 条
     * 是同一批种子数据），同一秒里的那些行之间顺序未定义，
     * 翻页时会重复或漏掉。数据少时完全看不出来。
     */
    QueryWrapper<MemberEntity> buildQueryWrapper(Map<String, Object> params) {
        QueryWrapper<MemberEntity> wrapper = new QueryWrapper<>();

        String key = trimmed(params, "key");
        if (key != null) {
            // 包在 and(...) 里，否则这个 or 会把 levelId / status 那些条件一起吞掉 ——
            // 表现是「筛了等级，结果还是全部」。
            wrapper.and(w -> w.like("username", key)
                    .or().like("nickname", key)
                    .or().like("mobile", key));
        }

        Long levelId = longOrNull(params, "levelId");
        if (levelId != null) {
            wrapper.eq("level_id", levelId);
        }

        Integer status = intOrNull(params, "status");
        if (status != null) {
            wrapper.eq("status", status);
        }

        String from = trimmed(params, "createTimeFrom");
        if (from != null) {
            wrapper.ge("create_time", from);
        }
        String to = trimmed(params, "createTimeTo");
        if (to != null) {
            wrapper.le("create_time", to);
        }

        return wrapper.orderByDesc("create_time").orderByDesc("id");
    }

    /**
     * 等级 id → 等级名。
     *
     * <p>用一次额外查询换掉一个 join：等级表只有个位数行，
     * 而写 join 就意味着要给 MemberDao 加一份手写 XML，
     * 那份 XML 会成为唯一一处「实体字段和 SQL 列名必须手工保持一致」的地方。
     *
     * <p><b>当前 ums_member_level 是空表</b>（2026-09-06 实测 0 行），
     * 所以 levelName 全部会是 null。这不是 bug，是那张表从来没被填过；
     * 前端要能回落到显示 levelId。
     */
    private Map<Long, String> levelNames() {
        Map<Long, String> names = new HashMap<>();
        for (MemberLevelEntity level : memberLevelService.list()) {
            if (level != null && level.getId() != null) {
                names.put(level.getId(), level.getName());
            }
        }
        return names;
    }

    /** 空白视同没传：清空后的输入框会被原样提交，拼成 {@code username LIKE '%%'} 没意义。 */
    private static String trimmed(Map<String, Object> params, String name) {
        Object raw = params == null ? null : params.get(name);
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    /** 解析不了就当没传，不抛异常 —— 一个非法的筛选值不该让整个列表接口 500。 */
    private static Integer intOrNull(Map<String, Object> params, String name) {
        String value = trimmed(params, name);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long longOrNull(Map<String, Object> params, String name) {
        String value = trimmed(params, name);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void register(MemberRegistVo vo) {
        MemberDao memberDao = this.baseMapper;
        MemberEntity entity = new MemberEntity();

        // Check Username
        Long count = memberDao.selectCount(new QueryWrapper<MemberEntity>().eq("username", vo.getUserName()));
        if (count > 0) {
            throw new UsernameExistException();
        }

        // Check Phone
        Long phoneCount = memberDao.selectCount(new QueryWrapper<MemberEntity>().eq("mobile", vo.getPhone()));
        if (phoneCount > 0) {
            throw new PhoneExistException();
        }

        entity.setUsername(vo.getUserName());
        entity.setMobile(vo.getPhone());
        entity.setNickname(vo.getUserName());

        // Encrypt Password
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String encode = passwordEncoder.encode(vo.getPassword());
        entity.setPassword(encode);

        memberDao.insert(entity);
    }

    @Override
    public MemberEntity login(MemberLoginVo vo) {
        String loginacct = vo.getLoginacct();
        String password = vo.getPassword();

        // Find by username or phone
        MemberEntity entity = this.baseMapper.selectOne(new QueryWrapper<MemberEntity>()
                .eq("username", loginacct).or().eq("mobile", loginacct));

        if (entity == null) {
            return null;
        } else {
            // Verify Password
            String dbPassword = entity.getPassword();
            BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
            boolean matches = passwordEncoder.matches(password, dbPassword);
            if (matches) {
                return entity;
            } else {
                return null;
            }
        }
    }

    @Override
    public MemberEntity login(com.mall.member.vo.SocialUser socialUser) {
        // Login/Register
        String uid = socialUser.getUid();
        // 1. Check if user exists
        MemberDao memberDao = this.baseMapper;
        MemberEntity memberEntity = memberDao.selectOne(new QueryWrapper<MemberEntity>().eq("social_uid", uid));

        if (memberEntity != null) {
            // Update token
            MemberEntity update = new MemberEntity();
            update.setId(memberEntity.getId());
            update.setAccessToken(socialUser.getAccess_token());
            update.setExpiresIn(socialUser.getExpires_in());
            memberDao.updateById(update);

            memberEntity.setAccessToken(socialUser.getAccess_token());
            memberEntity.setExpiresIn(socialUser.getExpires_in());
            return memberEntity;
        } else {
            // Register
            MemberEntity regist = new MemberEntity();
            try {
                // Get user info from Weibo if needed, or just use what we have
                regist.setNickname("WeiboUser_" + uid);
                regist.setSocialUid(uid);
                regist.setAccessToken(socialUser.getAccess_token());
                regist.setExpiresIn(socialUser.getExpires_in());
                regist.setLevelId(1L);

                memberDao.insert(regist);
            } catch (Exception e) {
                return null;
            }
            return regist;
        }
    }
}
