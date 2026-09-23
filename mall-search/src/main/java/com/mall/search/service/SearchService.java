package com.mall.search.service;

import com.mall.search.vo.SearchParam;
import com.mall.search.vo.SearchResult;
import com.mall.search.vo.SkuEsModel;

import java.io.IOException;
import java.util.List;

public interface SearchService {

    SearchResult search(SearchParam param) throws IOException;

    /**
     * 相似商品推荐：拿这个 sku 自己的标题向量去找最近邻。
     *
     * <p><b>刻意不声明 throws</b>。调用方是商品详情页，而详情页的模板会直接
     * 解引用这个列表；推荐算不出来是完全可以接受的，把详情页带崩不是。
     * 所以这里的契约是<b>永远返回一个列表（可能为空），永不抛异常、永不返回 null</b>，
     * 降级全部在实现里做完。
     *
     * @param skuId 当前商品
     * @param size  想要几条（实现会做上限保护）
     */
    List<SkuEsModel> similar(Long skuId, int size);
}

