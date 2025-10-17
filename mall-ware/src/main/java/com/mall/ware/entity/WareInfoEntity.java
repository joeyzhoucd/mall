package com.mall.ware.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * ä»“åº“ä¿¡æ¯
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-28 02:39:50
 */
@Data
@TableName("wms_ware_info")
public class WareInfoEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @TableId
    private Long id;
    /**
     * ä»“åº“å
     */
    private String name;
    /**
     * ä»“åº“åœ°å€
     */
    private String address;
    /**
     * åŒºåŸŸç¼–ç 
     */
    private String areacode;
}
