package com.mall.product.vo;

import com.mall.product.entity.AttrEntity;
import lombok.Data;

@Data
public class AttrAttrgroupRelationVO {
    private Long id;
    /**
     * å±žæ€§id
     */
    private Long attrId;
    /**
     * å±žæ€§åç§°ï¼ˆç”¨äºŽå‰ç«¯ç›´æŽ¥å±•ç¤ºï¼‰
     */
    private String attrName;
    /**
     * å±žæ€§åˆ†ç»„id
     */
    private Long attrGroupId;
    /**
     * å±žæ€§ç»„å†…æŽ’åº
     */
    private Integer attrSort;

    private AttrEntity attr;
}
