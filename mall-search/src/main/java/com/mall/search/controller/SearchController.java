package com.mall.search.controller;

import com.mall.common.utils.R;
import com.mall.search.service.ProductSaveService;
import com.mall.search.service.SearchService;
import com.mall.search.vo.SearchParam;
import com.mall.search.vo.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.List;

@Slf4j
@Controller
public class SearchController {

    @Autowired
    private ProductSaveService productSaveService;
    @Autowired
    private SearchService searchService;

    @GetMapping({"/list.html", "/"})
    public String listPage(SearchParam param, Model model) {
        try {
            SearchResult result = searchService.search(param);
            model.addAttribute("result", result);
            model.addAttribute("searchParam", param);
        } catch (IOException e) {
            log.error("SearchController - 搜索异常: ", e);
            model.addAttribute("result", new SearchResult());
            model.addAttribute("searchParam", param);
        }
        return "list";
    }

    /**
     * 上架商品到Elasticsearch
     */
    @PostMapping("/search/product/up")
    @ResponseBody
    public R productUp(@RequestBody List<Object> skuEsModels) {
        boolean status;
        try {
            status = productSaveService.productUp(skuEsModels);
        } catch (IOException e) {
            log.error("ElasticSaveController - 商品上架错误: ", e);
            return R.error();
        }
        if (status) {
            return R.error();
        } else {
            return R.ok();
        }
    }

    /**
     * 从 Elasticsearch 下架（删除）商品文档。
     *
     * <p>返回值判断和上架那个方法一样是反的：service 返回的是「有没有失败项」，
     * 所以 true 要转成 R.error()。名字叫 status 容易读反，别照着字面理解。
     */
    @PostMapping("/search/product/down")
    @ResponseBody
    public R productDown(@RequestBody List<Long> skuIds) {
        boolean hasFailures;
        try {
            hasFailures = productSaveService.productDown(skuIds);
        } catch (IOException e) {
            log.error("SearchController - 商品下架错误: ", e);
            return R.error();
        }
        return hasFailures ? R.error() : R.ok();
    }

}
