package com.mall.thirdparty.config;

import com.mall.thirdparty.properties.StorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * 对象存储客户端。用 S3 API，可以打 AWS S3 / MinIO / R2 / 阿里云 OSS 的兼容端点。
 *
 * <h3>这里只建 Presigner，不建 S3Client</h3>
 * 当前唯一的用法是<b>发预签名 URL 让浏览器直传</b>，文件字节不经过后端。
 * 建一个 S3Client 会多一条到对象存储的连接池和一份鉴权状态，而没有任何地方用它。
 * 以后真需要服务端读写对象（比如生成缩略图）再加，那时它的用途也清楚。
 *
 * <p>浏览器直传本身是有意的：文件走后端意味着每个上传都占住一个请求线程和一份内存缓冲，
 * 10MB 的图片并发上传几十个就能把服务打满 —— 而这件事对业务没有任何价值。
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    @ConditionalOnMissingBean
    public S3Presigner s3Presigner(StorageProperties properties) {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                properties.getAccessKeyId(),
                                properties.getSecretAccessKey())))
                // pathStyleAccess 必须和实际服务端一致，否则表现为 DNS 解析不到子域名
                // 或者 404，而不是一条指向配置的报错。
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess())
                        .build());

        // endpoint 留空时走 AWS 的默认端点（按 region 推导）；
        // 打阿里云 OSS / MinIO 这类兼容服务时必须显式指定。
        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        return builder.build();
    }
}
