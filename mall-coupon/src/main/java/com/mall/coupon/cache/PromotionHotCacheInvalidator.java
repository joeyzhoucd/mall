package com.mall.coupon.cache;

import com.mall.common.cache.MultiLevelCacheClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Objects;

@Component
public class PromotionHotCacheInvalidator {

    public static final String SECKILL_RELATION_CACHE_NAME = "coupon:seckill-relation";
    public static final String SECKILL_SESSION_CACHE_NAME = "coupon:seckill-session";
    public static final String SECKILL_PAGE_CACHE_NAME = "coupon:seckill-page";
    public static final String HOME_ADV_ACTIVE_CACHE_NAME = "coupon:home-adv-active";
    public static final String HOME_SUBJECT_ACTIVE_CACHE_NAME = "coupon:home-subject-active";
    public static final String HOME_SUBJECT_SPU_CACHE_NAME = "coupon:home-subject-spu";

    public static final String HOME_ACTIVE_CACHE_KEY = "active";

    private final MultiLevelCacheClient multiLevelCacheClient;

    public PromotionHotCacheInvalidator(MultiLevelCacheClient multiLevelCacheClient) {
        this.multiLevelCacheClient = multiLevelCacheClient;
    }

    public static String key(Long id) {
        return String.valueOf(id);
    }

    public void evictRelation(Long relationId) {
        if (relationId == null) {
            return;
        }
        String key = key(relationId);
        multiLevelCacheClient.evict(SECKILL_RELATION_CACHE_NAME, key);
        multiLevelCacheClient.evict(SECKILL_PAGE_CACHE_NAME, key);
    }

    public void evictRelations(Collection<Long> relationIds) {
        if (CollectionUtils.isEmpty(relationIds)) {
            return;
        }
        relationIds.stream().filter(Objects::nonNull).distinct().forEach(this::evictRelation);
    }

    public void evictSession(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        multiLevelCacheClient.evict(SECKILL_SESSION_CACHE_NAME, key(sessionId));
    }

    public void evictSessions(Collection<Long> sessionIds) {
        if (CollectionUtils.isEmpty(sessionIds)) {
            return;
        }
        sessionIds.stream().filter(Objects::nonNull).distinct().forEach(this::evictSession);
    }

    public void evictHomeAdv() {
        multiLevelCacheClient.evict(HOME_ADV_ACTIVE_CACHE_NAME, HOME_ACTIVE_CACHE_KEY);
    }

    public void evictHomeSubjects() {
        multiLevelCacheClient.evict(HOME_SUBJECT_ACTIVE_CACHE_NAME, HOME_ACTIVE_CACHE_KEY);
    }

    public void evictHomeSubjectSpu(Long subjectId) {
        if (subjectId == null) {
            return;
        }
        multiLevelCacheClient.evict(HOME_SUBJECT_SPU_CACHE_NAME, key(subjectId));
    }

    public void evictHomeSubjectSpus(Collection<Long> subjectIds) {
        if (CollectionUtils.isEmpty(subjectIds)) {
            return;
        }
        subjectIds.stream().filter(Objects::nonNull).distinct().forEach(this::evictHomeSubjectSpu);
    }

    public void evictRelationAfterCommit(Long relationId) {
        afterCommit(() -> evictRelation(relationId));
    }

    public void evictRelationsAfterCommit(Collection<Long> relationIds) {
        afterCommit(() -> evictRelations(relationIds));
    }

    public void evictSessionAfterCommit(Long sessionId) {
        afterCommit(() -> evictSession(sessionId));
    }

    public void evictSessionsAfterCommit(Collection<Long> sessionIds) {
        afterCommit(() -> evictSessions(sessionIds));
    }

    public void evictHomeAdvAfterCommit() {
        afterCommit(this::evictHomeAdv);
    }

    public void evictHomeSubjectsAfterCommit() {
        afterCommit(this::evictHomeSubjects);
    }

    public void evictHomeSubjectSpuAfterCommit(Long subjectId) {
        afterCommit(() -> evictHomeSubjectSpu(subjectId));
    }

    public void evictHomeSubjectSpusAfterCommit(Collection<Long> subjectIds) {
        afterCommit(() -> evictHomeSubjectSpus(subjectIds));
    }

    public void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }
        task.run();
    }
}
