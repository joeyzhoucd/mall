package com.mall.order.reco;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mall.common.cache.MultiLevelCacheClient;
import com.mall.common.cache.MultiLevelCacheOptions;
import com.mall.order.dao.RecoActiveDao;
import com.mall.order.dao.SpuComplementDao;
import com.mall.order.entity.RecoActiveEntity;
import com.mall.order.entity.SpuComplementEntity;
import com.mall.order.reco.batch.BatchConfig;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.List;

/**
 * 读「搭配购买」当前生效批次：指针 → 该 SPU 的前 K 行。
 *
 * <h3>两层缓存，TTL 不一样是有意的</h3>
 * <ul>
 *   <li><b>指针</b>短缓存（本地 30s / Redis 60s）：批次一天切一次，切了之后最多约 1.5 分钟生效。</li>
 *   <li><b>明细</b>长缓存（本地 10min / Redis 1h），键里带 batchId：一个批次的明细写完就不再变，
 *       指针一切，键自然换了 —— 不需要任何失效逻辑，也不会读到新旧两批拼起来的结果。
 *       没有搭配的 SPU 缓存空列表，否则长尾商品每次详情页都打一次库。</li>
 * </ul>
 */
@Service
public class ComplementQueryService {

    public static final int MAX_SIZE = 20;
    static final String CACHE_POINTER = "reco:active";
    static final String CACHE_ROWS = "reco:complement";

    private static final MultiLevelCacheOptions POINTER_OPTIONS = new MultiLevelCacheOptions(
            Duration.ofSeconds(30), Duration.ofSeconds(60), Duration.ofSeconds(30), true,
            Duration.ofSeconds(3), Duration.ofMillis(150), Duration.ofMillis(20), 0.1);
    private static final MultiLevelCacheOptions ROWS_OPTIONS = new MultiLevelCacheOptions(
            Duration.ofMinutes(10), Duration.ofHours(1), Duration.ofMinutes(10), true,
            Duration.ofSeconds(3), Duration.ofMillis(150), Duration.ofMillis(20), 0.1);
    private static final TypeReference<Long> LONG = new TypeReference<>() {
    };
    private static final TypeReference<List<Item>> ITEMS = new TypeReference<>() {
    };

    public record Item(Long spuId, Double score) {
    }

    private final RecoActiveDao activeDao;
    private final SpuComplementDao complementDao;
    private final MultiLevelCacheClient cache;

    public ComplementQueryService(RecoActiveDao activeDao, SpuComplementDao complementDao, MultiLevelCacheClient cache) {
        this.activeDao = activeDao;
        this.complementDao = complementDao;
        this.cache = cache;
    }

    /** 当前生效批次；还没有任何批次时为 null（详情页于是不出这一块） */
    public Long activeBatchId() {
        return cache.get(CACHE_POINTER, BatchConfig.KIND, LONG, () -> {
            RecoActiveEntity a = activeDao.selectById(BatchConfig.KIND);
            return a == null ? null : a.getBatchId();
        }, POINTER_OPTIONS);
    }

    public List<Item> complements(long spuId, int size) {
        int n = Math.max(1, Math.min(size, MAX_SIZE));
        Long batch = activeBatchId();
        if (batch == null) {
            return List.of();
        }
        List<Item> all = cache.get(CACHE_ROWS, batch + ":" + spuId, ITEMS,
                () -> complementDao.selectList(new QueryWrapper<SpuComplementEntity>()
                                .eq("batch_id", batch)
                                .eq("spu_id", spuId)
                                .orderByAsc("rank_no"))
                        .stream()
                        .map(r -> new Item(r.getComplementSpuId(), r.getScore()))
                        .toList(),
                ROWS_OPTIONS);
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        return all.size() <= n ? all : all.subList(0, n);
    }
}
