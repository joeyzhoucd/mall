package com.mall.product.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * spuå›¾ç‰‡
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@Data
@TableName("pms_spu_images")
public class SpuImagesEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId
    private Long id;
    /**
     * spu_id
     */
    private Long spuId;
    /**
     * å›¾ç‰‡å
     */
    private String imgName;
    /**
     * å›¾ç‰‡åœ°å€
     */
    private String imgUrl;
    /**
     * é¡ºåº
     */
    private Integer imgSort;
    /**
     * æ˜¯å¦é»˜è®¤å›¾
     */
    private Integer defaultImg;

}
