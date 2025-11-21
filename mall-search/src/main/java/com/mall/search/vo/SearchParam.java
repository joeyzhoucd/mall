package com.mall.search.vo;

import lombok.Data;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 前端搜索请求参数封装
 */
@Data
public class SearchParam {

    /**
     * 全文检索关键字
     */
    private String keyword;

    /**
     * 分类 id
     */
    private Long categoryId;

    /**
     * 品牌 id（可多选）
     */
    private List<Long> brandId;

    /**
     * 属性过滤（attrId_attrValue）
     */
    private List<String> attr;

    /**
     * 价格区间（0_500 / 500_ / _1000）
     */
    private String skuPrice;

    /**
     * 是否仅显示有库存（1 表示有库存）
     */
    private Integer hasStock;

    /**
     * 排序字段（hotScore_desc / skuPrice_asc ...）
     */
    private String sort;

    /**
     * 当前页码
     */
    private Integer pageNum = 1;

    /**
     * 每页数量，可选；默认由服务端决定
     */
    private Integer pageSize;

    public int resolvePageNum() {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    public int resolvePageSize(int defaultSize) {
        if (pageSize == null || pageSize <= 0) {
            return defaultSize;
        }
        return pageSize;
    }

    public boolean hasKeyword() {
        return StringUtils.hasText(keyword);
    }

    public String buildAttrLink(Long attrId, String attrValue) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(keyword)) {
            sb.append("keyword=").append(keyword).append("&");
        }
        if (categoryId != null) {
            sb.append("categoryId=").append(categoryId).append("&");
        }
        if (brandId != null && !brandId.isEmpty()) {
            for (Long bId : brandId) {
                sb.append("brandId=").append(bId).append("&");
            }
        }
        if (attr != null && !attr.isEmpty()) {
            for (String a : attr) {
                sb.append("attr=").append(a).append("&");
            }
        }
        sb.append("attr=").append(attrId).append("_").append(attrValue);
        return sb.toString();
    }
}

