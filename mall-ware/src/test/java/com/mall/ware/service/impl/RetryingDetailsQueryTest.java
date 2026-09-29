package com.mall.ware.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mall.common.constant.StockLockStatus;
import com.mall.ware.entity.WareOrderTaskDetailEntity;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/** 宽限期条件：按任务的 create_time 过滤，截止时间作为绑定参数（不是 SQL 里的 NOW()，那会差 8 小时） */
class RetryingDetailsQueryTest {

    @Test
    void filtersByTaskCreateTimeWithABoundParameter() {
        Date cutoff = new Date(1_790_000_000_000L);

        QueryWrapper<WareOrderTaskDetailEntity> q = WareOrderTaskDetailServiceImpl.retryingQuery(StockLockStatus.LOCKED, 3, cutoff);
        String sql = q.getSqlSegment();

        assertThat(sql).contains("lock_status =").contains("retry_count")
                .contains("task_id IN (SELECT id FROM wms_ware_order_task WHERE create_time < #{")
                .doesNotContainIgnoringCase("now()");
        assertThat(q.getParamNameValuePairs()).containsValue(cutoff);
    }
}
