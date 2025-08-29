package com.joeyzhoucd.thirdparty.controller;

import com.aliyun.oss.OSS;
import com.aliyun.oss.common.auth.ServiceSignature;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.joeyzhoucd.common.utils.R;
import com.joeyzhoucd.thirdparty.properties.OssProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("thirdparty/oss")
public class AliyunOssController {

    @Autowired
    private OssProperties properties;

    /**
     * 生成 OSS POST Policy 和签名，用于前端直传
     */
    @GetMapping("/policy")
    public R policy() {
        try {
            // 生成日期目录（格式：yyyy-MM-dd/，如 2025-08-28/）
            String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "/";

            // 设置 Policy 过期时间（1 小时后），格式为 ISO8601，包含毫秒
            long expireTime = Instant.now().getEpochSecond() + 3600;
            String expiration = Instant.ofEpochSecond(expireTime)
                    .atZone(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));

            // 创建 Policy JSON
            Map<String, Object> policyMap = new HashMap<>();
            policyMap.put("expiration", expiration);

            // 创建 conditions 列表，遵循官方 V1 签名示例
            List<Object> conditions = new ArrayList<>();
            // 添加 bucket 条件（对象格式）
            Map<String, String> bucketCondition = new HashMap<>();
            bucketCondition.put("bucket", properties.getBucketName());
            conditions.add(bucketCondition);
            // 添加 content-length-range 条件，限制文件大小（1 到 10MB）
            conditions.add(new Object[]{"content-length-range", 1, 10485760});
            // 添加 key 前缀条件
            conditions.add(new String[]{"starts-with", "$key", dateDir});
            // 添加 success_action_status 条件（与官方示例一致，使用 201）
            conditions.add(new String[]{"eq", "$success_action_status", "201"});

            policyMap.put("conditions", conditions);

            // 将 Policy 转换为 JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String policyJson = mapper.writeValueAsString(policyMap);
            // 使用阿里云 SDK 的 Base64 编码
            String encodedPolicy = BinaryUtil.toBase64String(policyJson.getBytes(StandardCharsets.UTF_8));

            // 调试日志
            log.debug("Policy JSON: {}", policyJson);
            log.debug("Encoded Policy: {}", encodedPolicy);
            // 验证 Base64 解码
            String decodedPolicy = new String(Base64.getDecoder().decode(encodedPolicy), StandardCharsets.UTF_8);
            log.debug("Decoded Policy: {}", decodedPolicy);
            log.debug("AccessKeySecret: {}{}", properties.getAccessKeySecret().substring(0, 4), "****");

            // 使用阿里云 SDK 的 ServiceSignature 计算签名（V1 签名）
            String signature = ServiceSignature.create().computeSignature(properties.getAccessKeySecret(), encodedPolicy);

            // 调试签名
            log.debug("Signature: {}", signature);

            // 构造返回数据
            Map<String, String> data = new HashMap<>();
            data.put("accessKeyId", properties.getAccessKeyId());
            data.put("host", "https://" + properties.getBucketName() + "." + properties.getEndpoint());
            data.put("policy", encodedPolicy);
            data.put("signature", signature);
            data.put("dir", dateDir);
            data.put("expire", String.valueOf(expireTime));

            return R.ok().put("data", data);
        } catch (Exception e) {
            e.printStackTrace();
            return R.error("生成 OSS 上传签名失败: " + e.getMessage());
        }
    }
}