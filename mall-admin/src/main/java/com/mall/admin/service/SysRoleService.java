package com.mall.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.admin.dao.SysRoleDao;
import com.mall.admin.dao.SysRoleMenuDao;
import com.mall.admin.entity.SysRoleEntity;
import com.mall.admin.entity.SysRoleMenuEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 角色与菜单授权。
 *
 * <h3>这里最要小心的是 -666666 哨兵</h3>
 * 前端的菜单授权树区分"全勾选"和"半勾选"（父节点部分选中），而半勾选的 key 拿不到统一接口，
 * 于是它用一个哨兵值把两段拼成一个数组传过来：
 * <pre>
 * 保存：menuIdList = [...getCheckedKeys(), -666666, ...getHalfCheckedKeys()]
 * 回显：idx = menuIdList.indexOf(-666666)
 *       if (idx !== -1) menuIdList.splice(idx, length - idx)   // 从哨兵处截断
 *       setCheckedKeys(menuIdList)
 * </pre>
 * 所以本服务必须：<b>原样按顺序落库（含 -666666 这一行），查询时按插入顺序原样返回</b>。
 * <ul>
 *   <li>如果把 -666666 过滤掉：前端找不到分隔符就不截断，半勾选的父节点会被当成完全勾选，
 *       授权范围被静默放大。</li>
 *   <li>如果不保序：截断位置错位，勾选状态错乱。</li>
 * </ul>
 * 两种错误都不会抛异常，只会让权限数据慢慢失真 —— 属于最难发现的一类 bug。
 * <p>
 * 注意这和 {@link SysMenuService} 里的要求正好相反：那边判断"用户能看哪些菜单"时
 * 必须把负数 id 过滤掉，因为哨兵不是真实菜单。同一个值，两个地方两种处理，都要写对。
 */
@Service
public class SysRoleService {

    private final SysRoleDao sysRoleDao;
    private final SysRoleMenuDao sysRoleMenuDao;

    public SysRoleService(SysRoleDao sysRoleDao, SysRoleMenuDao sysRoleMenuDao) {
        this.sysRoleDao = sysRoleDao;
        this.sysRoleMenuDao = sysRoleMenuDao;
    }

    public IPage<SysRoleEntity> page(int page, int limit, String roleName) {
        QueryWrapper<SysRoleEntity> wrapper = new QueryWrapper<>();
        if (roleName != null && !roleName.isBlank()) {
            wrapper.like("role_name", roleName);
        }
        wrapper.orderByAsc("role_id");
        return sysRoleDao.selectPage(new Page<>(page, limit), wrapper);
    }

    public List<SysRoleEntity> listAll() {
        return sysRoleDao.selectList(new QueryWrapper<SysRoleEntity>().orderByAsc("role_id"));
    }

    /** 角色详情，带上 menuIdList（保序、含哨兵）。 */
    public SysRoleEntity info(Long roleId) {
        SysRoleEntity role = sysRoleDao.selectById(roleId);
        if (role == null) {
            return null;
        }
        role.setMenuIdList(sysRoleMenuDao.selectMenuIdsByRoleId(roleId));
        return role;
    }

    @Transactional(rollbackFor = Exception.class)
    public void save(SysRoleEntity role, Long operatorId) {
        role.setCreateUserId(operatorId);
        role.setCreateTime(new Date());
        sysRoleDao.insert(role);
        saveMenuRelations(role.getRoleId(), role.getMenuIdList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SysRoleEntity role) {
        sysRoleDao.updateById(role);
        saveMenuRelations(role.getRoleId(), role.getMenuIdList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        sysRoleDao.deleteByIds(roleIds);
        sysRoleMenuDao.delete(new QueryWrapper<SysRoleMenuEntity>().in("role_id", roleIds));
    }

    /**
     * 重建角色的菜单关系：先全删再按顺序插入。
     * <p>
     * 用"删了重建"而不是"算差集增量更新"，是因为这里的顺序本身就是数据的一部分
     * （哨兵的位置决定了前端怎么截断），增量更新没法保证最终顺序，反而更复杂更容易错。
     * 角色的菜单数量是几十的量级，全量重建的成本可以忽略。
     * <p>
     * 逐条 insert 而不是批量：需要依赖自增 id 的严格递增来保证读取顺序，
     * 逐条插入语义最明确。同样地，这个量级不值得为性能牺牲确定性。
     */
    private void saveMenuRelations(Long roleId, List<Long> menuIdList) {
        sysRoleMenuDao.delete(new QueryWrapper<SysRoleMenuEntity>().eq("role_id", roleId));
        if (menuIdList == null || menuIdList.isEmpty()) {
            return;
        }
        for (Long menuId : menuIdList) {
            if (menuId == null) {
                continue;
            }
            // 这里【不过滤】负数：-666666 必须作为一条真实记录存下来，它是前端的分隔符。
            SysRoleMenuEntity relation = new SysRoleMenuEntity();
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            sysRoleMenuDao.insert(relation);
        }
    }
}
