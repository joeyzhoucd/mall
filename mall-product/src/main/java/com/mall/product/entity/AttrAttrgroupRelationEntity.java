package com.mall.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * å±žæ€§&å±žæ€§åˆ†ç»„å…³è”
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@Data
@TableName("pms_attr_attrgroup_relation")
public class AttrAttrgroupRelationEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId
    private Long id;
    /**
     * å±žæ€§id
     */
    private Long attrId;
    /**
     * å±žæ€§åˆ†ç»„id
     */
    private Long attrGroupId;
    /**
     * å±žæ€§ç»„å†…æŽ’åº
     */
    private Integer attrSort;

}
