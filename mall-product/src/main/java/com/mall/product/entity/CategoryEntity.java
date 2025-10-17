package com.mall.product.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * å•†å“ä¸‰çº§åˆ†ç±»
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@Data
@TableName("pms_category")
public class CategoryEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * åˆ†ç±»id
     */
    @TableId
    private Long catId;
    /**
     * åˆ†ç±»åç§°
     */
    private String name;
    /**
     * çˆ¶åˆ†ç±»id
     */
    private Long parentCid;
    /**
     * å±‚çº§
     */
    private Integer catLevel;
    /**
     * æ˜¯å¦æ˜¾ç¤º[0-ä¸æ˜¾ç¤ºï¼Œ1æ˜¾ç¤º]
     */
    private Integer showStatus;
    /**
     * æŽ’åº
     */
    private Integer sort = 0;
    /**
     * å›¾æ ‡åœ°å€
     */
    private String icon;
    /**
     * è®¡é‡å•ä½
     */
    private String productUnit;
    /**
     * å•†å“æ•°é‡
     */
    private Integer productCount;

    @TableField(exist = false)
    private List<CategoryEntity> children;

    @TableLogic   // æ ‡è®°é€»è¾‘åˆ é™¤å­—æ®µ
    private Integer deleted;


}
