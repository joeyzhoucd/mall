package com.mall.product.controller;

import com.mall.product.entity.CategoryEntity;
import com.mall.product.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class HomeController {

    @Autowired
    private CategoryService categoryService;

    @GetMapping({"/", "/index.html"})
    public String index(Model model) {
        List<CategoryEntity> categories = categoryService.listAsTree();
        model.addAttribute("categories", categories);
        return "index";
    }
}


