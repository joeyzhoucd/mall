package com.mall.product.controller;

import com.mall.product.entity.CategoryEntity;
import com.mall.product.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@Slf4j
public class HomeController {

    @Autowired
    private CategoryService categoryService;

    @GetMapping({"/", "/index.html"})
    public String index(Model model) {
        List<CategoryEntity> categories = categoryService.listAsTree();
        int size = categories == null ? 0 : categories.size();
        log.info("[Home] 分类数据装载完成，数量={}（{}）", size, size > 0 ? "正常" : "为空");
        model.addAttribute("categories", categories);
        return "index";
    }
}


