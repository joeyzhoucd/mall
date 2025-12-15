package com.mall.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.member.dao.MemberDao;
import com.mall.member.entity.MemberEntity;
import com.mall.member.exception.PhoneExistException;
import com.mall.member.exception.UsernameExistException;
import com.mall.member.service.MemberService;
import com.mall.member.vo.MemberLoginVo;
import com.mall.member.vo.MemberRegistVo;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service("memberService")
public class MemberServiceImpl extends ServiceImpl<MemberDao, MemberEntity> implements MemberService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<MemberEntity> page = this.page(
                new Query<MemberEntity>().getPage(params),
                new QueryWrapper<>());

        return new PageUtils(page);
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
