package com.mall.ware.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;


@Data
@TableName("wms_ware_order_task_detail")
public class WareOrderTaskDetailEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	
	@TableId
	private Long id;
	
	private Long skuId;
	
	private String skuName;
	
	private Integer skuNum;
	
	private Long taskId;

	private Integer lockStatus;

	private Integer retryCount;

	/**
	 * 这条明细当初锁的是哪个仓库。
	 * <p>
	 * 没有它的时候，释放和扣减只能按 sku_id 查、用 ORDER BY id LIMIT 1 任选一行
	 * （见 WareSkuDao.xml 里 releaseLockedBySku / deductStockBySku）。单仓库时结果
	 * 恰好精确，多仓库时就会出现：A 仓锁的库存被从 B 仓释放 —— <b>sku 的总量始终对，
	 * 但每个仓的 stock_locked 会持续漂移</b>，而且没有任何报错。
	 * 这种「总数对、分布错」的问题在单仓环境里永远测不出来。
	 * <p>
	 * 允许为 null：迁移之前创建的明细行没有这个值，那些行仍走旧的按 sku 释放路径
	 * （见 StockAtomicOps）。
	 */
	private Long wareId;

}
