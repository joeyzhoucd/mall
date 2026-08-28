package com.mall.ware.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.ware.entity.WareOrderTaskDetailEntity;
import com.mall.ware.service.WareOrderTaskDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;




@RestController
@RequestMapping("ware/wareordertaskdetail")
public class WareOrderTaskDetailController {
    @Autowired
    private WareOrderTaskDetailService wareOrderTaskDetailService;

    
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = wareOrderTaskDetailService.queryPage(params);

        return R.ok().put("page", page);
    }


    
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		WareOrderTaskDetailEntity wareOrderTaskDetail = wareOrderTaskDetailService.getById(id);

        return R.ok().put("wareOrderTaskDetail", wareOrderTaskDetail);
    }

    
    /**
     * 【已移除 /save、/update、/delete】
     *
     * <p>这三个是 renren 生成器自动生成的，前端一次都没调用过（全仓 grep 为 0），
     * 但它们能直接改 {@code lock_status} —— 而那是库存锁定的状态机字段，
     * 由 {@link com.mall.ware.service.StockAtomicOps} 用 CAS
     * （{@code casLockStatus(detailId, from, to)}）保证只能按 锁定 -> 释放/扣减
     * 单向迁移一次。
     *
     * <p>一次裸的 {@code updateById} 就能把已经扣减的明细改回"锁定"，
     * 于是同一笔库存被释放两次、库存凭空增加；或者把在途明细改成"已释放"，
     * 于是订单取消时对应的库存永远回不来。两种都<b>不会报任何错</b>，
     * 只表现为对不上的库存账，而且事后无从追溯是谁写的。
     *
     * <p>合法的人工干预入口是 {@code POST /ware/waresku/fail/retry/{detailId}}，
     * 它走的是同一套 CAS，不会破坏不变式。查看用下面的 /list 和 /info。
     *
     * <p>补一句：整个仓库还有 40 个同样是「整实体 updateById」的生成器接口。
     * 绝大多数改的是描述性数据，问题不大；这里处理的是<b>会破坏并发不变式</b>的那几个
     * （另外两个是 WareSkuController 的 /update 和 SeckillSkuRelationController 的 /update）。
     */
}
