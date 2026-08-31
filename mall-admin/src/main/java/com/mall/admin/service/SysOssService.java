package com.mall.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.admin.dao.SysOssDao;
import com.mall.admin.entity.SysOssEntity;
import com.mall.admin.feign.StorageFeignService;
import com.mall.common.utils.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 后台上传文件的登记与清理。 */
@Service
public class SysOssService {

    private static final Logger log = LoggerFactory.getLogger(SysOssService.class);

    private final SysOssDao sysOssDao;
    private final StorageFeignService storageFeignService;

    public SysOssService(SysOssDao sysOssDao, StorageFeignService storageFeignService) {
        this.sysOssDao = sysOssDao;
        this.storageFeignService = storageFeignService;
    }

    public IPage<SysOssEntity> page(int page, int limit) {
        QueryWrapper<SysOssEntity> wrapper = new QueryWrapper<>();
        // 最近上传的排前面：这个页面的用途是"我刚传的那个在哪"，不是翻历史。
        wrapper.orderByDesc("create_date");
        return sysOssDao.selectPage(new Page<>(page, limit), wrapper);
    }

    /**
     * 登记一次已完成的上传。
     *
     * <p>同一个 key 重复登记时<b>不报错</b>，直接返回已有记录。前端 PUT 成功后调这个接口，
     * 网络抖动时它会重试；如果重试被当成错误，用户看到的是"上传失败"而文件其实已经传上去了 ——
     * 那比多一条记录糟得多。key 上有唯一约束，靠它保证不会真的写重。
     */
    @Transactional
    public SysOssEntity confirm(SysOssEntity oss) {
        SysOssEntity existing = sysOssDao.selectOne(
                new QueryWrapper<SysOssEntity>().eq("object_key", oss.getObjectKey()));
        if (existing != null) {
            return existing;
        }
        sysOssDao.insert(oss);
        return oss;
    }

    /**
     * 删除记录，并让 mall-thirdparty 删掉对应的对象。
     *
     * <h3>顺序：先删对象，再删记录</h3>
     * 和 SPU 删除时「先提交数据库、再清 ES」相反，这里刻意反过来，因为两种残留的
     * 可恢复性不同：
     * <ul>
     *   <li>先删记录再删对象 —— 对象删失败时，记录已经没了，<b>再也没人知道那个 key</b>，
     *       桶里留下一个永远清不掉的孤儿文件（还在继续计费）。</li>
     *   <li>先删对象再删记录 —— 记录删失败时，列表里留一条指向已删对象的记录，
     *       用户点开是 404，<b>再删一次就好了</b>（S3 的删除是幂等的）。</li>
     * </ul>
     * 两种都不完美，但后者可见、可重放。
     */
    @Transactional
    public int delete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<SysOssEntity> rows = sysOssDao.selectByIds(ids);
        if (rows.isEmpty()) {
            return 0;
        }
        List<String> keys = rows.stream()
                .map(SysOssEntity::getObjectKey)
                .filter(k -> k != null && !k.isBlank())
                .toList();
        if (!keys.isEmpty()) {
            R result = storageFeignService.deleteObjects(keys);
            if (result == null || result.getCode() != 0) {
                // 抛出去让事务回滚：对象没删掉就不该把记录删了，否则就是上面说的
                // "再也没人知道那个 key"。
                throw new IllegalStateException("删除对象失败，已回滚记录删除: "
                        + (result != null ? result.getMsg() : "调用无返回"));
            }
        }
        return sysOssDao.deleteByIds(ids);
    }

    /** 当前生效的存储配置（非密）。取不到时不抛异常，让页面显示"未知"而不是整页报错。 */
    public Object storageConfig() {
        try {
            R result = storageFeignService.storageConfig();
            return result != null && result.getCode() == 0 ? result.get("data") : null;
        } catch (Exception e) {
            log.warn("获取存储配置失败: {}", e.getMessage());
            return null;
        }
    }
}
