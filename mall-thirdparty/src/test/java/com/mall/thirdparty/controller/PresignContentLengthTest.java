package com.mall.thirdparty.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 证明「把 size 签进预签名 URL」这件事真的成立。
 *
 * <h3>为什么这条测试是整个改动的地基</h3>
 * {@code ObjectStorageController} 原来的类注释断言
 * 「预签名 PUT 做不到限制大小 —— 签名里没有描述 body 长度的位置」。
 * 这次改动的全部前提就是那句话不对：{@code contentLength} 会作为
 * {@code Content-Length} 进入 {@code X-Amz-SignedHeaders}，从而被 S3 端校验。
 *
 * <p>如果这个前提不成立，那么控制器里那两个 {@code if} 就只是「服务端不发大文件的 URL」，
 * 而拿到 URL 的人照样能往里塞任意字节 —— 也就是<b>这个改动等于没做，而且没有任何症状</b>。
 * 所以必须有一条测试直接盯住 {@code X-Amz-SignedHeaders} 的内容。
 *
 * <h3>为什么不需要网络</h3>
 * 预签名是纯本地的签名计算（HMAC），不发任何请求。
 * 这个集群目前也没有部署对象存储，端到端上传验不了 ——
 * 但「签名里有没有 content-length」这件事是确定性的，本地就能验死。
 */
class PresignContentLengthTest {

    private static final long SIZE = 12_345L;

    /** 造一个和生产同构的 presigner：固定凭据 + 固定区域，纯本地。 */
    private static S3Presigner presigner() {
        return S3Presigner.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create("https://s3.example.com"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("AKIAEXAMPLE", "secretexample")))
                .build();
    }

    private static String presignedUrl(Long contentLength) {
        PutObjectRequest.Builder put = PutObjectRequest.builder()
                .bucket("mall-test")
                .key("2026-09-14/abc.jpg");
        if (contentLength != null) {
            put.contentLength(contentLength);
        }
        try (S3Presigner p = presigner()) {
            return p.presignPutObject(PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(600))
                    .putObjectRequest(put.build())
                    .build()).url().toString();
        }
    }

    private static String signedHeadersOf(String url) {
        // X-Amz-SignedHeaders=host%3Bcontent-length 这种形态，大小写和转义都可能变，
        // 所以统一成小写再看子串，而不是精确比字符串。
        String lower = url.toLowerCase(Locale.ROOT);
        int i = lower.indexOf("x-amz-signedheaders=");
        assertThat(i).as("预签名 URL 里必须有 X-Amz-SignedHeaders").isGreaterThan(-1);
        String tail = lower.substring(i + "x-amz-signedheaders=".length());
        int amp = tail.indexOf('&');
        return amp >= 0 ? tail.substring(0, amp) : tail;
    }

    @Test
    @DisplayName("设了 contentLength 之后，content-length 进入 X-Amz-SignedHeaders")
    void contentLengthIsSigned() {
        String signed = signedHeadersOf(presignedUrl(SIZE));

        // %3B 是分号的转义。两种形态都认，免得 SDK 换个编码方式这条就假阳性。
        assertThat(signed.replace("%3b", ";"))
                .as("content-length 必须被签进去 —— 否则服务端校验 size 只是个摆设，"
                        + "拿到 URL 的人照样能传任意大小")
                .contains("content-length");
    }

    @Test
    @DisplayName("【负控】不设 contentLength 时它不在签名头里 —— 证明上面那条不是恒真")
    void notSignedWhenAbsent() {
        String signed = signedHeadersOf(presignedUrl(null));

        // 没有这一条的话，上面那条测试可能只是"X-Amz-SignedHeaders 里碰巧
        // 总有 content-length"，断言就失去了判别力。
        assertThat(signed.replace("%3b", ";"))
                .as("不设 contentLength 时不该出现 —— 出现了说明这条断言区分不出有没有签")
                .doesNotContain("content-length");
    }

    @Test
    @DisplayName("签的是具体的那个数：换一个 size，签名本身必须变")
    void differentSizeProducesDifferentSignature() {
        String a = presignedUrl(SIZE);
        String b = presignedUrl(SIZE + 1);

        // 只有 size 不同，其余全一样。签名变了才说明 size 真的参与了计算，
        // 而不是"头名字在列表里但值没进哈希"。
        assertThat(sigOf(a))
                .as("size 变了签名却没变，说明它没真正参与签名计算")
                .isNotEqualTo(sigOf(b));
    }

    private static String sigOf(String url) {
        int i = url.indexOf("X-Amz-Signature=");
        assertThat(i).isGreaterThan(-1);
        String tail = url.substring(i + "X-Amz-Signature=".length());
        int amp = tail.indexOf('&');
        return amp >= 0 ? tail.substring(0, amp) : tail;
    }
}
