package com.mall.admin;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.mall.admin.dao.ScheduleJobLogDao;
import com.mall.admin.entity.ScheduleJobLogEntity;
import com.mall.admin.task.ScheduleJobLogCleanupTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 守住日志清理的<b>循环</b>逻辑。
 *
 * <h3>为什么测的是循环，不是"删对了没有"</h3>
 * 「删的是哪些行」由 SQL 决定，用 mock 验不出来（也不该在单测里验 SQL）。
 * 真正容易出错、而且出错代价大的是循环本身：
 * <ul>
 *   <li>边界查不到时<b>不该发任何删除</b> —— 少了这个判断会退化成
 *       「没有 where 的批量删」，把整张表删空；</li>
 *   <li>删够一批但不满一批时<b>必须停</b> —— 少了这个判断就是死循环，
 *       每次都发一条删 0 行的 SQL，任务永远不结束；</li>
 *   <li>撞上批次上限时<b>必须停</b> —— 少了这个上限，表异常大时这个任务
 *       会一跑几个小时把数据库压住。</li>
 * </ul>
 * 这三条都是「跑起来看不出问题、量大了才出事」的类型。
 */
class ScheduleJobLogCleanupTaskTest {

    /** 和实现里的 BATCH_SIZE 一致。写死是有意的：改了实现这条测试要跟着改。 */
    private static final int BATCH_SIZE = 1000;

    /** 和实现里的 MAX_BATCHES 一致。 */
    private static final int MAX_BATCHES = 100;

    private ScheduleJobLogDao dao;
    private ScheduleJobLogCleanupTask task;

    @BeforeEach
    void setUp() {
        dao = mock(ScheduleJobLogDao.class);
        task = new ScheduleJobLogCleanupTask(dao);
    }

    /** 让「找边界」那次查询返回指定的 log_id；null 表示查不到。 */
    @SuppressWarnings("unchecked")
    private void boundary(Long logId) {
        if (logId == null) {
            when(dao.selectList(any(Wrapper.class))).thenReturn(List.of());
            return;
        }
        ScheduleJobLogEntity row = new ScheduleJobLogEntity();
        row.setLogId(logId);
        when(dao.selectList(any(Wrapper.class))).thenReturn(List.of(row));
    }

    @Test
    @DisplayName("边界查不到时一条删除都不能发")
    void doesNotDeleteAnythingWhenNoOldRows() {
        boundary(null);

        task.run("30");

        verify(dao, never()).delete(any());
    }

    @Test
    @DisplayName("不满一批就停：只发一次删除，不进入下一轮")
    void stopsAfterAPartialBatch() {
        boundary(12345L);
        // 第一批只删掉 7 行（< BATCH_SIZE），说明已经删完了
        when(dao.delete(any())).thenReturn(7);

        task.run("30");

        verify(dao, times(1)).delete(any());
    }

    @Test
    @DisplayName("满批就继续：删满两批之后遇到不满的一批才停")
    void continuesWhileBatchesAreFull() {
        boundary(12345L);
        when(dao.delete(any()))
                .thenReturn(BATCH_SIZE)
                .thenReturn(BATCH_SIZE)
                .thenReturn(3);

        task.run("30");

        verify(dao, times(3)).delete(any());
    }

    @Test
    @DisplayName("撞上批次上限必须停 —— 否则表异常大时会一直跑")
    void stopsAtTheBatchCap() {
        boundary(12345L);
        // 每批都是满的，永远删不完 —— 只有上限能让它停
        when(dao.delete(any())).thenReturn(BATCH_SIZE);

        task.run("30");

        verify(dao, times(MAX_BATCHES)).delete(any());
    }

    @Test
    @DisplayName("参数不合法时用默认值继续跑，不抛异常")
    void fallsBackOnBadParams() {
        // 一个定时任务因为参数没填而每次都失败，比用一个保守的默认值糟。
        // 这里验的是「不抛」——具体用了多少天由日志说明（实现里会打 WARN）。
        for (String bad : new String[]{null, "", "   ", "abc", "0", "-5"}) {
            boundary(null);
            task.run(bad);
        }
        // 边界都查不到，所以一次删除都不该发；关键是上面 6 次调用都没抛异常。
        verify(dao, never()).delete(any());
    }
}
