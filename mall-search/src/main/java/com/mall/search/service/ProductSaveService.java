package com.mall.search.service;

import java.io.IOException;
import java.util.List;

public interface ProductSaveService {
    boolean productUp(List<Object> skuEsModels) throws IOException;

    /**
     * 从索引里删除这些 sku 文档（下架/删除商品用）。
     * 返回值语义和 {@link #productUp} 保持一致：true 表示<b>有失败项</b>。
     */
    boolean productDown(List<Long> skuIds) throws IOException;

}

