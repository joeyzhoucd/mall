package com.mall.coupon.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mall.common.utils.PageUtils;
import com.mall.coupon.entity.HomeSubjectEntity;

import java.util.Map;

/**
 * é¦–é¡µä¸“é¢˜è¡¨ã€jdé¦–é¡µä¸‹é¢å¾ˆå¤šä¸“é¢˜ï¼Œæ¯ä¸ªä¸“é¢˜é“¾æŽ¥æ–°çš„é¡µé¢ï¼Œå±•ç¤ºä¸“é¢˜å•†å“ä¿¡æ¯ã€‘
 *
 * @author joeyzhou
 * @email eryueshier@gmail.com
 * @date 2025-03-30 23:08:26
 */
public interface HomeSubjectService extends IService<HomeSubjectEntity> {

    PageUtils queryPage(Map<String, Object> params);
}

