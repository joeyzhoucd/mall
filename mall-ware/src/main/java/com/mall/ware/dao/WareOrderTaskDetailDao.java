package com.mall.ware.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.ware.entity.WareOrderTaskDetailEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


@Mapper
public interface WareOrderTaskDetailDao extends BaseMapper<WareOrderTaskDetailEntity> {

    /**
     * 用 compare-and-swap 的方式推进明细的锁定状态：只有当前状态等于 from 才会改成 to。
     * <p>
     * 这是整个库存释放/扣减流程的【并发闸门】。原来的写法是先查出明细、在内存里判断
     * {@code lockStatus != LOCKED 就返回}、再去改库存——判断和修改之间没有任何保护，
     * 两个执行流（多个副本、或者同一个副本上并发的 MQ 监听线程）可以同时通过这个判断，
     * 于是同一笔明细被处理两次：如果第二次的读发生在第一次的写之后，真实库存会被扣两次。
     * <p>
     * 改成 CAS 之后，"抢到处理权"这件事由数据库的行锁裁决，只有影响行数为 1 的那个
     * 执行流才继续往下动库存，其余的直接退出。判断本身就是修改，不存在中间窗口。
     *
     * @return 1 = 抢到了处理权，0 = 状态不是 from（别人已经处理过，或已被推进到别的状态）
     */
    int casLockStatus(@Param("id") Long id,
                      @Param("from") Integer from,
                      @Param("to") Integer to);

    /**
     * 原子自增重试次数，且【只在明细仍处于 LOCKED 时】生效。
     * <p>
     * 原来这里是 detailEntity.setRetryCount(current + 1) 之后 updateById 整行写回。
     * 两个问题：
     * <ul>
     *   <li>丢更新：两个执行流同时失败，各自读到 retryCount=1 都写 2，实际只算了一次，
     *       重试上限要更久才触发（相对轻微）。</li>
     *   <li>状态复活：MyBatis-Plus 的 updateById 会把实体里所有非空字段一并写回，
     *       其中包含【内存里那份已经过期的 lock_status】。如果另一个执行流已经把这条明细
     *       CAS 成 UNLOCKED，这次写回会把它改回 LOCKED，重试任务再次捡起它，
     *       CAS 又能成功一次 —— 库存被释放两次。这一条会直接抵消掉 casLockStatus 的作用。</li>
     * </ul>
     * 所以这里必须只更新 retry_count 这一列，并且把「仍是 LOCKED」写进 WHERE。
     *
     * @return 1 = 自增成功，0 = 明细已不是 LOCKED（别人处理完了，不该再累加）
     */
    int incrementRetryIfLocked(@Param("id") Long id);

    /** 重置重试次数（后台手动重试用），只改这一列。 */
    int resetRetryCount(@Param("id") Long id);
}
