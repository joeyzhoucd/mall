package com.mall.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mall.common.validator.group.AddGroup;
import com.mall.common.validator.group.UpdateGroup;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Null;
import java.io.Serializable;

/**
 * å“ç‰Œ
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@Data
@TableName("pms_brand")
public class BrandEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * å“ç‰Œid
     */
    @TableId
    @NotBlank(message = "å“ç‰ŒIdä¸èƒ½ä¸ºç©º", groups = UpdateGroup.class)
    @Null(message = "å“ç‰ŒIdå¿…é¡»ä¸ºç©º", groups = AddGroup.class)
    private Long brandId;
    /**
     * å“ç‰Œå
     */
    @NotBlank(message = "å“ç‰Œåä¸èƒ½ä¸ºç©º")
    private String name;
    /**
     * å“ç‰Œlogoåœ°å€
     */
    private String logo;
    /**
     * ä»‹ç»
     */
    private String descript;
    /**
     * æ˜¾ç¤ºçŠ¶æ€[0-ä¸æ˜¾ç¤ºï¼›1-æ˜¾ç¤º]
     */
    private Integer showStatus;
    /**
     * æ£€ç´¢é¦–å­—æ¯
     */
    private String firstLetter;
    /**
     * æŽ’åº
     */
    private Integer sort;

}
