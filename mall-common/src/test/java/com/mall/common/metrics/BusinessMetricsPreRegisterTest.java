package com.mall.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预注册 0 值计数器（{@link BusinessMetrics#preRegister}）和它依赖的清单 {@link BusinessFlow#FAILURE_REASONS}。
 *
 * <h3>要防的两件事</h3>
 * <ul>
 *   <li>零流量时序列不存在 —— 面板 No data、无法用 absent() 告警（这次改动的目的）；</li>
 *   <li>反方向：一个服务把<b>不归它管</b>的流也注册出来，发布一条永远是 0 的幽灵序列，
 *       面板上像「那条流没流量」。所以必须只注册传进来的流（负控在下面）。</li>
 * </ul>
 */
class BusinessMetricsPreRegisterTest {

    private static Counter find(SimpleMeterRegistry reg, String flow, String result, String reason) {
        return reg.find(BusinessMetrics.METRIC_NAME)
                .tag("flow", flow).tag("result", result).tag("reason", reason).counter();
    }

    private static Set<String> flowsIn(SimpleMeterRegistry reg) {
        Set<String> flows = new HashSet<>();
        for (Meter m : reg.getMeters()) {
            if (BusinessMetrics.METRIC_NAME.equals(m.getId().getName())) {
                flows.add(m.getId().getTag("flow"));
            }
        }
        return flows;
    }

    @Test
    @DisplayName("预注册：成功 + 清单里每个失败原因各一条，值都是 0")
    void preRegistersZeroCountersForEveryListedCombination() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        new BusinessMetrics(reg).preRegister(List.of(BusinessFlow.STOCK_LOCK));

        Counter ok = find(reg, BusinessFlow.STOCK_LOCK, "success", "none");
        assertNotNull(ok, "成功序列必须在零流量时就存在");
        assertEquals(0.0, ok.count());
        for (String reason : BusinessFlow.FAILURE_REASONS.get(BusinessFlow.STOCK_LOCK)) {
            Counter c = find(reg, BusinessFlow.STOCK_LOCK, "failure", reason);
            assertNotNull(c, "失败原因 " + reason + " 的序列必须在零流量时就存在");
            assertEquals(0.0, c.count());
        }
    }

    @Test
    @DisplayName("负控：只注册传进来的流，别的流一条都不出现（否则就是幽灵序列）")
    void registersOnlyTheGivenFlows() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        new BusinessMetrics(reg).preRegister(List.of(BusinessFlow.ORDER_SUBMIT));

        assertEquals(Set.of(BusinessFlow.ORDER_SUBMIT), flowsIn(reg));
    }

    @Test
    @DisplayName("清单里没有的流：抛异常（配置写错一个字母不能变成「静默什么都没注册」）")
    void unknownFlowFailsLoudly() {
        BusinessMetrics metrics = new BusinessMetrics(new SimpleMeterRegistry());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> metrics.preRegister(List.of("order.sumbit")));
        assertTrue(e.getMessage().contains("order.sumbit"));
    }

    @Test
    @DisplayName("预注册之后照常计数落在同一个计数器上，不会多出一份")
    void recordAfterPreRegisterHitsTheSameCounter() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        BusinessMetrics metrics = new BusinessMetrics(reg);
        metrics.preRegister(List.of(BusinessFlow.STOCK_UNLOCK));
        int before = reg.getMeters().size();

        metrics.failure(BusinessFlow.STOCK_UNLOCK, BusinessFlow.REASON_CAS_LOST);
        metrics.success(BusinessFlow.STOCK_UNLOCK);

        assertEquals(before, reg.getMeters().size());
        assertEquals(1.0, find(reg, BusinessFlow.STOCK_UNLOCK, "failure", BusinessFlow.REASON_CAS_LOST).count());
        assertEquals(1.0, find(reg, BusinessFlow.STOCK_UNLOCK, "success", "none").count());
    }

    @Test
    @DisplayName("清单完整：BusinessFlow 里每个业务流常量都在清单里（漏了 = 那条流永远不会被预注册）")
    void everyFlowConstantIsListed() throws IllegalAccessException {
        int flows = 0;
        for (Field f : BusinessFlow.class.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod) && Modifier.isFinal(mod) && f.getType() == String.class
                    && !f.getName().startsWith("REASON_")) {
                String flow = (String) f.get(null);
                assertTrue(BusinessFlow.FAILURE_REASONS.containsKey(flow), "业务流 " + flow + " 不在 FAILURE_REASONS 里");
                flows++;
            }
        }
        // 正控：确认上面的反射真的找到了流常量（找到 0 个时循环体一次不跑、测试照样绿）
        assertEquals(BusinessFlow.FAILURE_REASONS.size(), flows);
    }

    @Test
    @DisplayName("清单自洽：同一条流的原因不重复，组合数在基数护栏之内")
    void catalogIsWellFormed() {
        BusinessFlow.FAILURE_REASONS.forEach((flow, reasons) -> {
            assertEquals(reasons.size(), new HashSet<>(reasons).size(), flow + " 的失败原因有重复");
            // +1 是成功那条；预注册的组合也计入护栏，超了运行时新原因会被归并到 _overflow
            assertTrue(reasons.size() + 1 <= BusinessMetrics.MAX_COMBINATIONS_PER_FLOW,
                    flow + " 的组合数超过护栏上限");
            reasons.forEach(r -> assertTrue(r.matches("[a-z][a-z0-9_]*"), flow + " 的原因 '" + r + "' 不是小写下划线"));
        });
    }
}
