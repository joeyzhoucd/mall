package com.mall.admin.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * sys_oss —— 后台上传文件的登记表。
 *
 * <h3>为什么需要这张表，而不是直接列 bucket</h3>
 * 后台的文件列表页按<b>页码</b>翻页，而 S3 的 {@code ListObjectsV2} 只给
 * 「下一页的游标」，给不了「第 7 页」—— 它根本不知道总数（桶里可能有上亿个对象，
 * 服务端不会为一次列表请求去数一遍）。按页码翻 S3 只有两条路：
 * 每次把所有 key 拉下来在内存里分页（规模一上去就废），或者自己维护索引。
 * 真实系统都选后者，顺带还能有上传人、大小、类型、搜索和排序。
 *
 * <h3>谁往里写</h3>
 * 浏览器是<b>直传</b>对象存储的（预签名 PUT，字节不经过后端），
 * 所以后端不知道某次上传成没成功。前端 PUT 成功后回调
 * {@code POST /sys/oss/confirm} 登记。
 * 代价是<b>漏调回调的文件会存在于桶里但不出现在列表里</b> —— 这是有意接受的：
 * 另一条路是让文件穿过后端（每次上传占一个请求线程和一份内存缓冲），
 * 或者接 S3 的事件通知（要额外的消息通道）。后者是正解，现在没做。
 */
@Data
@TableName("sys_oss")
public class SysOssEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId
    private Long id;

    /** 对外可访问地址，可能带 CDN 前缀，不一定等于上传端点。 */
    private String url;

    /** 对象存储里的 key。删除时用它 —— 不能从 url 反推，CDN 前缀会让反推出错。 */
    private String objectKey;

    private Long fileSize;

    private String contentType;

    private String createBy;

    /** 前端列表的列名就是 createDate，别改。 */
    private Date createDate;
}
