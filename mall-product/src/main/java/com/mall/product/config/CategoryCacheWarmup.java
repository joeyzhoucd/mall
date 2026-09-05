package com.mall.product.config;

import com.mall.product.service.CategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "mall.product.cache.category.warmup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CategoryCacheWarmup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CategoryCacheWarmup.class);

    private final CategoryService categoryService;

    public CategoryCacheWarmup(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int rootCount = categoryService.listAsTree().size();
            log.info("Category tree cache warmup completed, rootCount={}", rootCount);
        } catch (Exception e) {
            log.warn("Category tree cache warmup failed; first request will rebuild it lazily: {}", e.getMessage());
        }
    }
}
