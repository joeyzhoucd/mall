package com.mall.member.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.Query;
import com.mall.member.dao.MemberCollectSpuDao;
import com.mall.member.entity.MemberCollectSpuEntity;
import com.mall.member.service.MemberCollectSpuService;
import org.springframework.stereotype.Service;

import java.util.Map;


@Service("memberCollectSpuService")
public class MemberCollectSpuServiceImpl extends ServiceImpl<MemberCollectSpuDao, MemberCollectSpuEntity> implements MemberCollectSpuService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<MemberCollectSpuEntity> page = this.page(
                new Query<MemberCollectSpuEntity>().getPage(params),
                new QueryWrapper<>()
        );

        return new PageUtils(page);
    }

}
