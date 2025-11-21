package com.mall.search.vo;

import com.mall.search.vo.SkuEsModel.Attrs;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索结果封装
 */
@Data
public class SearchResult {

    /**
     * 商品列表
     */
    private List<com.mall.search.vo.SkuEsModel> products = new ArrayList<>();

    private Integer pageNum;
    private Long total;
    private Integer totalPages;
    private List<Integer> pageNavs = new ArrayList<>();

    /**
     * 所有可选品牌
     */
    private List<BrandVo> brands = new ArrayList<>();

    /**
     * 所有可选分类
     */
    private List<CatalogVo> categories = new ArrayList<>();

    /**
     * 所有可选属性
     */
    private List<AttrVo> attrs = new ArrayList<>();

    /**
     * 面包屑导航
     */
    private List<NavVo> navs = new ArrayList<>();

    @Data
    public static class BrandVo {
        private Long brandId;
        private String brandName;
        private String brandImg;
    }

    @Data
    public static class CatalogVo {
        private Long categoryId;
        private String categoryName;
    }

    @Data
    public static class AttrVo {
        private Long attrId;
        private String attrName;
        private List<AttrValueVo> attrValue;
    }

    @Data
    public static class AttrValueVo {
        private String val;
        private String link;
    }

    @Data
    public static class NavVo {
        private String name;
        private String value;
        private String link;
    }
}

