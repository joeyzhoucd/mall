package io.renren.modules.oss.cloud;

import io.renren.common.validator.group.AliyunGroup;
import io.renren.common.validator.group.QcloudGroup;
import io.renren.common.validator.group.QiniuGroup;
import lombok.Data;
import org.hibernate.validator.constraints.Range;
import org.hibernate.validator.constraints.URL;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * Cloud storage configuration
 */
@Data
public class CloudStorageConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    // Type 1: Qiniu 2: Aliyun 3: Qcloud
    @Range(min=1, max=3, message = "Type error")
    private Integer type;

    // Qiniu domain name
    @NotBlank(message="Qiniu domain name cannot be empty", groups = QiniuGroup.class)
    @URL(message = "Qiniu domain name format error", groups = QiniuGroup.class)
    private String qiniuDomain;
    // Qiniu prefix
    private String qiniuPrefix;
    // Qiniu ACCESS_KEY
    @NotBlank(message="Qiniu AccessKey cannot be empty", groups = QiniuGroup.class)
    private String qiniuAccessKey;
    // Qiniu SECRET_KEY
    @NotBlank(message="Qiniu SecretKey cannot be empty", groups = QiniuGroup.class)
    private String qiniuSecretKey;
    // Qiniu bucket name
    @NotBlank(message="Qiniu bucket name cannot be empty", groups = QiniuGroup.class)
    private String qiniuBucketName;

    // Aliyun domain name
    @NotBlank(message="Aliyun domain name cannot be empty", groups = AliyunGroup.class)
    @URL(message = "Aliyun domain name format error", groups = AliyunGroup.class)
    private String aliyunDomain;
    // Aliyun prefix
    private String aliyunPrefix;
    // Aliyun EndPoint
    @NotBlank(message="Aliyun EndPoint cannot be empty", groups = AliyunGroup.class)
    private String aliyunEndPoint;
    // Aliyun AccessKeyId
    @NotBlank(message="Aliyun AccessKeyId cannot be empty", groups = AliyunGroup.class)
    private String aliyunAccessKeyId;
    // Aliyun AccessKeySecret
    @NotBlank(message="Aliyun AccessKeySecret cannot be empty", groups = AliyunGroup.class)
    private String aliyunAccessKeySecret;
    // Aliyun BucketName
    @NotBlank(message="Aliyun BucketName cannot be empty", groups = AliyunGroup.class)
    private String aliyunBucketName;

    // Qcloud domain name
    @NotBlank(message="Qcloud domain name cannot be empty", groups = QcloudGroup.class)
    @URL(message = "Qcloud domain name format error", groups = QcloudGroup.class)
    private String qcloudDomain;
    // Qcloud prefix
    private String qcloudPrefix;
    // Qcloud AppId
    @NotNull(message="Qcloud AppId cannot be empty", groups = QcloudGroup.class)
    private Integer qcloudAppId;
    // Qcloud SecretId
    @NotBlank(message="Qcloud SecretId cannot be empty", groups = QcloudGroup.class)
    private String qcloudSecretId;
    // Qcloud SecretKey
    @NotBlank(message="Qcloud SecretKey cannot be empty", groups = QcloudGroup.class)
    private String qcloudSecretKey;
    // Qcloud BucketName
    @NotBlank(message="Qcloud BucketName cannot be empty", groups = QcloudGroup.class)
    private String qcloudBucketName;
    // Qcloud COS region
    @NotBlank(message="Region cannot be empty", groups = QcloudGroup.class)
    private String qcloudRegion;

}