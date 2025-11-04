package com.mall.search.controller;

import com.mall.common.utils.R;
import com.mall.search.service.ProductSaveService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/search/product")
public class SearchController {

    @Autowired
    private ProductSaveService productSaveService;

    /**
     * 上架商品到Elasticsearch
     */
    @PostMapping("/up")
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
}

