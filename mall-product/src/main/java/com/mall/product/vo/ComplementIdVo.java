package com.mall.product.vo;

import lombok.Data;

/** mall-order「搭配购买」接口返回的一项：只有 SPU 和 LLR 分数，展示信息靠 mall-search 补 */
@Data
public class ComplementIdVo {

    private Long spuId;

    private Double score;
}
