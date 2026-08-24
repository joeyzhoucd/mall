package com.mall.coupon.service.impl;

import com.mall.common.constant.ErrorCode;
import com.mall.common.constant.MqConstants;
import com.mall.common.constant.ResponseKeys;
import com.mall.common.constant.SeckillMessageStatus;
import com.mall.common.to.SeckillOrderTo;
import com.mall.common.utils.R;
import com.mall.common.utils.RUtils;
import com.mall.coupon.entity.SeckillLocalMessageEntity;
import com.mall.coupon.entity.SeckillSkuRelationEntity;
import com.mall.coupon.feign.MemberFeignService;
import com.mall.coupon.feign.ProductFeignService;
import com.mall.coupon.service.SeckillGrabService;
import com.mall.coupon.service.SeckillLocalMessageService;
import com.mall.coupon.service.SeckillSkuRelationService;
import com.mall.coupon.vo.MemberAddressVo;
import com.mall.coupon.vo.SeckillGrabResultVo;
import com.mall.coupon.vo.SkuInfoVo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service("seckillGrabService")
public class SeckillGrabServiceImpl implements SeckillGrabService {

    private static final Logger log = LoggerFactory.getLogger(SeckillGrabServiceImpl.class);
    private static final long CONFIRM_WAIT_SECONDS = 3;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private SeckillSkuRelationService seckillSkuRelationService;

    @Autowired
    private SeckillLocalMessageService seckillLocalMessageService;

    @Autowired
    private MemberFeignService memberFeignService;

    @Autowired
    private ProductFeignService productFeignService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private final RedisScript<Long> grabScript =
            new DefaultRedisScript<>(readLua(), Long.class);

