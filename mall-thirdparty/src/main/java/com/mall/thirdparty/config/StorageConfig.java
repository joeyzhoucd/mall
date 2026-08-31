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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * 对象存储客户端。用 S3 API，可以打 AWS S3 / MinIO / R2 / 阿里云 OSS 的兼容端点。
 *
 * <h3>Presigner 负责上传，S3Client 负责删除</h3>
 * 上传走<b>预签名 URL 让浏览器直传</b>，文件字节不经过后端。
 * 删除则必须由服务端做 —— 发一个预签名的 DELETE 给浏览器等于把删除权交出去，
 * 任何人拿到那个链接都能删对象。所以两件事用两个客户端，职责不混。
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

    /**
     * 服务端直接操作对象的客户端。
     *
     * <p>建这个 bean 之前只有 {@link S3Presigner} —— 当时的注释写着「唯一的用法是发预签名
     * 地址让浏览器直传，建 Client 只是多一条没人用的连接池」。现在有用途了：
     * 后台文件管理要<b>删对象</b>，而删除必须由服务端做（不能发一个预签名的 DELETE 给浏览器，
     * 那等于把删除权交出去，任何人拿到链接都能删）。
     *
     * <p>仍然<b>不</b>用它做上传：上传继续走预签名 PUT，字节不经过后端。
     * 删除只传 key、不传字节，开销和风险都不一样。
     */
    @Bean
    @ConditionalOnMissingBean
    public S3Client s3Client(StorageProperties properties) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                properties.getAccessKeyId(),
                                properties.getSecretAccessKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(properties.isPathStyleAccess())
                        .build());
        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
        }
        return builder.build();
    }
}
