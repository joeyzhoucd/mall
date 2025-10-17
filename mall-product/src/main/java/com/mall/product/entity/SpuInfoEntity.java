package com.mall.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * spuä¿¡æ¯
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@Data
@TableName("pms_spu_info")
public class SpuInfoEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * å•†å“id
     */
    @TableId
    private Long id;
    /**
     * å•†å“åç§°
     */
    private String spuName;
    /**
     * å•†å“æè¿°
     */
    private String spuDescription;
    /**
     * æ‰€å±žåˆ†ç±»id
     */
    private Long categoryId;
    /**
     * å“ç‰Œid
     */
    private Long brandId;
    /**
     *
     */
    private BigDecimal weight;
    /**
     * ä¸Šæž¶çŠ¶æ€[0 - ä¸‹æž¶ï¼Œ1 - ä¸Šæž¶]
     */
    private Integer publishStatus;
    /**
     *
     */
    private Date createTime;
    /**
     *
     */
    private Date updateTime;

}
