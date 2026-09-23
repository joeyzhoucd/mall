package com.mall.cart.service.impl;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.mall.cart.constant.CartConstant;
import com.mall.cart.feign.ProductFeignService;
import com.mall.cart.interceptor.CartInterceptor;
import com.mall.cart.service.CartService;
import com.mall.cart.to.UserInfoTo;
import com.mall.cart.vo.CartItemVo;
import com.mall.cart.vo.CartVo;
import com.mall.cart.vo.SkuInfoVo;
import com.mall.common.utils.R;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import com.mall.cart.service.CartEventLogger;
import com.mall.cart.to.CartLogTo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CartServiceImpl implements CartService {

    @Autowired
    private ProductFeignService productFeignService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CartEventLogger eventLogger;

    @Autowired
    private ObjectMapper objectMapper;

    private String getCartKey(UserInfoTo userInfoTo) {
        if (userInfoTo.getUserId() != null) {
            return CartConstant.CART_REDIS_KEY_PREFIX + userInfoTo.getUserId();
        }
        return CartConstant.CART_REDIS_KEY_PREFIX + userInfoTo.getUserKey();
    }

    private BoundHashOperations<String, Object, Object> getCartOps() {
        UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();
        String cartKey = getCartKey(userInfoTo);
        return redisTemplate.boundHashOps(cartKey);
    }

    @Override
    public CartItemVo addToCart(Long skuId, Integer num) throws ExecutionException, InterruptedException {
        if (num == null || num <= 0) {
            num = 1;
        }
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        String cacheValue = (String) cartOps.get(String.valueOf(skuId));
        if (StringUtils.isEmpty(cacheValue)) {
            CartItemVo cartItem = new CartItemVo();
            cartItem.setCount(num);
            cartItem.setSkuId(skuId);
            fillSkuInfo(cartItem, skuId);
            saveCartItem(cartOps, cartItem);
            eventLogger.record(currentMemberId(), skuId, cartItem.getSpuId(),
                    CartLogTo.ACTION_ADD, cartItem.getCount());
            return cartItem;
        } else {
            CartItemVo cartItem = readCartItem(cacheValue);
            cartItem.setCount(cartItem.getCount() + num);
            saveCartItem(cartOps, cartItem);
            eventLogger.record(currentMemberId(), skuId, cartItem.getSpuId(),
                    CartLogTo.ACTION_ADD, cartItem.getCount());
            return cartItem;
        }
    }

    private void fillSkuInfo(CartItemVo cartItem, Long skuId) throws ExecutionException, InterruptedException {
        R r = productFeignService.getSkuInfo(skuId);
        if (r == null || r.getCode() != 0) {
            throw new RuntimeException("获取商品信息失败");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> skuInfoMap = (Map<String, Object>) r.get("skuInfo");
        SkuInfoVo skuInfoVo = objectMapper.convertValue(skuInfoMap, SkuInfoVo.class);
        // spuId 必须在这里落进缓存：后续的改数量/删除只读缓存，不会再来一次商品服务，
        // 而行为埋点的共现统计是 SPU 粒度的。
        cartItem.setSpuId(skuInfoVo.getSpuId());
        cartItem.setTitle(skuInfoVo.getSkuTitle());
        cartItem.setImage(skuInfoVo.getSkuDefaultImg());
        cartItem.setPrice(skuInfoVo.getPrice() == null ? BigDecimal.ZERO : skuInfoVo.getPrice());

        try {
            List<String> attrValues = productFeignService.getSkuSaleAttrValues(skuId);
            cartItem.setSkuAttr(attrValues);
        } catch (Exception e) {
            log.warn("获取销售属性失败 skuId={}", skuId, e);
            cartItem.setSkuAttr(Collections.emptyList());
        }
    }

    private void saveCartItem(BoundHashOperations<String, Object, Object> cartOps, CartItemVo cartItem) {
        try {
            cartOps.put(String.valueOf(cartItem.getSkuId()), objectMapper.writeValueAsString(cartItem));
        } catch (JacksonException e) {
            throw new RuntimeException("保存购物车数据失败", e);
        }
    }

    private CartItemVo readCartItem(String cacheValue) {
        try {
            return objectMapper.readValue(cacheValue, CartItemVo.class);
        } catch (JacksonException e) {
            throw new RuntimeException("解析购物车数据失败", e);
        }
    }

    @Override
    public CartItemVo getCartItem(Long skuId) {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        String cacheValue = (String) cartOps.get(String.valueOf(skuId));
        if (StringUtils.isEmpty(cacheValue)) {
            return null;
        }
        return readCartItem(cacheValue);
    }

    @Override
    public CartVo getCart() throws ExecutionException, InterruptedException {
        UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();
        CartVo cartVo = new CartVo();

        // 登录后合并临时购物车
        if (userInfoTo.getUserId() != null) {
            String tempCartKey = CartConstant.CART_REDIS_KEY_PREFIX + userInfoTo.getUserKey();
            List<CartItemVo> tempItems = getCartItems(tempCartKey);
            if (tempItems != null) {
                for (CartItemVo item : tempItems) {
                    addToCart(item.getSkuId(), item.getCount());
                }
                redisTemplate.delete(tempCartKey);
            }
        }

        List<CartItemVo> cartItems = getCartItems(getCartKey(userInfoTo));
        cartVo.setItems(cartItems);
        return cartVo;
    }

    private List<CartItemVo> getCartItems(String cartKey) {
        BoundHashOperations<String, Object, Object> cartOps = redisTemplate.boundHashOps(cartKey);
        List<Object> values = cartOps.values();
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream().map(obj -> readCartItem(String.valueOf(obj))).collect(Collectors.toList());
    }

    /** 当前会员 id；未登录的临时购物车返回 null，埋点里会落成 0。 */
    private Long currentMemberId() {
        UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();
        return userInfoTo == null ? null : userInfoTo.getUserId();
    }

    @Override
    public void checkItem(Long skuId, Boolean check) {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        String cacheValue = (String) cartOps.get(String.valueOf(skuId));
        if (StringUtils.isNotEmpty(cacheValue)) {
            CartItemVo itemVo = readCartItem(cacheValue);
            itemVo.setCheck(check);
            saveCartItem(cartOps, itemVo);
            eventLogger.record(currentMemberId(), skuId, itemVo.getSpuId(),
                    CartLogTo.ACTION_CHECK, itemVo.getCount());
        }
    }

    @Override
    public void changeItemCount(Long skuId, Integer num) {
        if (num == null || num <= 0) {
            num = 1;
        }
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        String cacheValue = (String) cartOps.get(String.valueOf(skuId));
        if (StringUtils.isNotEmpty(cacheValue)) {
            CartItemVo itemVo = readCartItem(cacheValue);
            itemVo.setCount(num);
            saveCartItem(cartOps, itemVo);
            eventLogger.record(currentMemberId(), skuId, itemVo.getSpuId(),
                    CartLogTo.ACTION_CHANGE_COUNT, num);
        }
    }

    @Override
    public void deleteItem(Long skuId) {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        // 【先读再删】删除本身不需要读，这一次 Redis GET 纯粹是为了埋点拿到 spuId
        // ——共现统计是 SPU 粒度的，只记 skuId 的删除事件后面用不了。
        // 一次 hash GET 相对于这条路径上已有的开销可以忽略。
        String cacheValue = (String) cartOps.get(String.valueOf(skuId));
        cartOps.delete(String.valueOf(skuId));
        if (StringUtils.isNotEmpty(cacheValue)) {
            CartItemVo itemVo = readCartItem(cacheValue);
            eventLogger.record(currentMemberId(), skuId, itemVo.getSpuId(),
                    CartLogTo.ACTION_DELETE, itemVo.getCount());
        }
    }

    @Override
    public List<CartItemVo> getUserCartItems() {
        UserInfoTo userInfoTo = CartInterceptor.threadLocal.get();
        if (userInfoTo.getUserId() == null) {
            return Collections.emptyList();
        }
        String cartKey = CartConstant.CART_REDIS_KEY_PREFIX + userInfoTo.getUserId();
        return getCartItems(cartKey).stream()
                .filter(item -> Boolean.TRUE.equals(item.getCheck()))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteItems(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return;
        }
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        for (Long skuId : skuIds) {
            if (skuId != null) {
                cartOps.delete(String.valueOf(skuId));
            }
        }
    }
}

