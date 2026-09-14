package com.mall.product.controller;

import com.mall.product.service.SkuInfoService;
import com.mall.product.vo.SkuItemVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

/**
 * 商品详情页（PDP）。
 *
 * <h3>这里的异常处理为什么要在进模板之前做完</h3>
 * 这个方法返回的是<b>视图名</b>，渲染发生在它 return 之后。
 * 也就是说模板里抛的异常是在 {@code DispatcherServlet} 把响应交给
 * {@code ThymeleafView.render()} 之后才出现的，
 * 那时候 {@code @ExceptionHandler} / {@code @ControllerAdvice} 的作用域
 * <b>已经结束了</b> —— 全局兜底捕获不到，日志里只看得见 /error 上的二次异常。
 * <p>
 * 2026-09-14 的详情页 500 就是这么来的（模板里迭代到一个 null 元素），
 * 排查时看日志完全看不出真实原因。
 * <p>
 * 结论：凡是模板会直接解引用的东西，都必须在<b>这里</b>就保证成立，
 * 不能指望后面有人接住。
 */
@Controller
public class ItemController {

    private static final Logger log = LoggerFactory.getLogger(ItemController.class);

    @Autowired
    private SkuInfoService skuInfoService;

    @GetMapping("/{skuId}.html")
    public String skuItem(@PathVariable("skuId") Long skuId, Model model) {
        SkuItemVo vo = skuInfoService.item(skuId);

        // 商品不存在时 item() 返回的是一个 info 为 null 的 VO（不是 null 本身）。
        // 不拦的话模板里 ${item.info.price} 会在渲染阶段抛 NPE，
        // 表现成 500 —— 而"这个商品没有"本来就该是 404。
        // 实测：/10.html（库里没有的 skuId）改之前就是 500。
        if (vo == null || vo.getInfo() == null) {
            log.debug("商品不存在，skuId={}", skuId);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "商品不存在");
        }

        model.addAttribute("item", vo);
        return "item";
    }
}
