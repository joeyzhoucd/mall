package com.mall.order.service.impl;

import com.mall.common.constant.ResponseKeys;
import com.mall.common.metrics.BusinessMetrics;
import com.mall.common.utils.R;
import com.mall.order.constant.OrderConstant;
import com.mall.order.dao.OrderDao;
import com.mall.order.feign.CartFeignService;
import com.mall.order.feign.CouponFeignService;
import com.mall.order.feign.MemberFeignService;
import com.mall.order.feign.WareFeignService;
import com.mall.order.interceptor.OrderInterceptor;
import com.mall.order.service.OrderItemService;
import com.mall.order.service.OrderOutboxMessageService;
import com.mall.order.to.UserInfoTo;
import com.mall.order.vo.OrderSubmitVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下单只在落库那一小段持有连接：每个 Feign 调用发生时<b>不在事务里</b>，三条写入发生时在。
 *
 * <p>2026-09-29 之前整个 submitOrder 是一个 @Transactional —— Tempo 实测一单连接占 366ms，SQL 只有 14ms。
 * 前三条用一个真实维护「当前是否有事务」的假事务管理器，检查每一步发生在事务内还是外。
 * 它们不经过 Spring 代理，所以<b>方法上的 @Transactional 在这里不生效</b> —— 把它加回来要靠最后一条
 * 反射检查拦住。
 */
class SubmitOrderTransactionScopeTest {

    private static final long MEMBER = 8000001L, ADDR = 55L;

    /** 不碰数据库的事务管理器：Spring 照常维护事务同步状态，只数提交 / 回滚次数 */
    static class CountingTxManager extends AbstractPlatformTransactionManager {
        int commits, rollbacks;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commits++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rollbacks++;
        }
    }

    private final List<String> events = new ArrayList<>();
    private MemberFeignService member;
    private CartFeignService cart;
    private WareFeignService ware;
    private OrderDao orderDao;
    private OrderItemService items;
    private OrderOutboxMessageService outbox;
    private CountingTxManager tx;
    private OrderServiceImpl service;

    /** 记下调用名和调用那一刻是否在事务里 */
    private <T> Answer<T> record(String name, T result) {
        return inv -> {
            events.add(name + (TransactionSynchronizationManager.isActualTransactionActive() ? "@tx" : "@none"));
            return result;
        };
    }

    @BeforeEach
    void setUp() {
        member = mock(MemberFeignService.class);
        cart = mock(CartFeignService.class);
        ware = mock(WareFeignService.class);
        orderDao = mock(OrderDao.class);
        items = mock(OrderItemService.class);
        outbox = mock(OrderOutboxMessageService.class);
        tx = new CountingTxManager();

        when(member.getAddress(MEMBER)).thenAnswer(record("member.getAddress",
                R.ok().put(ResponseKeys.ADDRESS, List.of(Map.of("id", ADDR, "name", "张三")))));
        when(cart.getCurrentUserCartItems()).thenAnswer(record("cart.items", R.ok().put(ResponseKeys.ITEMS,
                List.of(Map.of("skuId", 1001, "title", "x", "price", 10, "count", 2)))));
        when(ware.orderLockStock(any())).thenAnswer(record("ware.lock", R.ok()));
        when(cart.deleteItems(anyList())).thenAnswer(record("cart.delete", R.ok()));
        when(orderDao.insert(any(com.mall.order.entity.OrderEntity.class))).thenAnswer(record("db.insertOrder", 1));
        when(items.saveBatch(any())).thenAnswer(record("db.insertItems", true));
        when(outbox.enqueue(anyString(), anyString(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(inv -> {
                    events.add("outbox:" + inv.getArgument(1)
                            + (TransactionSynchronizationManager.isActualTransactionActive() ? "@tx" : "@none"));
                    return null;
                });

        service = new OrderServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", orderDao);
        ReflectionTestUtils.setField(service, "businessMetrics", mock(BusinessMetrics.class));
        ReflectionTestUtils.setField(service, "memberFeignService", member);
        ReflectionTestUtils.setField(service, "cartFeignService", cart);
        ReflectionTestUtils.setField(service, "wareFeignService", ware);
        ReflectionTestUtils.setField(service, "couponFeignService", mock(CouponFeignService.class));
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "orderItemService", items);
        ReflectionTestUtils.setField(service, "orderOutboxMessageService", outbox);
        ReflectionTestUtils.setField(service, "internalToken", "t");
        ReflectionTestUtils.setField(service, "transactionTemplate", new TransactionTemplate(tx));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(OrderConstant.ORDER_TOKEN_PREFIX + MEMBER, "tok");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        UserInfoTo user = new UserInfoTo();
        user.setUserId(MEMBER);
        user.setUsername("lt0001");
        OrderInterceptor.threadLocal.set(user);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        OrderInterceptor.threadLocal.remove();
    }

    private static OrderSubmitVo submit() {
        OrderSubmitVo vo = new OrderSubmitVo();
        vo.setOrderToken("tok");
        vo.setAddrId(ADDR);
        vo.setPayType(1);
        return vo;
    }

    @Test
    void remoteCallsRunOutsideTheTransactionAndOnlyTheWritesRunInside() {
        assertThat(service.submitOrder(submit()).getCode()).isZero();

        assertThat(events).containsExactly(
                "member.getAddress@none",          // 只查一次（原来查两次）
                "cart.items@none",
                "ware.lock@none",
                "db.insertOrder@tx",
                "db.insertItems@tx",
                "outbox:ORDER_CLOSE@tx",
                "cart.delete@none");               // 提交之后才清购物车
        assertThat(tx.commits).isEqualTo(1);
    }

    @Test
    void persistFailureReleasesStockFromMemoryInItsOwnTransaction() {
        when(orderDao.insert(any(com.mall.order.entity.OrderEntity.class))).thenThrow(new RuntimeException("Deadlock found"));

        assertThat(service.submitOrder(submit()).getCode()).isEqualTo(1);

        assertThat(tx.rollbacks).as("落库事务回滚").isEqualTo(1);
        assertThat(events).contains("outbox:STOCK_RELEASE@tx");   // 释放消息确实写了、在自己的事务里
        assertThat(tx.commits).as("释放消息的那个事务提交了").isEqualTo(1);
        verify(outbox).enqueue(startsWith("stock.release."), eq("STOCK_RELEASE"), anyString(), anyString(), anyString(), any());
        verify(cart, never()).deleteItems(anyList());
    }

    /** 上面几条不经过代理、看不见注解；这条专门拦「有人把 @Transactional 加回 submitOrder」 */
    @Test
    void submitOrderIsNotTransactional() throws Exception {
        assertThat(OrderServiceImpl.class.getMethod("submitOrder", OrderSubmitVo.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                .as("submitOrder 上不能有 @Transactional：它会让连接跨所有 Feign 调用被占着").isFalse();
        assertThat(OrderServiceImpl.class.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                .as("类级 @Transactional 同样会套住 submitOrder").isFalse();
    }

    @Test
    void cartCleanupFailureDoesNotFailTheCommittedOrder() {
        when(cart.deleteItems(anyList())).thenThrow(new RuntimeException("mall-cart 超时"));

        assertThat(service.submitOrder(submit()).getCode()).isZero();
        assertThat(tx.rollbacks).isZero();
        verify(member, times(1)).getAddress(anyLong());
    }
}
