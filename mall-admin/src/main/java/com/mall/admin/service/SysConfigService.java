package com.mall.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.admin.dao.SysConfigDao;
import com.mall.admin.entity.SysConfigEntity;
import org.springframework.stereotype.Service;

import java.util.List;

/** 系统参数配置。纯 CRUD。 */
@Service
public class SysConfigService {

    private final SysConfigDao sysConfigDao;

    public SysConfigService(SysConfigDao sysConfigDao) {
        this.sysConfigDao = sysConfigDao;
    }

    public IPage<SysConfigEntity> page(int page, int limit, String paramKey) {
        QueryWrapper<SysConfigEntity> wrapper = new QueryWrapper<>();
        if (paramKey != null && !paramKey.isBlank()) {
            wrapper.like("param_key", paramKey);
        }
        wrapper.orderByAsc("id");
        return sysConfigDao.selectPage(new Page<>(page, limit), wrapper);
    }

    public SysConfigEntity info(Long id) {
        return sysConfigDao.selectById(id);
    }

    public void save(SysConfigEntity config) {
        sysConfigDao.insert(config);
    }

    public void update(SysConfigEntity config) {
        sysConfigDao.updateById(config);
    }

    public void delete(List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            sysConfigDao.deleteByIds(ids);
        }
    }
}
