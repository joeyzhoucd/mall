package com.mall.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 「搭配购买」离线批次的一行：某个 SPU 的第 rankNo 个搭配。
 * 只由批任务（com.mall.order.reco.batch，裸 JDBC）写入，在线服务只读。
 * 表结构见 mall-deploy/data-seed/migration-2026-09-28-reco-complement.sql。
 */
@Data
@TableName("oms_spu_complement")
public class SpuComplementEntity {

    private Long batchId;

    private Long spuId;

    private Integer rankNo;

    private Long complementSpuId;

    private Double score;

    private Integer cooccur;
}
