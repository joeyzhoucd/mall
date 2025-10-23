package com.mall.thirdparty.controller;

import com.aliyun.oss.common.auth.ServiceSignature;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.mall.common.utils.R;
import com.mall.thirdparty.properties.OssProperties;
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

    
    @GetMapping("/policy")
    public R policy() {
        try {
            // Generate directory path with current date format yyyy-MM-dd/
            String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "/";

            // Create Policy with expiration time of 1 hour from now
            long expireTime = Instant.now().getEpochSecond() + 3600;
            String expiration = Instant.ofEpochSecond(expireTime)
                    .atZone(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));

            // Create Policy JSON
            Map<String, Object> policyMap = new HashMap<>();
            policyMap.put("expiration", expiration);

            // Create conditions list for V1 signature
            List<Object> conditions = new ArrayList<>();
            // Add bucket condition
            Map<String, String> bucketCondition = new HashMap<>();
            bucketCondition.put("bucket", properties.getBucketName());
            conditions.add(bucketCondition);
            // Add content-length-range condition (1 byte to 10MB)
            conditions.add(new Object[]{"content-length-range", 1, 10485760});
            // Add key condition
            conditions.add(new String[]{"starts-with", "$key", dateDir});
            // Add success_action_status condition for 201 response
            conditions.add(new String[]{"eq", "$success_action_status", "201"});

            policyMap.put("conditions", conditions);

            // Convert Policy to JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String policyJson = mapper.writeValueAsString(policyMap);
            // Encode using SDK Base64
            String encodedPolicy = BinaryUtil.toBase64String(policyJson.getBytes(StandardCharsets.UTF_8));

            // Debug output
            log.debug("Policy JSON: {}", policyJson);
            log.debug("Encoded Policy: {}", encodedPolicy);
            // Decode Base64 for verification
            String decodedPolicy = new String(Base64.getDecoder().decode(encodedPolicy), StandardCharsets.UTF_8);
            log.debug("Decoded Policy: {}", decodedPolicy);
            log.debug("AccessKeySecret: {}{}", properties.getAccessKeySecret().substring(0, 4), "****");

            // Generate signature using SDK ServiceSignature for V1 signature
            String signature = ServiceSignature.create().computeSignature(properties.getAccessKeySecret(), encodedPolicy);

            // Debug signature
            log.debug("Signature: {}", signature);

            // Return response data
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
            return R.error("Generate OSS signature failed: " + e.getMessage());
        }
    }
}