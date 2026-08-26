package com.mall.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.admin.dao.SysUserDao;
import com.mall.admin.dao.SysUserRoleDao;
import com.mall.admin.entity.SysUserEntity;
import com.mall.admin.entity.SysUserRoleEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 后台用户。
 */
@Service
public class SysUserService {

    /** 正常 */
    public static final int STATUS_ENABLED = 1;

    private final SysUserDao sysUserDao;
    private final SysUserRoleDao sysUserRoleDao;
    private final PasswordEncoder passwordEncoder;

    public SysUserService(SysUserDao sysUserDao, SysUserRoleDao sysUserRoleDao, PasswordEncoder passwordEncoder) {
        this.sysUserDao = sysUserDao;
        this.sysUserRoleDao = sysUserRoleDao;
        this.passwordEncoder = passwordEncoder;
    }

    public SysUserEntity findByUsername(String username) {
        return sysUserDao.selectOne(new QueryWrapper<SysUserEntity>().eq("username", username));
    }

    /**
     * 校验明文密码。
     * <p>
     * 只支持 BCrypt。种子数据里 admin 原本是 Shiro 的 sha256(salt + password)，
     * 已经在 mall_admin 的种子 SQL 里换成 BCrypt 哈希（密码不变，仍是 admin123）。
     * <p>
     * 这里刻意【不做】"先试 BCrypt、失败再试旧的 sha256"这种兼容：那样等于把弱哈希
     * 永久保留成一条可用的登录路径，而且很容易被忘掉。一次性把数据换过去更干净。
     * 如果将来遇到未迁移的旧哈希，表现是登录失败（而不是静默降级），这是想要的行为。
     */
    public boolean matchesPassword(SysUserEntity user, String rawPassword) {
        if (user == null || user.getPassword() == null || rawPassword == null) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    public SysUserEntity infoWithRoles(Long userId) {
        SysUserEntity user = sysUserDao.selectById(userId);
        if (user == null) {
            return null;
        }
        user.setPassword(null);
        user.setSalt(null);
        user.setRoleIdList(sysUserRoleDao.selectRoleIdsByUserId(userId));
        return user;
    }

    public IPage<SysUserEntity> page(int page, int limit, String username) {
        QueryWrapper<SysUserEntity> wrapper = new QueryWrapper<>();
        if (username != null && !username.isBlank()) {
            wrapper.like("username", username);
        }
        wrapper.orderByAsc("user_id");
        IPage<SysUserEntity> result = sysUserDao.selectPage(new Page<>(page, limit), wrapper);
        // 列表里绝不能带出口令哈希和盐。前端不需要，带出去只是白白扩大泄露面。
        for (SysUserEntity u : result.getRecords()) {
            u.setPassword(null);
            u.setSalt(null);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(SysUserEntity user, Long operatorId) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        // salt 是旧的 Shiro 方案用的列，BCrypt 自带盐，这里留空。
        user.setSalt(null);
        user.setCreateUserId(operatorId);
        user.setCreateTime(new Date());
        if (user.getStatus() == null) {
            user.setStatus(STATUS_ENABLED);
        }
        sysUserDao.insert(user);
        saveRoleRelations(user.getUserId(), user.getRoleIdList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SysUserEntity user) {
        // 前端修改用户时密码框留空表示"不改密码"。必须把字段置成 null，
        // 让 MyBatis-Plus 的非空更新跳过这一列 —— 否则会把空串编码成一个 BCrypt 哈希写进去，
        // 用户从此再也登不上，而且没有任何报错。
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            user.setPassword(null);
        } else {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        sysUserDao.updateById(user);
        saveRoleRelations(user.getUserId(), user.getRoleIdList());
    }

    /**
     * 批量删除。
     *
     * @throws IllegalArgumentException 试图删除超级管理员或自己时
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> userIds, Long operatorId) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        if (userIds.contains(SysMenuService.SUPER_ADMIN_ID)) {
            throw new IllegalArgumentException("系统管理员不能删除");
        }
        if (userIds.contains(operatorId)) {
            // 允许删自己会造成一个很难恢复的状态：手上的令牌还有效但账号已经没了，
            // 各接口行为不一致。直接禁止最省事。
            throw new IllegalArgumentException("当前用户不能删除");
        }
        sysUserDao.deleteByIds(userIds);
        sysUserRoleDao.delete(new QueryWrapper<SysUserRoleEntity>().in("user_id", userIds));
    }

    /**
     * 修改自己的密码。
     *
     * @return false 表示原密码不对
     */
    public boolean updatePassword(Long userId, String oldPassword, String newPassword) {
        SysUserEntity user = sysUserDao.selectById(userId);
        if (!matchesPassword(user, oldPassword)) {
            return false;
        }
        SysUserEntity update = new SysUserEntity();
        update.setUserId(userId);
        update.setPassword(passwordEncoder.encode(newPassword));
        sysUserDao.updateById(update);
        return true;
    }

    /** 重建用户的角色关系。数量很小，删了重建最简单也最不容易出错。 */
    private void saveRoleRelations(Long userId, List<Long> roleIdList) {
        sysUserRoleDao.delete(new QueryWrapper<SysUserRoleEntity>().eq("user_id", userId));
        if (roleIdList == null || roleIdList.isEmpty()) {
            return;
        }
        for (Long roleId : roleIdList) {
            if (roleId == null) {
                continue;
            }
            SysUserRoleEntity relation = new SysUserRoleEntity();
            relation.setUserId(userId);
            relation.setRoleId(roleId);
            sysUserRoleDao.insert(relation);
        }
    }
}
