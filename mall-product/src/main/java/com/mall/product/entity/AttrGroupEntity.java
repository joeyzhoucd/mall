package com.mall.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * å±žæ€§åˆ†ç»„
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@Data
@TableName("pms_attr_group")
public class AttrGroupEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * åˆ†ç»„id
     */
    @TableId
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

}
