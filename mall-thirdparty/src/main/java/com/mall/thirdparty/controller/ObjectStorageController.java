package com.mall.thirdparty.controller;

import com.mall.common.utils.R;
import com.mall.thirdparty.properties.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import java.util.LinkedHashMap;
import java.util.List;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 发预签名上传地址，浏览器拿着它直传对象存储，文件字节不经过后端。
 *
 * <h3>取代了原来的 /thirdparty/oss/policy</h3>
 * 原来那个用的是阿里云 OSS 的 PostObject policy + V1 签名，是这套系统里最后一处
 * 绑死在某一家云上的代码。现在换成 S3 的预签名 PUT ——
 * 同一份代码能打 AWS S3、MinIO、R2、以及阿里云 OSS 的 S3 兼容端点。
 *
 * <h3>一个安全上的实质改进：对象名由服务端生成</h3>
 * 老实现把 key 交给前端拼，服务端只用 policy 约束了「必须以今天的日期目录开头」。
 * 也就是说任何拿到 policy 的人都可以在那个目录下<b>覆盖已有对象</b> ——
 * 比如把别人刚传的商品图换掉。现在 key 完全由服务端生成
 * （日期目录 + UUID + 扩展名），前端只能传它拿到的那一个，覆盖不了任何东西。
 *
 * <h3>大小限制（2026-09-14 补上，之前这里写的结论是错的）</h3>
 * 原来这段写的是「预签名 PUT 做不到这件事 —— 签名里没有描述 body 长度的位置」，
 * <b>那句话不准确</b>。SigV4 会把<b>显式设置的头</b>列进 {@code X-Amz-SignedHeaders}
 * 并纳入签名计算，而 {@code contentLength} 对应的就是 {@code Content-Length} 头。
 * 签进去之后，S3 端拿<b>实际收到的</b> Content-Length 重算签名，对不上就是
 * 403 {@code SignatureDoesNotMatch}。
 *
 * <p>所以现在 {@code /presign} 的 {@code size} 参数是<b>必填</b>的，并且会被签进 URL。
 * 它不是范围限制而是<b>精确匹配</b>：这个 URL 只能上传恰好 {@code size} 字节 ——
 * 比 OSS 那个 {@code content-length-range} 还严。而浏览器在上传前就知道
 * {@code File.size}，对调用方没有额外负担。
 *
 * <p><b>只在服务端校验 size 是不够的</b>：那只是拒绝为一个大文件发 URL；
 * 拿到 URL 之后往里塞多少字节，服务端管不着 —— 不签进去的话，
 * 一个为 1KB 申请的 URL 可以用来传 1GB。
 *
 * <p>仍然<b>建议在存储侧再加一道</b> bucket policy 的
 * {@code s3:content-length-range}：这里这道防的是"拿着合法 URL 超量上传"，
 * bucket policy 防的是"这套代码以外的任何路径"。两者不重叠，都该有。
 * 那一道要在云上配，不在本仓库里。
 *
 * <p>另外两道仍然在：
 * <ul>
 *   <li>扩展名白名单（防的是「上传 .html/.svg 到同源域名下」这类问题，不是大小）；</li>
 *   <li>有效期 10 分钟，缩小链接泄漏后的可用窗口。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("thirdparty/oss")
public class ObjectStorageController {

    /**
     * 允许上传的扩展名。
     *
     * <p>关键的不是"只让传图片"，而是<b>不能让人往这个域名下传可执行的东西</b>：
     * .html 和 .svg 都能带脚本，如果对象存储和站点同源（或者被 CDN 挂在同一个域下），
     * 那就是一个存储型 XSS 的入口。
     */
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp", "mp4", "pdf");

    @Autowired
    private StorageProperties properties;

    @Autowired
    private S3Presigner presigner;

    @Autowired
    private S3Client s3Client;

