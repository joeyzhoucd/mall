package com.mall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.product.dao.CategoryDao;
import com.mall.product.entity.CategoryEntity;
import com.mall.product.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service("categoryService")
@Slf4j
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {

    @Autowired
    private RedissonClient redissonClient;

    private static final String LOCK_KEY = "category:lock";
    private static final long LOCK_WAIT_TIME = 10;
    private static final long LOCK_LEASE_TIME = 30;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryEntity> page = this.page(
                new Query<CategoryEntity>().getPage(params),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageUtils(page);
    }

    /**
     * 查询分类树，使用缓存
     * 缓存 key: category::listAsTree
     */
    @Cacheable(value = "category", key = "'listAsTree'", unless = "#result == null or #result.isEmpty()")
    @Override
    public List<CategoryEntity> listAsTree() {
        log.info("[Category] 缓存未命中，准备查询数据库并写入缓存");
        // 1. Get all categories
        List<CategoryEntity> allCategories = baseMapper.selectList(null);
        log.info("[Category] 数据库返回分类总数: {}", allCategories != null ? allCategories.size() : 0);

        if (CollectionUtils.isEmpty(allCategories)) {
            return Collections.emptyList();
        }

        // 2. Group by parentCid
        Map<Long, List<CategoryEntity>> parentMap = allCategories.stream()
                .collect(Collectors.groupingBy(CategoryEntity::getParentCid));

        // 3. Get root categories (parentCid = 0)
        List<CategoryEntity> rootCategories = parentMap.getOrDefault(0L, Collections.emptyList());

        // 4. Set children recursively
        rootCategories.forEach(cat -> setChildren(cat, parentMap));

        // 5. Sort root categories by sort
        rootCategories.sort(Comparator.comparingInt(o -> (o.getSort() == null ? 0 : o.getSort())));

        log.info("[Category] 分类树构建完成，根节点数量: {}，写入缓存 category::listAsTree", rootCategories.size());
        return rootCategories;
    }

    /**
     * 保存分类，使用分布式锁，并清除缓存
     */
    @CacheEvict(value = "category", allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean save(CategoryEntity entity) {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            log.info("[Category] save 操作准备获取分布式锁: {}", LOCK_KEY);
            // 尝试获取锁，最多等待10秒，锁定30秒后自动释放
            if (lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                log.info("[Category] save 成功获取锁: {}", LOCK_KEY);
                try {
                    boolean result = super.save(entity);
                    log.info("[Category] save 完成数据库写入，准备清除缓存");
                    return result;
                } finally {
                    // 释放锁
                    if (lock.isHeldByCurrentThread()) {
                        log.info("[Category] save 释放锁: {}", LOCK_KEY);
                        lock.unlock();
                    }
                }
            } else {
                log.info("[Category] save 获取锁失败: {}", LOCK_KEY);
                throw new RuntimeException("获取分布式锁失败，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取分布式锁被中断", e);
        }
    }

    /**
     * 批量保存分类，使用分布式锁，并清除缓存
     */
    @CacheEvict(value = "category", allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean saveBatch(List<CategoryEntity> entityList) {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            log.info("[Category] saveBatch 操作准备获取分布式锁: {}", LOCK_KEY);
            if (lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                log.info("[Category] saveBatch 成功获取锁: {}", LOCK_KEY);
                try {
                    boolean result = super.saveBatch(entityList);
                    log.info("[Category] saveBatch 完成数据库写入，准备清除缓存");
                    return result;
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        log.info("[Category] saveBatch 释放锁: {}", LOCK_KEY);
                        lock.unlock();
                    }
                }
            } else {
                log.info("[Category] saveBatch 获取锁失败: {}", LOCK_KEY);
                throw new RuntimeException("获取分布式锁失败，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取分布式锁被中断", e);
        }
    }

    /**
     * 更新分类，使用分布式锁，并清除缓存
     */
    @CacheEvict(value = "category", allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean updateById(CategoryEntity entity) {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            log.info("[Category] update 操作准备获取分布式锁: {}", LOCK_KEY);
            if (lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                log.info("[Category] update 成功获取锁: {}", LOCK_KEY);
                try {
                    boolean result = super.updateById(entity);
                    log.info("[Category] update 完成数据库写入，准备清除缓存");
                    return result;
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        log.info("[Category] update 释放锁: {}", LOCK_KEY);
                        lock.unlock();
                    }
                }
            } else {
                log.info("[Category] update 获取锁失败: {}", LOCK_KEY);
                throw new RuntimeException("获取分布式锁失败，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取分布式锁被中断", e);
        }
    }

    /**
     * 批量删除分类，使用分布式锁，并清除缓存
     */
    @CacheEvict(value = "category", allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean removeByIds(List<?> idList) {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            log.info("[Category] remove 操作准备获取分布式锁: {}", LOCK_KEY);
            if (lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                log.info("[Category] remove 成功获取锁: {}", LOCK_KEY);
                try {
                    // 把关必须在锁【里面】做：在锁外面先查一遍再进来删，
                    // 「查」和「删」之间没有保护 —— 这中间有人新建一个子分类，
                    // 检查就白做了，照样会留下看不见的孤儿分类。
                    List<Long> ids = idList.stream()
                            .map(id -> Long.valueOf(String.valueOf(id)))
                            .collect(Collectors.toList());
                    long orphans = countChildrenOutside(ids);
                    if (orphans > 0) {
                        throw new IllegalStateException(
                                "有 " + orphans + " 个子分类挂在待删除的分类下面，请先处理子分类");
                    }

                    boolean result = super.removeByIds(idList);
                    log.info("[Category] remove 完成数据库写入，准备清除缓存");
                    return result;
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        log.info("[Category] remove 释放锁: {}", LOCK_KEY);
                        lock.unlock();
                    }
                }
            } else {
                log.info("[Category] remove 获取锁失败: {}", LOCK_KEY);
                throw new RuntimeException("获取分布式锁失败，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取分布式锁被中断", e);
        }
    }

    
    private void setChildren(CategoryEntity parent, Map<Long, List<CategoryEntity>> parentMap) {
        List<CategoryEntity> children = parentMap.getOrDefault(parent.getCatId(), Collections.emptyList());

        // Set children recursively
        children.forEach(child -> setChildren(child, parentMap));

        // Sort
        children.sort(Comparator.comparingInt(o -> (o.getSort() == null ? 0 : o.getSort())));

        parent.setChildren(children);
    }

    /**
     * 批量更新分类，使用分布式锁，并清除缓存。
     *
     * <h3>为什么必须重写这个方法</h3>
     * listAsTree() 是 @Cacheable 的，所以每一个会改动分类的写操作
     * 都必须清缓存，否则界面上看到的是旧树。
     * save / saveBatch / updateById / removeByIds 都已经重写并加了 @CacheEvict，
     * 唯独 updateBatchById 没有 —— 它一直没被用到，所以这个洞一直没暴露。
     *
     * 2026-09-04 把 /product/category/save/drag 从 saveBatch 改成 updateBatchById
     * 之后它就暴露了，而且暴露的形式最坏：<b>接口返回 success，数据也确实写进了
     * 数据库，但树接口返回的还是旧值</b>——看起来就像「保存没生效」。
     * 实测确认过：改 sort 为 42 后，不走缓存的 /product/category/info/{id} 返回 42，
     * 而 @Cacheable 的 list/tree 仍然返回 100。
     *
     * 换句话说：修好一个「报错但不写」的 bug，换来了一个「写了但看不见」的 bug。
     * 后者更难查，因为它不报错。
     */
    @CacheEvict(value = "category", allEntries = true)
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean updateBatchById(Collection<CategoryEntity> entityList) {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            log.info("[Category] updateBatch 操作准备获取分布式锁: {}", LOCK_KEY);
            if (lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                log.info("[Category] updateBatch 成功获取锁: {}", LOCK_KEY);
                try {
                    boolean result = super.updateBatchById(entityList);
                    log.info("[Category] updateBatch 完成数据库写入，准备清除缓存");
                    return result;
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        log.info("[Category] updateBatch 释放锁: {}", LOCK_KEY);
                        lock.unlock();
                    }
                }
            } else {
                log.info("[Category] updateBatch 获取锁失败: {}", LOCK_KEY);
                throw new RuntimeException("获取分布式锁失败，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取分布式锁被中断", e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * 用 count 而不是把子分类查出来：这里只需要「有没有、有几个」，
     * 查出实体是白花的 IO，而分类表在真实电商里可以有上万行。
     *
     * 不用显式过滤 deleted —— CategoryEntity 上有 @TableLogic，
     * MyBatis-Plus 会自动给查询加上 deleted = 0。
     * （这一点值得写出来：看代码的人会以为漏了删除标记的过滤。）
     */
    @Override
    public long countChildrenOutside(List<Long> catIds) {
        if (CollectionUtils.isEmpty(catIds)) {
            return 0L;
        }
        QueryWrapper<CategoryEntity> wrapper = new QueryWrapper<>();
        wrapper.in("parent_cid", catIds).notIn("cat_id", catIds);
        return this.count(wrapper);
    }

}