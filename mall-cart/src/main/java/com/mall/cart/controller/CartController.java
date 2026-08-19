package com.mall.cart.controller;

import com.mall.cart.service.CartService;
import com.mall.cart.vo.CartItemVo;
import com.mall.cart.vo.CartVo;
import com.mall.common.utils.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Slf4j
@Controller
public class CartController {

    @Autowired
    private CartService cartService;

    @GetMapping("/addCartItem")
    public String addCartItem(@RequestParam("skuId") Long skuId,
                              @RequestParam(value = "num", required = false, defaultValue = "1") Integer num) throws ExecutionException, InterruptedException {
        cartService.addToCart(skuId, num);
        return "redirect:http://cart.mall.com/addToCartSuccess.html?skuId=" + skuId;
    }

    @GetMapping("/addToCartSuccess.html")
    public String addToCartSuccessPage(@RequestParam("skuId") Long skuId, Model model) {
        CartItemVo item = cartService.getCartItem(skuId);
        model.addAttribute("item", item);
        return "success";
    }

    @GetMapping("/cart.html")
    public String cartListPage(Model model) throws ExecutionException, InterruptedException {
        CartVo cart = cartService.getCart();
        model.addAttribute("cart", cart);
        return "cart";
    }

    @GetMapping("/checkItem")
    public String checkItem(@RequestParam("skuId") Long skuId, @RequestParam("check") Boolean check) {
        cartService.checkItem(skuId, check);
        return "redirect:http://cart.mall.com/cart.html";
    }

    @GetMapping("/changeItemCount")
    public String changeItemCount(@RequestParam("skuId") Long skuId, @RequestParam("num") Integer num) {
        cartService.changeItemCount(skuId, num);
        return "redirect:http://cart.mall.com/cart.html";
    }

    @GetMapping("/deleteItem")
    public String deleteItem(@RequestParam("skuId") Long skuId) {
        cartService.deleteItem(skuId);
        return "redirect:http://cart.mall.com/cart.html";
    }

    @ResponseBody
    @GetMapping("/currentUserCartItems")
    public R getCurrentUserCartItems() {
        List<CartItemVo> items = cartService.getUserCartItems();
        return R.ok().put("items", items);
    }

    @ResponseBody
    @PostMapping("/deleteItems")
    public R deleteItems(@RequestBody List<Long> skuIds) {
        cartService.deleteItems(skuIds);
        return R.ok();
    }
}

