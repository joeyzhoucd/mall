package com.mall.thirdparty.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.mall.thirdparty.properties.OssProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OssProperties.class) // Enable configuration properties
public class OssConfig {

    @Bean
    @ConditionalOnMissingBean
    public OSS ossClient(OssProperties properties) {
        return new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret()
        );
    }
}