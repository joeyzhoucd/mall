package com.mall.coupon.controller;

import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import com.mall.coupon.entity.SeckillSkuRelationEntity;
import com.mall.coupon.service.SeckillSkuRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;




@RestController
@RequestMapping("coupon/seckillskurelation")
public class SeckillSkuRelationController {
    @Autowired
    private SeckillSkuRelationService seckillSkuRelationService;

    
    @RequestMapping("/list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = seckillSkuRelationService.queryPage(params);

        return R.ok().put("page", page);
    }


    
    @RequestMapping("/info/{id}")
    public R info(@PathVariable("id") Long id){
		SeckillSkuRelationEntity seckillSkuRelation = seckillSkuRelationService.getById(id);

        return R.ok().put("seckillSkuRelation", seckillSkuRelation);
    }

    
    @RequestMapping("/save")
    public R save(@RequestBody SeckillSkuRelationEntity seckillSkuRelation){
		seckillSkuRelationService.save(seckillSkuRelation);

        return R.ok();
    }

    
    /**
     * 修改秒杀关联的<b>非库存</b>字段。
     *
     * <h3>为什么 seckillCount / soldCount 不能从这里改</h3>
     * 秒杀的真实库存<b>不在数据库里</b>，而在 Redis 的 {@code seckill:stock:{relationId}}
     * 上，抢购是靠一段 Lua 脚本原子扣减那个键。数据库里的 {@code seckill_count}
     * 只是活动的"配置值"，它是通过
     * {@code POST /coupon/seckill/activate/{relationId}} 被<b>拷贝</b>进 Redis 的。
     * <p>
     * 直接改数据库的后果分两种，都很难查：
     * <ul>
     *   <li>改了但不重新 activate：库里写着 500，Redis 里还是 100 —— 后台显示的数字
     *       和实际能抢到的数量对不上，而且没有任何地方会报这个不一致。</li>
     *   <li>改了之后去 activate：activate 会<b>删掉 {@code seckill:user:{id}}</b>
     *       这个"谁已经抢过"的集合。活动进行中做这件事，等于让已经抢中的人可以再抢一次，
     *       超卖立刻发生。</li>
     * </ul>
     * {@code sold_count} 同理：它是统计值，不是可以随手改的数字。
     * <p>
     * 所以这里只放行价格、限购、排序这些配置字段。要调库存就走
     * 「先改配置，再在活动<b>未开始</b>时 activate」这条明确的路径。
     */
    @PostMapping("/update")
    public R update(@RequestBody SeckillSkuRelationEntity relation) {
        if (relation == null || relation.getId() == null) {
            return R.error("id 不能为空");
        }
        if (relation.getSeckillCount() != null || relation.getSoldCount() != null) {
            return R.error("这个接口不能改 seckillCount / soldCount。秒杀库存的真实来源是 Redis，"
                    + "数据库里的值要通过 POST /coupon/seckill/activate/{relationId} 才会生效；"
                    + "而 activate 会清空「谁已抢过」的记录，活动进行中执行会导致超卖。"
                    + "请在活动未开始时调整。");
        }
        SeckillSkuRelationEntity patch = new SeckillSkuRelationEntity();
        patch.setId(relation.getId());
        patch.setSeckillPrice(relation.getSeckillPrice());
        patch.setSeckillLimit(relation.getSeckillLimit());
        patch.setSeckillSort(relation.getSeckillSort());
        seckillSkuRelationService.updateById(patch);
        return R.ok();
    }

    @RequestMapping("/delete")
    public R delete(@RequestBody Long[] ids){
		seckillSkuRelationService.removeByIds(Arrays.asList(ids));

        return R.ok();
    }

}
