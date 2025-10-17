/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

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
 * äº‘å­˜å‚¨é…ç½®ä¿¡æ¯
 *
 * @author Mark sunlightcs@gmail.com
 */
@Data
public class CloudStorageConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    //ç±»åž‹ 1ï¼šä¸ƒç‰›  2ï¼šé˜¿é‡Œäº‘  3ï¼šè…¾è®¯äº‘
    @Range(min=1, max=3, message = "ç±»åž‹é”™è¯¯")
    private Integer type;

    //ä¸ƒç‰›ç»‘å®šçš„åŸŸå
    @NotBlank(message="ä¸ƒç‰›ç»‘å®šçš„åŸŸåä¸èƒ½ä¸ºç©º", groups = QiniuGroup.class)
    @URL(message = "ä¸ƒç‰›ç»‘å®šçš„åŸŸåæ ¼å¼ä¸æ­£ç¡®", groups = QiniuGroup.class)
    private String qiniuDomain;
    //ä¸ƒç‰›è·¯å¾„å‰ç¼€
    private String qiniuPrefix;
    //ä¸ƒç‰›ACCESS_KEY
    @NotBlank(message="ä¸ƒç‰›AccessKeyä¸èƒ½ä¸ºç©º", groups = QiniuGroup.class)
    private String qiniuAccessKey;
    //ä¸ƒç‰›SECRET_KEY
    @NotBlank(message="ä¸ƒç‰›SecretKeyä¸èƒ½ä¸ºç©º", groups = QiniuGroup.class)
    private String qiniuSecretKey;
    //ä¸ƒç‰›å­˜å‚¨ç©ºé—´å
    @NotBlank(message="ä¸ƒç‰›ç©ºé—´åä¸èƒ½ä¸ºç©º", groups = QiniuGroup.class)
    private String qiniuBucketName;

    //é˜¿é‡Œäº‘ç»‘å®šçš„åŸŸå
    @NotBlank(message="é˜¿é‡Œäº‘ç»‘å®šçš„åŸŸåä¸èƒ½ä¸ºç©º", groups = AliyunGroup.class)
    @URL(message = "é˜¿é‡Œäº‘ç»‘å®šçš„åŸŸåæ ¼å¼ä¸æ­£ç¡®", groups = AliyunGroup.class)
    private String aliyunDomain;
    //é˜¿é‡Œäº‘è·¯å¾„å‰ç¼€
    private String aliyunPrefix;
    //é˜¿é‡Œäº‘EndPoint
    @NotBlank(message="é˜¿é‡Œäº‘EndPointä¸èƒ½ä¸ºç©º", groups = AliyunGroup.class)
    private String aliyunEndPoint;
    //é˜¿é‡Œäº‘AccessKeyId
    @NotBlank(message="é˜¿é‡Œäº‘AccessKeyIdä¸èƒ½ä¸ºç©º", groups = AliyunGroup.class)
    private String aliyunAccessKeyId;
    //é˜¿é‡Œäº‘AccessKeySecret
    @NotBlank(message="é˜¿é‡Œäº‘AccessKeySecretä¸èƒ½ä¸ºç©º", groups = AliyunGroup.class)
    private String aliyunAccessKeySecret;
    //é˜¿é‡Œäº‘BucketName
    @NotBlank(message="é˜¿é‡Œäº‘BucketNameä¸èƒ½ä¸ºç©º", groups = AliyunGroup.class)
    private String aliyunBucketName;

    //è…¾è®¯äº‘ç»‘å®šçš„åŸŸå
    @NotBlank(message="è…¾è®¯äº‘ç»‘å®šçš„åŸŸåä¸èƒ½ä¸ºç©º", groups = QcloudGroup.class)
    @URL(message = "è…¾è®¯äº‘ç»‘å®šçš„åŸŸåæ ¼å¼ä¸æ­£ç¡®", groups = QcloudGroup.class)
    private String qcloudDomain;
    //è…¾è®¯äº‘è·¯å¾„å‰ç¼€
    private String qcloudPrefix;
    //è…¾è®¯äº‘AppId
    @NotNull(message="è…¾è®¯äº‘AppIdä¸èƒ½ä¸ºç©º", groups = QcloudGroup.class)
    private Integer qcloudAppId;
    //è…¾è®¯äº‘SecretId
    @NotBlank(message="è…¾è®¯äº‘SecretIdä¸èƒ½ä¸ºç©º", groups = QcloudGroup.class)
    private String qcloudSecretId;
    //è…¾è®¯äº‘SecretKey
    @NotBlank(message="è…¾è®¯äº‘SecretKeyä¸èƒ½ä¸ºç©º", groups = QcloudGroup.class)
    private String qcloudSecretKey;
    //è…¾è®¯äº‘BucketName
    @NotBlank(message="è…¾è®¯äº‘BucketNameä¸èƒ½ä¸ºç©º", groups = QcloudGroup.class)
    private String qcloudBucketName;
    //è…¾è®¯äº‘COSæ‰€å±žåœ°åŒº
    @NotBlank(message="æ‰€å±žåœ°åŒºä¸èƒ½ä¸ºç©º", groups = QcloudGroup.class)
    private String qcloudRegion;


}
