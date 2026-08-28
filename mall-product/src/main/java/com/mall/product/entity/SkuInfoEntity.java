package com.mall.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;


@Data
@TableName("pms_sku_info")
public class SkuInfoEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    
    @TableId
    private Long skuId;
    
    private Long spuId;
    
    private String skuName;
    
    private String skuDesc;
    
    private Long categoryId;
    
    private Long brandId;
    
    private String skuDefaultImg;
    
    private String skuTitle;
    
    private String skuSubtitle;
    
    private BigDecimal price;
    
    private Long saleCount;

    /**
     * 上下架状态：1 上架、0 下架。
     *
     * <p>这一列是后加的。原来的 pms_sku_info <b>没有</b>上下架状态，只有 SPU 有 ——
     * 于是后台「批量上架/下架 SKU」那个按钮在数据模型里无处落脚，接口没法实现。
     *
     * <p>补在 SKU 而不是复用 SPU 的状态，因为这是两个不同粒度的业务动作：
     * 某个规格断货或停产要单独下架，同 SPU 的其他规格照常卖。
     * 如果把 SKU 的下架映射到它的 SPU 上，点一个规格会连带下架所有兄弟规格 ——
     * 界面上完全看不出来，等发现时已经误下架了一批在售商品。
     *
     * <p>默认 1（在售）。迁移脚本给存量数据填 1，因为它们当前就是可搜可买的状态；
     * 填 0 会让整个商品库在一次 schema 变更之后从搜索结果里消失。
     */
    private Integer publishStatus;

}
