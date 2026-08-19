package com.mall.cart.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
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
            return cartItem;
        } else {
            CartItemVo cartItem = readCartItem(cacheValue);
            cartItem.setCount(cartItem.getCount() + num);
            saveCartItem(cartOps, cartItem);
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
        } catch (JsonProcessingException e) {
            throw new RuntimeException("保存购物车数据失败", e);
        }
    }

    private CartItemVo readCartItem(String cacheValue) {
        try {
            return objectMapper.readValue(cacheValue, CartItemVo.class);
        } catch (JsonProcessingException e) {
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

    @Override
    public void checkItem(Long skuId, Boolean check) {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        String cacheValue = (String) cartOps.get(String.valueOf(skuId));
        if (StringUtils.isNotEmpty(cacheValue)) {
            CartItemVo itemVo = readCartItem(cacheValue);
            itemVo.setCheck(check);
            saveCartItem(cartOps, itemVo);
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
        }
    }

    @Override
    public void deleteItem(Long skuId) {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        cartOps.delete(String.valueOf(skuId));
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