    /**
     * @param filename 原始文件名，只用来取扩展名，不作为对象名的一部分
     * @param size     文件字节数。<b>必填，而且会被签进 URL</b>，见下面的说明
     */
    @GetMapping("/presign")
    public R presign(@RequestParam("filename") String filename,
                     @RequestParam("size") long size,
                     @RequestParam(value = "contentType", required = false) String contentType) {
        String ext = extensionOf(filename);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            return R.error("不支持的文件类型: " + (ext.isEmpty() ? "(无扩展名)" : ext)
                    + "，允许的是 " + ALLOWED_EXTENSIONS);
        }
        if (size <= 0) {
            return R.error("size 必须是正数（浏览器侧取 File.size）");
        }
        if (size > properties.getMaxUploadBytes()) {
            return R.error("文件太大：" + size + " 字节，上限 " + properties.getMaxUploadBytes() + " 字节");
        }

        String key = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;

        PutObjectRequest.Builder put = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                // ------------------------------------------------------------------
                // 【这一行才是大小限制，上面那两个 if 只是提前给个好错误信息】
                // ------------------------------------------------------------------
                // 类注释里原来写着"预签名 PUT 做不到限制大小，签名里没有描述 body 长度
                // 的位置"—— 那句话不准确。SigV4 会把【显式设置的头】列进
                // X-Amz-SignedHeaders 并纳入签名计算，而 contentLength 对应的就是
                // Content-Length 头。签进去之后，S3 端会拿【实际收到的】Content-Length
                // 重算签名，对不上就是 403 SignatureDoesNotMatch。
                //
                // 所以它不是"范围限制"而是【精确匹配】：这个 URL 只能上传恰好 size 字节。
                // 这比 OSS 那个 content-length-range 还严 —— 而浏览器在上传前就知道
                // File.size，所以对调用方没有额外负担。
                //
                // 【只做前两个 if 是不够的】那只是服务端拒绝发一个大文件的 URL；
                // 拿到 URL 之后往里塞多少字节，服务端管不着 ——
                // 不签进去的话，一个为 1KB 申请的 URL 可以用来传 1GB。
                //
                // 【仍然建议在存储侧再加一道】bucket policy 的 s3:content-length-range。
                // 这里这道防的是"拿着合法 URL 超量上传"，
                // bucket policy 防的是"这套代码以外的任何路径"。两者不重叠。
                .contentLength(size);
        // Content-Type 一旦签进去，浏览器上传时就必须带一模一样的值，否则签名不匹配。
        // 所以下面的响应里把它一起回给前端，让前端照着设，而不是让前端自己猜。
        if (contentType != null && !contentType.isBlank()) {
            put.contentType(contentType);
        }

