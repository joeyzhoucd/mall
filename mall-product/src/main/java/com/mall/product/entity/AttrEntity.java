package com.mall.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * å•†å“å±žæ€§
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@Data
@TableName("pms_attr")
public class AttrEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * å±žæ€§id
     */
    @TableId
    private Long attrId;
    /**
     * å±žæ€§å
     */
    private String attrName;
    /**
     * æ˜¯å¦éœ€è¦æ£€ç´¢[0-ä¸éœ€è¦ï¼Œ1-éœ€è¦]
     */
    private Integer searchType;
    /**
     * å±žæ€§å›¾æ ‡
     */
    private String icon;
    /**
     * å¯é€‰å€¼åˆ—è¡¨[ç”¨é€—å·åˆ†éš”]
     */
    private String valueSelect;
    /**
     * å±žæ€§ç±»åž‹[0-é”€å”®å±žæ€§ï¼Œ1-åŸºæœ¬å±žæ€§ï¼Œ2-æ—¢æ˜¯é”€å”®å±žæ€§åˆæ˜¯åŸºæœ¬å±žæ€§]
     */
    private Integer attrType;
    /**
     * å¯ç”¨çŠ¶æ€[0 - ç¦ç”¨ï¼Œ1 - å¯ç”¨]
     */
    private Long enable;
    /**
     * æ‰€å±žåˆ†ç±»
     */
    private Long categoryId;
    /**
     * å¿«é€Ÿå±•ç¤ºã€æ˜¯å¦å±•ç¤ºåœ¨ä»‹ç»ä¸Šï¼›0-å¦ 1-æ˜¯ã€‘ï¼Œåœ¨skuä¸­ä»ç„¶å¯ä»¥è°ƒæ•´
     */
    private Integer showDesc;

}
