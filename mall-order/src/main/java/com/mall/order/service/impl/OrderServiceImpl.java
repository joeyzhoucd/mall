package com.mall.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.common.constant.MqConstants;
import com.mall.common.constant.OrderStatus;
import com.mall.common.constant.ResponseKeys;
import com.mall.common.to.OrderCloseTo;
import com.mall.common.to.StockDeductTo;
import com.mall.common.to.StockReleaseItemTo;
import com.mall.common.to.StockReleaseTo;
import com.mall.common.to.OrderOperateTo;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.common.utils.R;
import com.mall.common.utils.RUtils;
import com.mall.order.constant.OrderConstant;
import com.mall.order.dao.OrderDao;
import com.mall.order.entity.OrderEntity;
import com.mall.order.entity.OrderItemEntity;
import com.mall.order.feign.CartFeignService;
import com.mall.order.feign.MemberFeignService;
import com.mall.order.feign.WareFeignService;
import com.mall.order.interceptor.OrderInterceptor;
import com.mall.order.service.OrderItemService;
import com.mall.order.service.OrderOperateHistoryService;
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
import org.apache.commons.lang.StringUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {

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
    private RabbitTemplate rabbitTemplate;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                new QueryWrapper<OrderEntity>()
        );

        return new PageUtils(page);
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
            responseVo.setCode(1);
            return responseVo;
        }

        if (!verifyToken(userInfoTo.getUserId(), submitVo.getOrderToken())) {
            responseVo.setCode(1);
            return responseVo;
        }

        if (submitVo.getAddrId() == null || getAddressById(userInfoTo.getUserId(), submitVo.getAddrId()) == null) {
            responseVo.setCode(4);
            return responseVo;
        }

        OrderCreateTo orderCreateTo = createOrder(submitVo, userInfoTo);
        if (submitVo.getPayPrice() != null) {
            BigDecimal delta = orderCreateTo.getPayPrice().subtract(submitVo.getPayPrice()).abs();
            if (delta.compareTo(new BigDecimal("0.01")) > 0) {
                responseVo.setCode(2);
                return responseVo;
            }
        }

        WareSkuLockVo lockVo = buildLockVo(orderCreateTo);
        R lockResp = wareFeignService.orderLockStock(lockVo);
        if (lockResp == null || lockResp.getCode() != 0) {
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
            responseVo.setCode(1);
            return responseVo;
        }

        responseVo.setCode(0);
        responseVo.setOrder(orderCreateTo.getOrder());
        return responseVo;
    }

    @Override
    public void closeOrder(String orderSn) {
        if (StringUtils.isBlank(orderSn)) {
            return;
        }
        OrderEntity orderEntity = this.getOne(new QueryWrapper<OrderEntity>().eq("order_sn", orderSn));
        if (orderEntity == null || orderEntity.getStatus() == null) {
            return;
        }
        if (orderEntity.getStatus() == OrderStatus.NEW) {
            OrderEntity update = new OrderEntity();
            update.setId(orderEntity.getId());
            update.setStatus(OrderStatus.CLOSED);
            update.setModifyTime(new Date());
            this.updateById(update);
            sendStockRelease(orderSn);
        }
    }

    @Override
    public void payOrderSuccess(String orderSn) {
        if (StringUtils.isBlank(orderSn)) {
            return;
        }
        OrderEntity orderEntity = this.getOne(new QueryWrapper<OrderEntity>().eq("order_sn", orderSn));
        if (orderEntity == null || orderEntity.getStatus() == null) {
            return;
        }
        if (orderEntity.getStatus() == OrderStatus.NEW) {
            OrderEntity update = new OrderEntity();
            update.setId(orderEntity.getId());
            update.setStatus(OrderStatus.PAYED);
            update.setPaymentTime(new Date());
            update.setModifyTime(new Date());
            this.updateById(update);
            sendStockDeduct(orderSn);
        }
    }

    @Override
    public OrderEntity getOrderBySn(String orderSn) {
        if (StringUtils.isBlank(orderSn)) {
            return null;
        }
        return this.getOne(new QueryWrapper<OrderEntity>().eq("order_sn", orderSn));
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
        rabbitTemplate.convertAndSend(
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
        rabbitTemplate.convertAndSend(
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
        rabbitTemplate.convertAndSend(
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
