package com.mall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.mall.common.constant.MqConstants;
import com.mall.common.constant.OrderStatus;
import com.mall.common.constant.ResponseKeys;
import com.mall.common.to.OrderCloseTo;
import com.mall.common.to.StockDeductTo;
import com.mall.common.to.StockReleaseItemTo;
import com.mall.common.to.StockReleaseTo;
import com.mall.common.to.OrderOperateTo;
import com.mall.common.to.SeckillOrderTo;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.common.metrics.BusinessFlow;
import com.mall.common.utils.R;
import com.mall.common.utils.RUtils;
import com.mall.order.constant.OrderConstant;
import com.mall.order.dao.OrderDao;
import com.mall.order.entity.OrderEntity;
import com.mall.order.entity.OrderItemEntity;
import com.mall.order.entity.OrderOperateHistoryEntity;
import com.mall.order.feign.CartFeignService;
import com.mall.order.feign.CouponFeignService;
import com.mall.order.feign.MemberFeignService;
import com.mall.order.feign.WareFeignService;
import com.mall.order.interceptor.OrderInterceptor;
import com.mall.order.service.OrderItemService;
import com.mall.order.service.OrderOperateHistoryService;
import com.mall.order.service.OrderOutboxMessageService;
import com.mall.order.service.OrderService;
import com.mall.order.to.OrderCreateTo;
import com.mall.order.to.UserInfoTo;
import com.mall.order.vo.MemberAddressVo;
import com.mall.order.vo.OrderConfirmVo;
import com.mall.order.vo.OrderItemLockVo;
import com.mall.order.vo.OrderItemVo;
import com.mall.order.vo.OrderSubmitVo;
import com.mall.order.vo.SubmitOrderResponseVo;
import com.mall.order.vo.WareSkuLockVo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {

    @Autowired
    private com.mall.common.metrics.BusinessMetrics businessMetrics;

    @Autowired
    private MemberFeignService memberFeignService;

    @Autowired
    private CartFeignService cartFeignService;

    @Autowired
    private WareFeignService wareFeignService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderItemService orderItemService;

    @Autowired
    private OrderOperateHistoryService orderOperateHistoryService;

    @Autowired
    private OrderOutboxMessageService orderOutboxMessageService;

    @Autowired
    private CouponFeignService couponFeignService;

    @Value("${mall.seckill.internal-token}")
    private String internalToken;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderServiceImpl.class);

    /**
     * 后台的订单分页查询。
     *
     * <h3>原来这里是生成器的裸模板</h3>
     * 空 QueryWrapper、没有任何筛选、也<b>没有 ORDER BY</b>。
     * 后果分两层：
     * <ul>
     *   <li>没有筛选 → 后台只能一页页翻，没法按订单号或状态找单，
     *       而「找一个具体的订单」正是订单管理最主要的用途</li>
     *   <li>没有 ORDER BY → MySQL 不保证无序查询的行顺序，
     *       分页的每一页都是独立查询，行会在页与页之间重复或漏掉。
     *       数据少时看不出来 —— 就像 spuinfo 那次，10004 行时
     *       第 1、2 页实测重复 8 行。</li>
     * </ul>
     *
     * <h3>排序为什么要带上 id</h3>
     * create_time 不唯一（批量下单、秒杀会在同一秒里产生大量订单），
     * 只按它排，并列的行之间顺序仍然未定义。必须落到唯一列上全序才确定。
     */
    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                buildQueryWrapper(params));
        return new PageUtils(page);
    }

    /**
     * 条件拼装单独抽出来，为的是<b>可测</b>：queryPage 要连数据库，而这一段是纯逻辑。
     *
     * 【这个抽取不是可有可无的】第一版测试自己复刻了一份拼装逻辑来测，
     * 结果把生产代码里的 orderByDesc 整行删掉，8 条测试<b>全部照常通过</b> ——
     * 因为它们测的是复刻的那一份。一个永远通过的测试比没有测试更糟。
     * 对应的测试见 OrderQueryPageTest，它现在调的是这个方法。
     */
    QueryWrapper<OrderEntity> buildQueryWrapper(Map<String, Object> params) {
        QueryWrapper<OrderEntity> wrapper = new QueryWrapper<>();

        // 订单号：精确匹配。订单号是给人报出来的东西（客服问「您的订单号是多少」），
        // 用 like 会让人输错一位也能查到别的单子，反而更糟。
        String orderSn = trimmed(params.get("orderSn"));
        if (orderSn != null) {
            wrapper.eq("order_sn", orderSn);
        }

        // 收件人 / 会员名：这两个是模糊找人用的
        String key = trimmed(params.get("key"));
        if (key != null) {
            wrapper.and(w -> w.like("receiver_name", key)
                    .or().like("member_username", key)
                    .or().like("receiver_phone", key));
        }

        Integer status = parseInt(params.get("status"));
        if (status != null) {
            wrapper.eq("status", status);
        }

        Long memberId = parseLong(params.get("memberId"));
        if (memberId != null) {
            wrapper.eq("member_id", memberId);
        }

        // 时间范围。两端都可以单独给 —— 只给开始时间是「这之后的所有订单」。
        String from = trimmed(params.get("createTimeFrom"));
        if (from != null) {
            wrapper.ge("create_time", from);
        }
        String to = trimmed(params.get("createTimeTo"));
        if (to != null) {
            wrapper.le("create_time", to);
        }

        wrapper.orderByDesc("create_time").orderByDesc("id");

        return wrapper;
    }

    /** 取字符串参数并去空白；空串当作没传，避免拼出恒真/恒假的条件。 */
    private static String trimmed(Object raw) {
        if (raw == null) return null;
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer parseInt(Object raw) {
        String s = trimmed(raw);
        if (s == null) return null;
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            // 解析不了就当没传。抛异常的话，前端传了个空字符串就变成 500，
            // 而这只是一个筛选条件。
            return null;
        }
    }

    private static Long parseLong(Object raw) {
        String s = trimmed(raw);
        if (s == null) return null;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public OrderConfirmVo confirmOrder() {
        OrderConfirmVo confirmVo = new OrderConfirmVo();
        UserInfoTo userInfoTo = OrderInterceptor.threadLocal.get();
        if (userInfoTo == null || userInfoTo.getUserId() == null) {
            return confirmVo;
        }

        R addressResp = memberFeignService.getAddress(userInfoTo.getUserId());
        List<MemberAddressVo> address = RUtils.getData(
                addressResp,
                ResponseKeys.ADDRESS,
                objectMapper,
                new TypeReference<List<MemberAddressVo>>() {}
        );
        confirmVo.setAddress(address == null ? Collections.emptyList() : address);

        List<OrderItemVo> items = getOrderItems();
        confirmVo.setItems(items);
        confirmVo.setIntegration(0);
        confirmVo.setFreightAmount(BigDecimal.ZERO);
        return confirmVo;
    }

    @Override
    public boolean saveAddress(MemberAddressVo addressVo) {
        UserInfoTo userInfoTo = OrderInterceptor.threadLocal.get();
        if (userInfoTo == null || userInfoTo.getUserId() == null) {
            return false;
        }
        addressVo.setMemberId(userInfoTo.getUserId());
        R result = memberFeignService.saveAddress(addressVo);
        return result != null && result.getCode() == 0;
    }

    @Transactional
    @Override
    public SubmitOrderResponseVo submitOrder(OrderSubmitVo submitVo) {
        SubmitOrderResponseVo responseVo = new SubmitOrderResponseVo();
        UserInfoTo userInfoTo = OrderInterceptor.threadLocal.get();
        if (userInfoTo == null || userInfoTo.getUserId() == null) {
            businessMetrics.failure(BusinessFlow.ORDER_SUBMIT, BusinessFlow.REASON_UNAUTHENTICATED);
            responseVo.setCode(1);
            return responseVo;
        }

        if (!verifyToken(userInfoTo.getUserId(), submitVo.getOrderToken())) {
            // 和 persist_failed 分开记：API 上都是 code 1，但这个绝大多数是用户双击，
            // 混进「下单失败率」会让那个指标被无害噪声污染到没法定阈值。
            businessMetrics.failure(BusinessFlow.ORDER_SUBMIT, BusinessFlow.REASON_DUPLICATE_SUBMIT);
            responseVo.setCode(1);
            return responseVo;
        }

        if (submitVo.getAddrId() == null || getAddressById(userInfoTo.getUserId(), submitVo.getAddrId()) == null) {
            businessMetrics.failure(BusinessFlow.ORDER_SUBMIT, BusinessFlow.REASON_ADDRESS_INVALID);
            responseVo.setCode(4);
            return responseVo;
        }

        OrderCreateTo orderCreateTo = createOrder(submitVo, userInfoTo);
        if (submitVo.getPayPrice() != null) {
            BigDecimal delta = orderCreateTo.getPayPrice().subtract(submitVo.getPayPrice()).abs();
            if (delta.compareTo(new BigDecimal("0.01")) > 0) {
                businessMetrics.failure(BusinessFlow.ORDER_SUBMIT, BusinessFlow.REASON_PRICE_CHANGED);
                responseVo.setCode(2);
                return responseVo;
            }
        }

        WareSkuLockVo lockVo = buildLockVo(orderCreateTo);
        R lockResp = wareFeignService.orderLockStock(lockVo);
        if (lockResp == null || lockResp.getCode() != 0) {
            businessMetrics.failure(BusinessFlow.ORDER_SUBMIT, BusinessFlow.REASON_STOCK_LOCK_FAILED);
            responseVo.setCode(3);
            return responseVo;
        }

        try {
            saveOrder(orderCreateTo);
            sendOrderCreateMessage(orderCreateTo.getOrder().getOrderSn());
            clearCartItems(orderCreateTo);
        } catch (Exception e) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            sendStockRelease(orderCreateTo.getOrder().getOrderSn());
            businessMetrics.failure(BusinessFlow.ORDER_SUBMIT, BusinessFlow.REASON_PERSIST_FAILED);
            responseVo.setCode(1);
            return responseVo;
        }

        businessMetrics.success(BusinessFlow.ORDER_SUBMIT);
        responseVo.setCode(0);
        responseVo.setOrder(orderCreateTo.getOrder());
        return responseVo;
    }

    @Override
    @Transactional
    public void closeOrder(String orderSn) {
        if (StringUtils.isBlank(orderSn)) {
            return;
        }
        if (transitOrderStatus(orderSn, OrderStatus.NEW, OrderStatus.CLOSED, null,
                "order timeout closed")) {
            sendStockRelease(orderSn);
        }
    }

    @Override
    @Transactional
    public void payOrderSuccess(String orderSn) {
        if (StringUtils.isBlank(orderSn)) {
            return;
        }
        if (transitOrderStatus(orderSn, OrderStatus.NEW, OrderStatus.PAYED,
                order -> order.setPaymentTime(new Date()), "payment success")) {
            sendStockDeduct(orderSn);
        }
    }

    @Override
    public boolean shipOrder(String orderSn, String deliveryCompany, String deliverySn) {
        return transitOrderStatus(orderSn, OrderStatus.PAYED, OrderStatus.SENT, order -> {
            order.setDeliveryCompany(deliveryCompany);
            order.setDeliverySn(deliverySn);
            order.setDeliveryTime(new Date());
        }, "order shipped");
    }

    @Override
    public boolean receiveOrder(String orderSn) {
        return transitOrderStatus(orderSn, OrderStatus.SENT, OrderStatus.RECEIVED,
                order -> order.setReceiveTime(new Date()), "order received");
    }

    @Override
    public boolean startAfterSale(String orderSn, String note) {
        return transitOrderStatus(orderSn,
                List.of(OrderStatus.PAYED, OrderStatus.SENT, OrderStatus.RECEIVED),
                OrderStatus.SERVICING,
                null,
                StringUtils.defaultIfBlank(note, "after-sale started"));
    }

    @Override
    public boolean finishAfterSale(String orderSn, String note) {
        return transitOrderStatus(orderSn, OrderStatus.SERVICING, OrderStatus.SERVICED,
                null, StringUtils.defaultIfBlank(note, "after-sale completed"));
    }

    @Override
    public OrderEntity getOrderBySn(String orderSn) {
        if (StringUtils.isBlank(orderSn)) {
            return null;
        }
        return this.getOne(new QueryWrapper<OrderEntity>().eq("order_sn", orderSn));
    }

    /** {@inheritDoc} */
    @Override
    public com.mall.order.vo.OrderDetailVo getOrderDetail(String orderSn) {
        OrderEntity order = getOrderBySn(orderSn);
        if (order == null) {
            return null;
        }

        com.mall.order.vo.OrderDetailVo vo = new com.mall.order.vo.OrderDetailVo();
        vo.setOrder(order);

        // 明细按 id 升序 —— 也就是下单时加入购物车的顺序。
        // 不排序的话每次打开同一个订单，商品顺序都可能不一样。
        vo.setItems(orderItemService.list(
                new QueryWrapper<OrderItemEntity>()
                        .eq("order_sn", orderSn)
                        .orderByAsc("id")));

        // 操作记录按【时间倒序】：排查订单问题时先看的总是「最后发生了什么」。
        // 再按 id 倒序兜底 —— create_time 只精确到秒，同一秒内的多条
        // （比如支付回调紧接着自动发货）顺序否则是不确定的，
        // 而操作记录一旦顺序错乱就会把因果关系显示反。
        vo.setHistory(orderOperateHistoryService.list(
                new QueryWrapper<OrderOperateHistoryEntity>()
                        .eq("order_id", order.getId())
                        .orderByDesc("create_time")
                        .orderByDesc("id")));

        return vo;
    }

    @Override
    public void recordOperateHistory(OrderOperateTo operateTo) {
        if (operateTo == null || StringUtils.isBlank(operateTo.getOrderSn())) {
            return;
        }
        OrderEntity orderEntity = getOrderBySn(operateTo.getOrderSn());
        if (orderEntity == null) {
            return;
        }
        com.mall.order.entity.OrderOperateHistoryEntity history = new com.mall.order.entity.OrderOperateHistoryEntity();
        history.setOrderId(orderEntity.getId());
        history.setOrderStatus(operateTo.getStatus());
        history.setOperateMan(StringUtils.isBlank(operateTo.getOperateMan()) ? "system" : operateTo.getOperateMan());
        history.setNote(operateTo.getNote());
        history.setCreateTime(new Date());
        orderOperateHistoryService.save(history);
    }

    private boolean transitOrderStatus(String orderSn,
                                       Integer fromStatus,
                                       Integer toStatus,
                                       Consumer<OrderEntity> customizer,
                                       String note) {
        if (fromStatus == null) {
            return false;
        }
        return transitOrderStatus(orderSn, Collections.singletonList(fromStatus), toStatus, customizer, note);
    }

    private boolean transitOrderStatus(String orderSn,
                                       List<Integer> fromStatuses,
                                       Integer toStatus,
                                       Consumer<OrderEntity> customizer,
                                       String note) {
        if (StringUtils.isBlank(orderSn) || fromStatuses == null || fromStatuses.isEmpty() || toStatus == null) {
            return false;
        }
        List<Integer> legalFromStatuses = fromStatuses.stream()
                .filter(fromStatus -> OrderStatus.canTransit(fromStatus, toStatus))
                .collect(Collectors.toList());
        if (legalFromStatuses.isEmpty()) {
            return false;
        }
        OrderEntity update = new OrderEntity();
        update.setStatus(toStatus);
        update.setModifyTime(new Date());
        if (customizer != null) {
            customizer.accept(update);
        }
        boolean updated = this.update(update, new UpdateWrapper<OrderEntity>()
                .eq("order_sn", orderSn)
                .in("status", legalFromStatuses));
        if (updated) {
            OrderOperateTo operateTo = new OrderOperateTo();
            operateTo.setOrderSn(orderSn);
            operateTo.setStatus(toStatus);
            operateTo.setOperateMan("system");
            operateTo.setNote(note);
            recordOperateHistory(operateTo);
        }
        return updated;
    }

    @Transactional
    @Override
    public void createSeckillOrder(SeckillOrderTo seckillOrderTo) {
        if (seckillOrderTo == null || seckillOrderTo.getLocalMessageId() == null) {
            return;
        }
        String orderSn = "SK" + seckillOrderTo.getLocalMessageId();

        OrderEntity existing;
        try {
            existing = getOrderBySn(orderSn);
            if (existing == null) {
                buildAndLockSeckillOrder(seckillOrderTo, orderSn);
            }
        } catch (Exception e) {
            // 故意不往上抛：这里如果抛出去，@RabbitListener 默认行为是 nack+重新入队，
            // 而这个方法里没有配 DLQ/重试上限，一条持续失败的消息（比如 mall-member/
            // mall-ware 短暂不可用）会被无限重投，堵住 seckill.order.queue 后面所有
            // 排队的其他秒杀订单。这里选择记日志、放弃这一条消息——本地消息表停在
            // SENT 状态，人工介入即可看到这单没能建成，比堵住整条队列安全。
            log.error("秒杀建单流程异常 messageId={} orderSn={}: {}",
                    seckillOrderTo.getLocalMessageId(), orderSn, e.getMessage(), e);
            return;
        }

        try {
            couponFeignService.handleOrderCreated(seckillOrderTo.getLocalMessageId(), orderSn, internalToken);
        } catch (Exception e) {
            // 故意不往上抛：这个方法整体在一个事务里，抛出去会把刚提交的订单和已经
            // 锁成功的库存一起回滚，而 MQ 重投时 orderLockStock 并不是幂等的，会导致
            // 同一个 orderSn 被重复锁两次库存——两害相较，回调失败只留个日志，本地消息表
            // 停在 SENT 状态等人工核对，比库存被错误多锁一份要安全。
            log.error("秒杀建单成功但回调 mall-coupon 失败(sold_count/审计记录未更新) messageId={} orderSn={}: {}",
                    seckillOrderTo.getLocalMessageId(), orderSn, e.getMessage());
        }
    }

    /**
     * 真正建单+锁库存那部分逻辑单独拎出来，方便上面统一 try/catch 兜底。
     * 锁库存失败（秒杀没有在上架时把库存从普通渠道划出来，理论上可能被普通购物提前
     * 买光——设计里已知接受的边界情况）只记日志不抛异常，跟其他异常一视同仁地放弃。
     */
    private void buildAndLockSeckillOrder(SeckillOrderTo seckillOrderTo, String orderSn) {
        MemberAddressVo address = getAddressById(seckillOrderTo.getMemberId(), seckillOrderTo.getAddrId());

        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setOrderSn(orderSn);
        orderEntity.setCreateTime(new Date());
        orderEntity.setMemberId(seckillOrderTo.getMemberId());
        orderEntity.setMemberUsername(seckillOrderTo.getUsername());
        orderEntity.setPayType(0);
        orderEntity.setSourceType(0);
        orderEntity.setStatus(OrderStatus.NEW);
        orderEntity.setNote("秒杀订单");
        if (address != null) {
            orderEntity.setReceiverName(address.getName());
            orderEntity.setReceiverPhone(address.getPhone());
            orderEntity.setReceiverPostCode(address.getPostCode());
            orderEntity.setReceiverProvince(address.getProvince());
            orderEntity.setReceiverCity(address.getCity());
            orderEntity.setReceiverRegion(address.getRegion());
            orderEntity.setReceiverDetailAddress(address.getDetailAddress());
        }

        BigDecimal seckillPrice = seckillOrderTo.getSeckillPrice();
        orderEntity.setTotalAmount(seckillPrice);
        orderEntity.setPayAmount(seckillPrice);
        orderEntity.setFreightAmount(BigDecimal.ZERO);
        orderEntity.setPromotionAmount(BigDecimal.ZERO);
        orderEntity.setIntegrationAmount(BigDecimal.ZERO);
        orderEntity.setCouponAmount(BigDecimal.ZERO);
        orderEntity.setDiscountAmount(BigDecimal.ZERO);
        orderEntity.setIntegration(0);
        orderEntity.setGrowth(0);

        OrderItemEntity itemEntity = new OrderItemEntity();
        itemEntity.setOrderSn(orderSn);
        itemEntity.setSkuId(seckillOrderTo.getSkuId());
        itemEntity.setSkuName(seckillOrderTo.getSkuName());
        itemEntity.setSkuPic(seckillOrderTo.getSkuPic());
        itemEntity.setSkuPrice(seckillPrice);
        itemEntity.setSkuQuantity(1);
        itemEntity.setSkuAttrsVals("秒杀");
        itemEntity.setPromotionAmount(BigDecimal.ZERO);
        itemEntity.setCouponAmount(BigDecimal.ZERO);
        itemEntity.setIntegrationAmount(BigDecimal.ZERO);
        itemEntity.setRealAmount(seckillPrice);
        itemEntity.setGiftIntegration(0);
        itemEntity.setGiftGrowth(0);

        OrderCreateTo orderCreateTo = new OrderCreateTo();
        orderCreateTo.setOrder(orderEntity);
        orderCreateTo.setOrderItems(Collections.singletonList(itemEntity));
        orderCreateTo.setPayPrice(seckillPrice);

        WareSkuLockVo lockVo = buildLockVo(orderCreateTo);
        R lockResp = wareFeignService.orderLockStock(lockVo);
        if (lockResp == null || lockResp.getCode() != 0) {
            log.error("秒杀建单锁库存失败 orderSn={} skuId={}, resp={}", orderSn, seckillOrderTo.getSkuId(), lockResp);
            return;
        }

        saveOrder(orderCreateTo);
        sendOrderCreateMessage(orderSn);
    }

    private boolean verifyToken(Long memberId, String orderToken) {
        if (StringUtils.isBlank(orderToken)) {
            return false;
        }
        HttpSession session = getSession();
        if (session == null) {
            return false;
        }
        String tokenKey = OrderConstant.ORDER_TOKEN_PREFIX + memberId;
        Object tokenInSession = session.getAttribute(tokenKey);
        if (tokenInSession == null || !orderToken.equals(tokenInSession)) {
            return false;
        }
        session.removeAttribute(tokenKey);
        return true;
    }

    private OrderCreateTo createOrder(OrderSubmitVo submitVo, UserInfoTo userInfoTo) {
        OrderCreateTo orderCreateTo = new OrderCreateTo();
        String orderSn = generateOrderSn();

        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setOrderSn(orderSn);
        orderEntity.setCreateTime(new Date());
        orderEntity.setMemberId(userInfoTo.getUserId());
        orderEntity.setMemberUsername(userInfoTo.getUsername());
        orderEntity.setPayType(submitVo.getPayType());
        orderEntity.setSourceType(0);
        orderEntity.setStatus(OrderStatus.NEW);
        orderEntity.setNote(submitVo.getNote());

        MemberAddressVo address = getAddressById(userInfoTo.getUserId(), submitVo.getAddrId());
        if (address != null) {
            orderEntity.setReceiverName(address.getName());
            orderEntity.setReceiverPhone(address.getPhone());
            orderEntity.setReceiverPostCode(address.getPostCode());
            orderEntity.setReceiverProvince(address.getProvince());
            orderEntity.setReceiverCity(address.getCity());
            orderEntity.setReceiverRegion(address.getRegion());
            orderEntity.setReceiverDetailAddress(address.getDetailAddress());
        }

        List<OrderItemVo> orderItems = getOrderItems();
        List<OrderItemEntity> orderItemEntities = orderItems.stream()
                .map(item -> buildOrderItem(orderSn, item))
                .collect(Collectors.toList());

        BigDecimal totalAmount = orderItemEntities.stream()
                .map(OrderItemEntity::getRealAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        orderEntity.setTotalAmount(totalAmount);
        orderEntity.setPayAmount(totalAmount);
        orderEntity.setFreightAmount(BigDecimal.ZERO);
        orderEntity.setPromotionAmount(BigDecimal.ZERO);
        orderEntity.setIntegrationAmount(BigDecimal.ZERO);
        orderEntity.setCouponAmount(BigDecimal.ZERO);
        orderEntity.setDiscountAmount(BigDecimal.ZERO);
        orderEntity.setIntegration(0);
        orderEntity.setGrowth(0);

        orderCreateTo.setOrder(orderEntity);
        orderCreateTo.setOrderItems(orderItemEntities);
        orderCreateTo.setPayPrice(totalAmount);
        return orderCreateTo;
    }

    private OrderItemEntity buildOrderItem(String orderSn, OrderItemVo item) {
        OrderItemEntity itemEntity = new OrderItemEntity();
        itemEntity.setOrderSn(orderSn);
        itemEntity.setSkuId(item.getSkuId());
        itemEntity.setSkuName(item.getTitle());
        itemEntity.setSkuPic(item.getImage());
        itemEntity.setSkuPrice(item.getPrice());
        itemEntity.setSkuQuantity(item.getCount());
        itemEntity.setSkuAttrsVals(item.getSkuAttr() == null ? "" : String.join(";", item.getSkuAttr()));
        itemEntity.setPromotionAmount(BigDecimal.ZERO);
        itemEntity.setCouponAmount(BigDecimal.ZERO);
        itemEntity.setIntegrationAmount(BigDecimal.ZERO);
        itemEntity.setRealAmount(item.getTotalPrice());
        itemEntity.setGiftIntegration(0);
        itemEntity.setGiftGrowth(0);
        return itemEntity;
    }

    private MemberAddressVo getAddressById(Long memberId, Long addrId) {
        if (addrId == null) {
            return null;
        }
        R addressResp = memberFeignService.getAddress(memberId);
        List<MemberAddressVo> addressList = RUtils.getData(
                addressResp,
                ResponseKeys.ADDRESS,
                objectMapper,
                new TypeReference<List<MemberAddressVo>>() {}
        );
        if (addressList == null) {
            return null;
        }
        return addressList.stream().filter(addr -> addrId.equals(addr.getId())).findFirst().orElse(null);
    }

    private List<OrderItemVo> getOrderItems() {
        R cartResp = cartFeignService.getCurrentUserCartItems();
        List<OrderItemVo> items = RUtils.getData(
                cartResp,
                ResponseKeys.ITEMS,
                objectMapper,
                new TypeReference<List<OrderItemVo>>() {}
        );
        return items == null ? Collections.emptyList() : items;
    }

    private WareSkuLockVo buildLockVo(OrderCreateTo orderCreateTo) {
        WareSkuLockVo lockVo = new WareSkuLockVo();
        lockVo.setOrderSn(orderCreateTo.getOrder().getOrderSn());
        List<OrderItemLockVo> locks = orderCreateTo.getOrderItems().stream().map(item -> {
            OrderItemLockVo lockItem = new OrderItemLockVo();
            lockItem.setSkuId(item.getSkuId());
            lockItem.setCount(item.getSkuQuantity());
            lockItem.setTitle(item.getSkuName());
            return lockItem;
        }).collect(Collectors.toList());
        lockVo.setLocks(locks);
        return lockVo;
    }

    private void saveOrder(OrderCreateTo orderCreateTo) {
        this.save(orderCreateTo.getOrder());
        Long orderId = orderCreateTo.getOrder().getId();
        for (OrderItemEntity item : orderCreateTo.getOrderItems()) {
            item.setOrderId(orderId);
        }
        orderItemService.saveBatch(orderCreateTo.getOrderItems());
    }

    private void clearCartItems(OrderCreateTo orderCreateTo) {
        List<Long> skuIds = orderCreateTo.getOrderItems().stream()
                .map(OrderItemEntity::getSkuId)
                .collect(Collectors.toList());
        if (!skuIds.isEmpty()) {
            cartFeignService.deleteItems(skuIds);
        }
    }

    private void sendOrderCreateMessage(String orderSn) {
        if (StringUtils.isBlank(orderSn)) {
            return;
        }
        OrderCloseTo closeTo = new OrderCloseTo();
        closeTo.setOrderSn(orderSn);
        orderOutboxMessageService.enqueue(
                "order.close." + orderSn,
                "ORDER_CLOSE",
                orderSn,
                MqConstants.ORDER_EVENT_EXCHANGE,
                MqConstants.ORDER_CREATE_ROUTING_KEY,
                closeTo
        );
    }

    private void sendStockRelease(String orderSn) {
        List<OrderItemEntity> items = orderItemService.list(new QueryWrapper<OrderItemEntity>().eq("order_sn", orderSn));
        if (items == null || items.isEmpty()) {
            return;
        }
        StockReleaseTo releaseTo = new StockReleaseTo();
        releaseTo.setOrderSn(orderSn);
        List<StockReleaseItemTo> releaseItems = items.stream().map(item -> {
            StockReleaseItemTo to = new StockReleaseItemTo();
            to.setOrderSn(orderSn);
            to.setSkuId(item.getSkuId());
            to.setCount(item.getSkuQuantity());
            return to;
        }).collect(Collectors.toList());
        releaseTo.setItems(releaseItems);
        orderOutboxMessageService.enqueue(
                "stock.release." + orderSn,
                "STOCK_RELEASE",
                orderSn,
                MqConstants.STOCK_RELEASE_EXCHANGE,
                MqConstants.STOCK_RELEASE_ROUTING_KEY,
                releaseTo
        );
    }

    private void sendStockDeduct(String orderSn) {
        List<OrderItemEntity> items = orderItemService.list(new QueryWrapper<OrderItemEntity>().eq("order_sn", orderSn));
        if (items == null || items.isEmpty()) {
            return;
        }
        StockDeductTo deductTo = new StockDeductTo();
        deductTo.setOrderSn(orderSn);
        List<StockReleaseItemTo> deductItems = items.stream().map(item -> {
            StockReleaseItemTo to = new StockReleaseItemTo();
            to.setOrderSn(orderSn);
            to.setSkuId(item.getSkuId());
            to.setCount(item.getSkuQuantity());
            return to;
        }).collect(Collectors.toList());
        deductTo.setItems(deductItems);
        orderOutboxMessageService.enqueue(
                "stock.deduct." + orderSn,
                "STOCK_DEDUCT",
                orderSn,
                MqConstants.STOCK_RELEASE_EXCHANGE,
                MqConstants.STOCK_DEDUCT_ROUTING_KEY,
                deductTo
        );
    }

    private String generateOrderSn() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private HttpSession getSession() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        return request.getSession();
    }
}
