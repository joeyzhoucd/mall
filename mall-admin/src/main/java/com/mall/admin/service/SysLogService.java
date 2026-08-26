package com.mall.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.admin.dao.SysLogDao;
import com.mall.admin.entity.SysLogEntity;
import org.springframework.stereotype.Service;

/**
 * 操作日志（只读）。
 * <p>
 * 目前只提供查询，没有写入：旧实现是用一个 &#64;SysLog 注解加 AOP 切面往表里写。
 * 重建时没有把那套搬过来，因为现在有了完整的观测栈——请求级别的信息在 Loki 里
 * （带 traceId，能直接跳到调用链），比一张只记方法名和参数的表信息量大得多。
 * 这张表和这个接口保留是为了让前端的"系统日志"页面不至于报错。
 * 如果确实需要"谁在什么时候改了什么"这种审计语义（和技术日志是两回事），
 * 应该单独设计一张审计表，而不是恢复那个切面。
 */
@Service
public class SysLogService {

    private final SysLogDao sysLogDao;

    public SysLogService(SysLogDao sysLogDao) {
        this.sysLogDao = sysLogDao;
    }

    public IPage<SysLogEntity> page(int page, int limit, String key) {
        QueryWrapper<SysLogEntity> wrapper = new QueryWrapper<>();
        if (key != null && !key.isBlank()) {
            wrapper.and(w -> w.like("username", key).or().like("operation", key));
        }
        wrapper.orderByDesc("id");
        return sysLogDao.selectPage(new Page<>(page, limit), wrapper);
    }
}