    private static String readLua() {
        try {
            return org.springframework.util.StreamUtils.copyToString(
                    new ClassPathResource("lua/seckill_grab.lua").getInputStream(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("加载 seckill_grab.lua 失败", e);
        }
    }

    private String stockKey(Long relationId) {
        return "seckill:stock:" + relationId;
    }

    private String userKey(Long relationId) {
        return "seckill:user:" + relationId;
    }

    private String infoKey(Long relationId) {
        return "seckill:info:" + relationId;
    }

    @Override
    public boolean activate(Long relationId) {
        SeckillSkuRelationEntity relation = seckillSkuRelationService.getById(relationId);
        if (relation == null || relation.getSeckillCount() == null) {
            return false;
        }
        int count = relation.getSeckillCount().intValue();
        redisTemplate.opsForValue().set(stockKey(relationId), String.valueOf(count));
        redisTemplate.delete(userKey(relationId));

        // 把 skuId/秒杀价/商品名/图片一起缓存进 Redis，供 grab() 直接读取——避免每一次
        // 抢购请求都重新查一次数据库和商品服务，也避免 relation 行在活动进行中被后台
        // 删除时 grab() 因为查不到而 NPE（那样的话已经在 Lua 里扣掉的库存也没法回滚）。
        SkuInfoVo skuInfo = lookupSkuInfo(relation.getSkuId());
        Map<String, String> info = new HashMap<>();
        info.put("skuId", String.valueOf(relation.getSkuId()));
        info.put("seckillPrice", relation.getSeckillPrice().toPlainString());
        info.put("skuName", skuInfo != null && skuInfo.getSkuName() != null ? skuInfo.getSkuName() : "");
        info.put("skuPic", skuInfo != null && skuInfo.getSkuDefaultImg() != null ? skuInfo.getSkuDefaultImg() : "");
        redisTemplate.opsForHash().putAll(infoKey(relationId), info);
        return true;
    }

    @Override
    public SeckillGrabResultVo grab(Long relationId, Long memberId, String username) {
        SeckillGrabResultVo result = new SeckillGrabResultVo();

        Long grabResult = redisTemplate.execute(grabScript,
                java.util.Arrays.asList(stockKey(relationId), userKey(relationId)),
                String.valueOf(memberId));

        if (grabResult == null || grabResult == -2) {
            result.setSuccess(false);
            result.setFailReason(ErrorCode.SECKILL_NOT_ACTIVE);
            return result;
        }
        if (grabResult == -1) {
            result.setSuccess(false);
            result.setFailReason(ErrorCode.SECKILL_ALREADY_GRABBED);
            return result;
        }
        if (grabResult == 0) {
            result.setSuccess(false);
            result.setFailReason(ErrorCode.SECKILL_SOLD_OUT);
            return result;
        }

        // grabResult == 1：Redis 网关这一步赢了，从这里往下人数已经被库存上限限流住。
        // 从这里开始任何异常都必须先把 Redis 那份"库存-1、用户已抢"状态回滚掉再对外报错，
        // 否则库存名额会凭空消失——Lua 那步本身不会再失败，但下面这些数据库/Feign/MQ
        // 操作都可能失败，统一在这一层兜底，不能只捕获 DuplicateKeyException 这一种。
        try {
            return doGrab(relationId, memberId, username);
        } catch (Exception e) {
            log.error("relationId={} memberId={} 抢购流程异常,已回滚 Redis 状态", relationId, memberId, e);
            rollbackRedisGrab(relationId, memberId);
            result.setSuccess(false);
            result.setFailReason(ErrorCode.SECKILL_SYSTEM_ERROR);
            return result;
        }
    }

    private SeckillGrabResultVo doGrab(Long relationId, Long memberId, String username) {
        SeckillGrabResultVo result = new SeckillGrabResultVo();

        Map<Object, Object> raw = redisTemplate.opsForHash().entries(infoKey(relationId));
        if (raw == null || raw.isEmpty()) {
            // 正常不会发生：activate() 里 stockKey 和 infoKey 是一起写的，Lua 已经确认
            // stockKey 存在。真出现说明数据不一致，当异常处理，交给外层统一回滚 Redis。
            throw new IllegalStateException("relationId=" + relationId + " 缺少秒杀信息缓存(seckill:info)");
        }
        Long skuId = Long.valueOf((String) raw.get("skuId"));
        String skuName = (String) raw.get("skuName");
        String skuPic = (String) raw.get("skuPic");
        BigDecimal seckillPrice = new BigDecimal((String) raw.get("seckillPrice"));

        SeckillLocalMessageEntity message = seckillLocalMessageService.getByRelationAndMember(relationId, memberId);
        boolean isRetry = message != null && message.getStatus() != null
                && message.getStatus() == SeckillMessageStatus.SEND_FAILED;

        if (message != null && !isRetry) {
            // Redis 说这是这个用户第一次赢，但数据库已经有一条非"发送失败"的记录——
            // 只会发生在 activate() 重新激活同一场（清空了 Redis 抢购名单，但没清库表）
            // 之后，一个上一轮已经成功抢过的用户又抢了一次。这一单不该被履行，把
            // 这次白扣的库存还回去。
            rollbackRedisGrab(relationId, memberId);
            result.setSuccess(false);
            result.setFailReason(ErrorCode.SECKILL_ALREADY_GRABBED);
            return result;
        }

        if (message == null) {
            Long addrId = lookupDefaultAddressId(memberId);
            try {
                message = seckillLocalMessageService.createPending(
                        relationId, memberId, skuId, skuName, skuPic, seckillPrice, addrId);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 并发竞态：两个请求同时查到没有记录，一个插入成功一个撞了唯一索引。
                message = seckillLocalMessageService.getByRelationAndMember(relationId, memberId);
                boolean raceIsRetry = message != null && message.getStatus() != null
                        && message.getStatus() == SeckillMessageStatus.SEND_FAILED;
                if (!raceIsRetry) {
                    rollbackRedisGrab(relationId, memberId);
                    result.setSuccess(false);
                    result.setFailReason(ErrorCode.SECKILL_ALREADY_GRABBED);
                    return result;
                }
                isRetry = true;
            }
        }

        if (isRetry && message.getAddrId() == null) {
            Long addrId = lookupDefaultAddressId(memberId);
            if (addrId != null) {
                seckillLocalMessageService.updateAddr(message.getId(), addrId);
                message.setAddrId(addrId);
            }
        }

        if (message.getAddrId() == null) {
            // 没有默认地址：先只占住这个抢购名额，不发 MQ——等前端确认页选完地址后
            // 调用 submitAddress 才真正建单，不然地址是空的订单没法用。
            result.setSuccess(true);
            result.setHasDefaultAddress(false);
            result.setMessageId(message.getId());
            return result;
        }

        boolean sent = publishAndWaitConfirm(message, username);
        if (!sent) {
            seckillLocalMessageService.markSendFailed(message.getId());
            rollbackRedisGrab(relationId, memberId);
            result.setSuccess(false);
            result.setFailReason(ErrorCode.SECKILL_MQ_FAILED);
            return result;
        }

        seckillLocalMessageService.markSent(message.getId());
        result.setSuccess(true);
        result.setHasDefaultAddress(true);
        result.setMessageId(message.getId());
        return result;
    }

    @Override
    public SeckillGrabResultVo submitAddress(Long messageId, Long memberId, Long addrId, String username) {
        SeckillGrabResultVo result = new SeckillGrabResultVo();

        SeckillLocalMessageEntity message = seckillLocalMessageService.getById(messageId);
        if (message == null || !message.getMemberId().equals(memberId)) {
            result.setSuccess(false);
            result.setFailReason(ErrorCode.SECKILL_MESSAGE_INVALID);
            return result;
        }
        int status = message.getStatus() == null ? SeckillMessageStatus.PENDING : message.getStatus();
        if (status != SeckillMessageStatus.PENDING) {
            // 已经发过 MQ 或已经建单了：重复提交直接把已有结果原样返回，不再重发一遍。
            boolean failed = status == SeckillMessageStatus.SEND_FAILED;
            result.setSuccess(!failed);
            result.setHasDefaultAddress(true);
            result.setMessageId(message.getId());
            if (failed) {
                result.setFailReason(ErrorCode.SECKILL_MQ_FAILED);
            }
            return result;
        }

        seckillLocalMessageService.updateAddr(messageId, addrId);
        message.setAddrId(addrId);

        boolean sent = publishAndWaitConfirm(message, username);
        if (!sent) {
            seckillLocalMessageService.markSendFailed(messageId);
            rollbackRedisGrab(message.getRelationId(), memberId);
            result.setSuccess(false);
            result.setFailReason(ErrorCode.SECKILL_MQ_FAILED);
            return result;
        }

        seckillLocalMessageService.markSent(messageId);
        result.setSuccess(true);
        result.setHasDefaultAddress(true);
        result.setMessageId(messageId);
        return result;
    }

    @Override
    public void handleOrderCreated(Long messageId, String orderSn) {
        SeckillLocalMessageEntity message = seckillLocalMessageService.getById(messageId);
        if (message == null) {
            return;
        }
        int status = message.getStatus() == null ? SeckillMessageStatus.PENDING : message.getStatus();
        if (status == SeckillMessageStatus.ORDER_CREATED) {
            return;
        }
        seckillLocalMessageService.markOrderCreated(messageId, orderSn);
        seckillSkuRelationService.incrementSoldCount(message.getRelationId());
    }

    private Long lookupDefaultAddressId(Long memberId) {
        try {
            R addressResp = memberFeignService.getAddress(memberId);
            List<MemberAddressVo> addresses = RUtils.getData(
                    addressResp, ResponseKeys.ADDRESS, objectMapper, new TypeReference<List<MemberAddressVo>>() {});
            if (addresses == null) {
                return null;
            }
            return addresses.stream()
                    .filter(a -> a.getDefaultStatus() != null && a.getDefaultStatus() == 1)
                    .map(MemberAddressVo::getId)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("memberId={} 查询默认地址失败,当没有默认地址处理: {}", memberId, e.getMessage());
            return null;
        }
    }

    private SkuInfoVo lookupSkuInfo(Long skuId) {
        try {
            R resp = productFeignService.getSkuInfo(skuId);
            return RUtils.getData(resp, ResponseKeys.SKU_INFO, objectMapper, new TypeReference<SkuInfoVo>() {});
        } catch (Exception e) {
            log.warn("skuId={} 查询商品信息失败: {}", skuId, e.getMessage());
            return null;
        }
    }

    private boolean publishAndWaitConfirm(SeckillLocalMessageEntity message, String username) {
        SeckillOrderTo to = new SeckillOrderTo();
        to.setLocalMessageId(message.getId());
        to.setRelationId(message.getRelationId());
        to.setMemberId(message.getMemberId());
        to.setUsername(username);
        to.setSkuId(message.getSkuId());
        to.setSkuName(message.getSkuName());
        to.setSkuPic(message.getSkuPic());
        to.setSeckillPrice(message.getSeckillPrice());
        to.setAddrId(message.getAddrId());

        CorrelationData correlationData = new CorrelationData(String.valueOf(message.getId()));
        try {
            rabbitTemplate.convertAndSend(MqConstants.SECKILL_EVENT_EXCHANGE, MqConstants.SECKILL_ORDER_ROUTING_KEY, to, correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture().get(CONFIRM_WAIT_SECONDS, TimeUnit.SECONDS);
            return confirm != null && confirm.isAck();
        } catch (Exception e) {
            log.error("messageId={} 发送秒杀MQ消息失败: {}", message.getId(), e.getMessage());
            return false;
        }
    }

    private void rollbackRedisGrab(Long relationId, Long memberId) {
        redisTemplate.opsForValue().increment(stockKey(relationId));
        redisTemplate.opsForSet().remove(userKey(relationId), String.valueOf(memberId));
    }
}
