package com.mall.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * å•†å“è¯„ä»·
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@Data
@TableName("pms_spu_comment")
public class SpuCommentEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId
    private Long id;
    /**
     * sku_id
     */
    private Long skuId;
    /**
     * spu_id
     */
    private Long spuId;
    /**
     * å•†å“åå­—
     */
    private String spuName;
    /**
     * ä¼šå‘˜æ˜µç§°
     */
    private String memberNickName;
    /**
     * æ˜Ÿçº§
     */
    private Integer star;
    /**
     * ä¼šå‘˜ip
     */
    private String memberIp;
    /**
     * åˆ›å»ºæ—¶é—´
     */
    private Date createTime;
    /**
     * æ˜¾ç¤ºçŠ¶æ€[0-ä¸æ˜¾ç¤ºï¼Œ1-æ˜¾ç¤º]
     */
    private Integer showStatus;
    /**
     * è´­ä¹°æ—¶å±žæ€§ç»„åˆ
     */
    private String spuAttributes;
    /**
     * ç‚¹èµžæ•°
     */
    private Integer likesCount;
    /**
     * å›žå¤æ•°
     */
    private Integer replyCount;
    /**
     * è¯„è®ºå›¾ç‰‡/è§†é¢‘[jsonæ•°æ®ï¼›[{type:æ–‡ä»¶ç±»åž‹,url:èµ„æºè·¯å¾„}]]
     */
    private String resources;
    /**
     * å†…å®¹
     */
    private String content;
    /**
     * ç”¨æˆ·å¤´åƒ
     */
    private String memberIcon;
    /**
     * è¯„è®ºç±»åž‹[0 - å¯¹å•†å“çš„ç›´æŽ¥è¯„è®ºï¼Œ1 - å¯¹è¯„è®ºçš„å›žå¤]
     */
    private Integer commentType;

}
