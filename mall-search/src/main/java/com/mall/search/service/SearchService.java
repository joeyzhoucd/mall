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

    /**
     * 按 SPU 补全展示信息：每个 SPU 取一个<b>有货</b>的 SKU（热度最高的那个），
     * <b>按入参顺序</b>返回（入参已按推荐分数排好）；没货或不在索引里的 SPU 直接缺席。
     * 给「搭配购买」用 —— 推荐结果只有 SPU id，标题/价格/图片/库存在这里补。
     * 契约同 {@link #similar}：永远返回列表，永不抛异常。
     */
    List<SkuEsModel> bySpuIds(List<Long> spuIds);
}