        PresignedPutObjectRequest presigned = presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofSeconds(properties.getPresignExpireSeconds()))
                        .putObjectRequest(put.build())
                        .build());

        Map<String, Object> data = new HashMap<>();
        data.put("uploadUrl", presigned.url().toString());
        data.put("method", "PUT");
        data.put("key", key);
        data.put("publicUrl", publicUrlOf(key));
        data.put("expiresInSeconds", properties.getPresignExpireSeconds());
        // 这两个回给前端不是"提示"，是【契约】：上传时的 Content-Length 必须
        // 恰好等于 signedContentLength，否则 S3 端重算签名对不上，返回 403。
        // 前端照着设即可，不用自己算。
        data.put("signedContentLength", size);
        data.put("maxUploadBytes", properties.getMaxUploadBytes());
        if (contentType != null && !contentType.isBlank()) {
            data.put("requiredHeaders", Map.of("Content-Type", contentType));
        }
        return R.ok().put("data", data);
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 拼对外可访问的 URL。
     *
     * <p>没有直接用预签名 URL 去掉查询串来拼 —— 那样得到的是<b>上传端点</b>，
     * 而对外访问通常走另一个域（CDN）。两者混淆的表现是「上传成功但图片打不开」，
     * 或者更糟：把带签名的上传地址存进了数据库，过期之后所有图片一起失效。
     */
    private String publicUrlOf(String key) {
        if (properties.getPublicBaseUrl() != null && !properties.getPublicBaseUrl().isBlank()) {
            return trimTrailingSlash(properties.getPublicBaseUrl()) + "/" + key;
        }
        String endpoint = trimTrailingSlash(properties.getEndpoint() == null ? "" : properties.getEndpoint());
        if (properties.isPathStyleAccess()) {
            return endpoint + "/" + properties.getBucket() + "/" + key;
        }
        // 虚拟主机风格：把 bucket 插到 host 前面
        int schemeEnd = endpoint.indexOf("://");
        if (schemeEnd < 0) {
            return endpoint + "/" + properties.getBucket() + "/" + key;
        }
        return endpoint.substring(0, schemeEnd + 3) + properties.getBucket() + "."
                + endpoint.substring(schemeEnd + 3) + "/" + key;
    }

    private String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * 按 key 批量删除对象。给后台的文件管理用。
     *
     * <p>删除由<b>服务端</b>做，不发预签名的 DELETE 给浏览器 —— 那等于把删除权交出去，
     * 任何人拿到那个链接都能删对象，而且链接在有效期内无法撤销。
     *
     * <p>S3 的 DeleteObjects 对<b>不存在的 key 返回成功</b>（幂等）。这正是想要的：
     * 调用方可能重试，重复删不该报错。所以「删了几个」这个数字不代表「原本存在几个」，
     * 不要拿它去推断状态。
     */
    @PostMapping("/delete")
    public R delete(@RequestBody List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return R.ok().put("deleted", 0);
        }
        // 一次最多 1000 个是 S3 的硬限制，超了整个请求会被拒。分批发。
        // 后台一次删的量远达不到，但这个限制不写出来的话，将来批量清理时会突然失败。
        int deleted = 0;
        try {
            for (int i = 0; i < keys.size(); i += 1000) {
                List<ObjectIdentifier> batch = keys.subList(i, Math.min(i + 1000, keys.size()))
                        .stream()
                        .filter(k -> k != null && !k.isBlank())
                        .map(k -> ObjectIdentifier.builder().key(k).build())
                        .toList();
                if (batch.isEmpty()) {
                    continue;
                }
                s3Client.deleteObjects(DeleteObjectsRequest.builder()
                        .bucket(properties.getBucket())
                        .delete(Delete.builder().objects(batch).build())
                        .build());
                deleted += batch.size();
            }
        } catch (Exception e) {
            log.error("删除对象失败 keys={}", keys, e);
            return R.error("删除对象失败: " + e.getMessage());
        }
        return R.ok().put("deleted", deleted);
    }

    /**
     * 当前生效的存储配置，<b>只回非密部分</b>。
     *
     * <p>后台原来的"云存储配置"页是一个能填七牛/阿里云/腾讯云 AccessKey+SecretKey 的表单，
     * 存进数据库、随时可改。<b>那个设计不实现</b>：把云厂商密钥放进一张 CRUD 表、
     * 再摆一个编辑框在后台页面上，等于任何拿到后台账号的人都能读走或替换掉存储凭据；
     * 而且密钥进了数据库就会进备份、进从库、进binlog。
     * 本项目的密钥走 Sealed Secret 注入环境变量，改配置是一次部署，不是一次点击。
     *
     * <p>所以这里只回「现在连的是哪儿」，让运维能在界面上确认配置生效了，
     * 但读不到也改不了任何凭据。对应地前端那个表单改成了只读展示。
     */
    @GetMapping("/config")
    public R config() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", "S3 兼容对象存储");
        data.put("endpoint", properties.getEndpoint());
        data.put("region", properties.getRegion());
        data.put("bucket", properties.getBucket());
        data.put("pathStyleAccess", properties.isPathStyleAccess());
        data.put("publicBaseUrl", properties.getPublicBaseUrl());
        data.put("presignExpireSeconds", properties.getPresignExpireSeconds());
        data.put("allowedExtensions", ALLOWED_EXTENSIONS);
        // 前端在选文件那一刻就能拦住超大的，不用先换一趟 presign 才知道超了。
        data.put("maxUploadBytes", properties.getMaxUploadBytes());
        // 只说明凭据是否已注入，不回任何片段 —— 连前 4 位都不回：
        // AccessKeyId 的前缀本身就能透露云厂商和账号族。
        data.put("credentialsConfigured",
                properties.getAccessKeyId() != null && !properties.getAccessKeyId().isBlank());
        return R.ok().put("data", data);
    }
}
