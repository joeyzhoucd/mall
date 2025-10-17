package com.mall.product.vo;

import com.mall.product.entity.AttrEntity;
import lombok.Data;

import java.util.List;

@Data
public class AttrGroupWithAttrVO {

    private Long attrGroupId;
    /**
     * ç»„å
     */
    private String attrGroupName;
    /**
     * æŽ’åº
     */
    private Integer sort;
    /**
     * æè¿°
     */
    private String descript;
    /**
     * ç»„å›¾æ ‡
     */
    private String icon;
    /**
     * æ‰€å±žåˆ†ç±»id
     */
    private Long categoryId;

    private List<AttrEntity> attrs;
}
