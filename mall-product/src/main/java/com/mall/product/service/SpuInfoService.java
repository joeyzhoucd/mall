package com.mall.product.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.product.entity.SpuInfoEntity;
import com.mall.product.vo.SpuSaveVo;

import java.util.Map;


public interface SpuInfoService extends IService<SpuInfoEntity> {

    PageUtils queryPage(Map<String, Object> params);

    
    void saveSpuInfo(SpuSaveVo spuSaveVo);

    
    void upSpu(Long spuId);

    /**
     * 删除 SPU 及其下全部数据。
     *
     * <p>顺序是先子后父，中间任何一步失败整笔回滚（{@code @Transactional}）。
     * 没有事务的话，删到一半失败会留下「SKU 没了但 SPU 还在」的半截数据，
     * 后台列表里那个 SPU 点进去是空的，而且再删一次仍然会失败在同一步。
     *
     * <p>ES 里的文档也要一起删。漏掉的话商品在后台已经不存在了，
     * 搜索页却还能搜到、还能点进详情 —— 详情页去数据库查不到就报 500。
     * 这是「删除了但没删干净」里最容易被当成偶发故障的一种。
     */
    void removeSpus(java.util.List<Long> spuIds);

    /**
     * 商品下架：置 publish_status=0，并从 ES 索引里移除。
     *
     * <p>和 {@link #upSpu(Long)} 相对。两件事都要做：只改库不删索引，
     * 商品仍然搜得到；只删索引不改库，下次任何一次重新上架/同步又会把它放回去。
     */
    void downSpu(Long spuId);

}
