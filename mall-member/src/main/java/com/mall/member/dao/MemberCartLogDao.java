package com.mall.member.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.member.entity.MemberCartLogEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MemberCartLogDao extends BaseMapper<MemberCartLogEntity> {

    /**
     * 批量插入。
     *
     * <p><b>为什么不用 MyBatis-Plus 的 saveBatch</b>：那个方法是循环单条 insert，
     * 只是共用一个 SqlSession 并按 batch size flush，网络往返仍然是 N 次。
     * 这里的量级是几十万条埋点，一次多值 INSERT 的差别是数量级的。
     *
     * <p>批量大小由调用方控制（见 mall-cart 的 CartEventLogger），
     * 不要在这里放开无上限 —— 单条 SQL 超过 max_allowed_packet 会整批失败。
     */
    @Insert("""
            <script>
            INSERT INTO ums_member_cart_log
              (member_id, sku_id, spu_id, action, quantity, create_time)
            VALUES
            <foreach collection="list" item="it" separator=",">
              (#{it.memberId}, #{it.skuId}, #{it.spuId}, #{it.action}, #{it.quantity}, #{it.createTime})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("list") List<MemberCartLogEntity> list);
}
