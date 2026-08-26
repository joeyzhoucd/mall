package com.mall.admin.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * sys_config
 */
@Data
@TableName("sys_config")
public class SysConfigEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    private String paramKey;

    private String paramValue;

    private Integer status;

    private String remark;

}
