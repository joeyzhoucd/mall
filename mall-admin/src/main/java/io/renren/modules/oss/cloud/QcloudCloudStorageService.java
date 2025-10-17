/**
 * Copyright (c) 2016-2019 äººäººå¼€æº All rights reserved.
 *
 * https://www.renren.io
 *
 * ç‰ˆæƒæ‰€æœ‰ï¼Œä¾µæƒå¿…ç©¶ï¼
 */

package io.renren.modules.oss.cloud;


import com.alibaba.fastjson.JSONObject;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.request.UploadFileRequest;
import com.qcloud.cos.sign.Credentials;
import io.renren.common.exception.RRException;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;

/**
 * è…¾è®¯äº‘å­˜å‚¨
 *
 * @author Mark sunlightcs@gmail.com
 */
public class QcloudCloudStorageService extends CloudStorageService {
    private COSClient client;

    public QcloudCloudStorageService(CloudStorageConfig config){
        this.config = config;

        //åˆå§‹åŒ–
        init();
    }

    private void init(){
    	Credentials credentials = new Credentials(config.getQcloudAppId(), config.getQcloudSecretId(),
                config.getQcloudSecretKey());
    	
    	//åˆå§‹åŒ–å®¢æˆ·ç«¯é…ç½®
        ClientConfig clientConfig = new ClientConfig();
        //è®¾ç½®bucketæ‰€åœ¨çš„åŒºåŸŸï¼ŒåŽå—ï¼šgz åŽåŒ—ï¼štj åŽä¸œï¼šsh
        clientConfig.setRegion(config.getQcloudRegion());
        
    	client = new COSClient(clientConfig, credentials);
    }

    @Override
    public String upload(byte[] data, String path) {
        //è…¾è®¯äº‘å¿…éœ€è¦ä»¥"/"å¼€å¤´
        if(!path.startsWith("/")) {
            path = "/" + path;
        }
        
        //ä¸Šä¼ åˆ°è…¾è®¯äº‘
        UploadFileRequest request = new UploadFileRequest(config.getQcloudBucketName(), path, data);
        String response = client.uploadFile(request);

        JSONObject jsonObject = JSONObject.parseObject(response);
        if(jsonObject.getInteger("code") != 0) {
            throw new RRException("æ–‡ä»¶ä¸Šä¼ å¤±è´¥ï¼Œ" + jsonObject.getString("message"));
        }

        return config.getQcloudDomain() + path;
    }

    @Override
    public String upload(InputStream inputStream, String path) {
    	try {
            byte[] data = IOUtils.toByteArray(inputStream);
            return this.upload(data, path);
        } catch (IOException e) {
            throw new RRException("ä¸Šä¼ æ–‡ä»¶å¤±è´¥", e);
        }
    }

    @Override
    public String uploadSuffix(byte[] data, String suffix) {
        return upload(data, getPath(config.getQcloudPrefix(), suffix));
    }

    @Override
    public String uploadSuffix(InputStream inputStream, String suffix) {
        return upload(inputStream, getPath(config.getQcloudPrefix(), suffix));
    }
}
