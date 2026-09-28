package com.mall.order.reco;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.mall.common.cache.MultiLevelCacheClient;
import com.mall.order.dao.RecoActiveDao;
import com.mall.order.dao.SpuComplementDao;
import com.mall.order.entity.RecoActiveEntity;
import com.mall.order.entity.SpuComplementEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComplementQueryServiceTest {

    private RecoActiveDao activeDao;
    private SpuComplementDao complementDao;
    private MultiLevelCacheClient cache;
    private ComplementQueryService service;
    private final List<String> cacheKeys = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        activeDao = mock(RecoActiveDao.class);
        complementDao = mock(SpuComplementDao.class);
        cache = mock(MultiLevelCacheClient.class);
        // 缓存透传：直接调 loader，同时记下缓存名和键
        when(cache.get(anyString(), anyString(), any(), any(), any())).thenAnswer(inv -> {
            cacheKeys.add(inv.getArgument(0) + "|" + inv.getArgument(1));
            return ((Supplier<Object>) inv.getArgument(3)).get();
        });
        service = new ComplementQueryService(activeDao, complementDao, cache);
    }

    private void activate(long batchId) {
        RecoActiveEntity a = new RecoActiveEntity();
        a.setKind("complement");
        a.setBatchId(batchId);
        when(activeDao.selectById("complement")).thenReturn(a);
    }

    private static List<SpuComplementEntity> rows(long batch, long spu, int n) {
        List<SpuComplementEntity> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            SpuComplementEntity e = new SpuComplementEntity();
            e.setBatchId(batch);
            e.setSpuId(spu);
            e.setRankNo(i);
            e.setComplementSpuId(1000L + i);
            e.setScore(100.0 - i);
            out.add(e);
        }
        return out;
    }

    @Test
    void noActiveBatchMeansNoRecommendationsAndNoRowQuery() {
        assertThat(service.complements(7L, 8)).isEmpty();
        verify(complementDao, never()).selectList(any(Wrapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void returnsRowsInRankOrderTruncatedToSize() {
        activate(5);
        when(complementDao.selectList(any(Wrapper.class))).thenReturn(rows(5, 7, 20));
        List<ComplementQueryService.Item> got = service.complements(7L, 8);
        assertThat(got).extracting(ComplementQueryService.Item::spuId)
                .containsExactly(1001L, 1002L, 1003L, 1004L, 1005L, 1006L, 1007L, 1008L);
        assertThat(service.complements(7L, 500)).hasSize(ComplementQueryService.MAX_SIZE);
        assertThat(service.complements(7L, 0)).hasSize(1);
    }

    /** 明细的缓存键必须带批次号：指针一切就读新批次，不会继续吃旧批次的缓存 */
    @Test
    @SuppressWarnings("unchecked")
    void rowCacheKeyFollowsTheActiveBatch() {
        activate(5);
        when(complementDao.selectList(any(Wrapper.class))).thenReturn(rows(5, 7, 3));
        service.complements(7L, 8);
        activate(6);
        service.complements(7L, 8);
        assertThat(cacheKeys).contains(ComplementQueryService.CACHE_ROWS + "|5:7", ComplementQueryService.CACHE_ROWS + "|6:7");
    }

    @Test
    @SuppressWarnings("unchecked")
    void spuWithoutRowsGetsAnEmptyList() {
        activate(5);
        when(complementDao.selectList(any(Wrapper.class))).thenReturn(List.of());
        assertThat(service.complements(7L, 8)).isEmpty();
        verify(complementDao).selectList(any(Wrapper.class));
        verify(activeDao).selectById(eq("complement"));
    }
}
