package com.mall.coupon.config;

import com.mall.coupon.service.SeckillGrabService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 订阅 SeckillPubSubConfig.ACTIVATE_CHANNEL，收到广播就清掉本地售罄标记。
 * 每个 pod（包括发广播的那个 pod 自己，Redis Pub/Sub 会把消息也投给发布者自己
 * 订阅的连接）都会执行这个方法，天然覆盖到所有副本。
 */
@Component
public class SeckillActivateListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(SeckillActivateListener.class);

    @Autowired
    private SeckillGrabService seckillGrabService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            Long relationId = Long.valueOf(new String(message.getBody(), StandardCharsets.UTF_8));
            seckillGrabService.clearLocalSoldOutFlag(relationId);
        } catch (Exception e) {
            log.warn("处理秒杀重新激活广播失败: {}", e.getMessage());
        }
    }
}
