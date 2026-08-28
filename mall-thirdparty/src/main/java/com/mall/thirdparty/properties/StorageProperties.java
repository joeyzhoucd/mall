package com.mall.thirdparty.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对象存储配置。
 *
 * <h3>为什么前缀从 aliyun.oss 改成 mall.storage</h3>
 * 配置的名字本身也是一种绑定。叫 {@code aliyun.oss} 的话，即使底下换成了 S3 API，
 * 部署到 MinIO 或 R2 时仍然要写一段 {@code aliyun.*} 配置 —— 看的人会以为
 * 这套系统还在用阿里云。名字要能反映它实际是什么：一个 S3 兼容的对象存储。
 *
 * <h3>几个字段为什么必须有</h3>
 * <ul>
 *   <li>{@code region}：S3 的签名算法（SigV4）把 region 算进签名里。填错了
 *       不会报「region 错误」，而是报签名不匹配 —— 排查方向会被带偏。
 *       打非 AWS 的兼容端点时它只是一个参与签名的字符串，两边一致即可。</li>
 *   <li>{@code pathStyleAccess}：AWS 默认用虚拟主机风格
 *       （{@code https://bucket.endpoint/key}），而 MinIO 和大多数自建服务
 *       只支持路径风格（{@code https://endpoint/bucket/key}）。
 *       搞错的表现是 DNS 解析不到那个子域名，或者 404 —— 同样不指向配置。</li>
 *   <li>{@code publicBaseUrl}：上传完之后要给前端一个可访问的 URL。
 *       它<b>不一定</b>等于上传用的 endpoint —— 生产上通常前面挂了 CDN。
 *       留空时回落到按 endpoint 拼。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "mall.storage")
public class StorageProperties {

    /** S3 兼容端点，例如 https://oss-cn-chengdu.aliyuncs.com 或 http://minio:9000 */
    private String endpoint;

    /** 参与 SigV4 签名。打非 AWS 端点时只要求两边一致，不要求是真实的 AWS region。 */
    private String region = "us-east-1";

    private String accessKeyId;

    private String secretAccessKey;

    private String bucket;

    /** MinIO 等自建服务必须为 true；AWS S3 用 false。 */
    private boolean pathStyleAccess = false;

    /** 对外访问的基地址（通常是 CDN）。留空则按 endpoint + 风格拼出来。 */
    private String publicBaseUrl;

    /** 预签名链接的有效期（秒）。默认 10 分钟：够上传，又不至于泄漏后长期可用。 */
    private long presignExpireSeconds = 600;

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }

    public String getSecretAccessKey() { return secretAccessKey; }
    public void setSecretAccessKey(String secretAccessKey) { this.secretAccessKey = secretAccessKey; }

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public boolean isPathStyleAccess() { return pathStyleAccess; }
    public void setPathStyleAccess(boolean pathStyleAccess) { this.pathStyleAccess = pathStyleAccess; }

    public String getPublicBaseUrl() { return publicBaseUrl; }
    public void setPublicBaseUrl(String publicBaseUrl) { this.publicBaseUrl = publicBaseUrl; }

    public long getPresignExpireSeconds() { return presignExpireSeconds; }
    public void setPresignExpireSeconds(long presignExpireSeconds) { this.presignExpireSeconds = presignExpireSeconds; }
}
