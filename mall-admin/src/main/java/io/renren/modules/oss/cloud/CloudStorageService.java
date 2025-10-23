package io.renren.modules.oss.cloud;

import io.renren.common.utils.DateUtils;
import org.apache.commons.lang.StringUtils;

import java.io.InputStream;
import java.util.Date;
import java.util.UUID;

/**
 * Abstract cloud storage service
 */
public abstract class CloudStorageService {
    
    CloudStorageConfig config;

    /**
     * Get file path
     */
    public String getPath(String prefix, String suffix) {
        // Generate uuid
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        // File path
        String path = DateUtils.format(new Date(), "yyyyMMdd") + "/" + uuid;

        if(StringUtils.isNotBlank(prefix)){
            path = prefix + "/" + path;
        }

        return path + suffix;
    }

    /**
     * Upload file by byte array
     */
    public abstract String upload(byte[] data, String path);

    /**
     * Upload file by byte array with suffix
     */
    public abstract String uploadSuffix(byte[] data, String suffix);

    /**
     * Upload file by input stream
     */
    public abstract String upload(InputStream inputStream, String path);

    /**
     * Upload file by input stream with suffix
     */
    public abstract String uploadSuffix(InputStream inputStream, String suffix);

}