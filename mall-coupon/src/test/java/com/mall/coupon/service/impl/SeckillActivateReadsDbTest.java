package com.mall.coupon.service.impl;

import com.mall.coupon.entity.SeckillSkuRelationEntity;
import com.mall.coupon.feign.ProductFeignService;
import com.mall.coupon.service.SeckillSkuRelationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 激活必须读库，不能读 getById 的缓存。
 *
 * <p>激活把秒杀价和库存固化进 Redis，之后整场活动只认那两个 key，而且不允许再激活一次。
 * 2026-09-28 修多级缓存「从未命中」的 bug 之前，getById 的缓存等于没开，这里读哪个都一样；
 * 缓存真正生效之后，后台刚改完价格、失效消息还没到时点激活，就会按旧价旧量卖完整场。
 * 所以这里让缓存和库给出不同的值，断言写进 Redis 的是库里的。
 */
class SeckillActivateReadsDbTest {

    @Test
    @SuppressWarnings("unchecked")
    void activateFreezesTheDatabaseRowNotTheCachedOne() {
        SeckillSkuRelationService relations = mock(SeckillSkuRelationService.class);
        when(relations.getById(anyLong())).thenReturn(relation("99.00", 500));   // 缓存里的旧行
        when(relations.getByIdFromDb(7L)).thenReturn(relation("59.00", 300));   // 库里刚改过

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.opsForHash()).thenReturn(hashes);

        ProductFeignService product = mock(ProductFeignService.class);
        when(product.getSkuInfo(anyLong())).thenThrow(new RuntimeException("商品名/图不影响这条断言"));

        SeckillGrabServiceImpl service = new SeckillGrabServiceImpl();
        ReflectionTestUtils.setField(service, "seckillSkuRelationService", relations);
        ReflectionTestUtils.setField(service, "redisTemplate", redis);
        ReflectionTestUtils.setField(service, "productFeignService", product);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());

        assertThat(service.activate(7L)).isTrue();

        verify(values).set("seckill:stock:7", "300");
        ArgumentCaptor<Map<Object, Object>> info = ArgumentCaptor.forClass(Map.class);
        verify(hashes).putAll(eq("seckill:info:7"), info.capture());
        assertThat(info.getValue()).containsEntry("seckillPrice", "59.00");
    }

    private static SeckillSkuRelationEntity relation(String price, int count) {
        SeckillSkuRelationEntity r = new SeckillSkuRelationEntity();
        r.setId(7L);
        r.setSkuId(1001L);
        r.setSeckillPrice(new BigDecimal(price));
        r.setSeckillCount(new BigDecimal(count));
        return r;
    }
}
