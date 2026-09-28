package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/** 每种推荐当前生效的批次。在线服务只看这里，批任务切换它（一个事务里） */
@Data
@TableName("oms_reco_active")
public class RecoActiveEntity {

    @TableId(type = IdType.INPUT)
    private String kind;

    private Long batchId;

    private Date switchedAt;
}
