package com.mall.admin.controller;

import com.mall.admin.entity.SysOssEntity;
import com.mall.admin.security.JwtService;
import com.mall.admin.service.SysOssService;
import com.mall.common.utils.PageUtils;
import com.mall.common.utils.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.List;

/**
 * 后台文件管理。
 *
 * <h3>和旧 renren 后台的两处不同，都是有意的</h3>
 * <b>1) 没有 {@code /sys/oss/upload}。</b> 旧实现是浏览器把文件 POST 给后端、
 * 后端再转存到对象存储 —— 每个上传占一个请求线程和一份内存缓冲。
 * 本项目统一走<b>预签名直传</b>：前端先向 {@code /thirdparty/oss/presign} 要地址，
 * 直接 PUT 到对象存储，成功后回调这里的 {@code /confirm} 登记。
 * 一套上传路径，不是两套。
 *
 * <b>2) 没有 {@code /sys/oss/saveConfig}。</b> 旧实现有个能填七牛/阿里云/腾讯云
 * AccessKey+SecretKey 的表单，存进数据库、随时可改。那等于任何拿到后台账号的人
 * 都能读走或替换掉存储凭据；而且密钥进了数据库就会进备份、进从库、进 binlog。
 * 本项目的密钥走 Sealed Secret 注入环境变量 —— 改配置是一次部署，不是一次点击。
 * {@code /config} 保留但改成<b>只读</b>，只回「现在连的是哪儿」，不回任何凭据。
 */
@RestController
@RequestMapping("/sys/oss")
public class SysOssController {

    private final SysOssService sysOssService;

    public SysOssController(SysOssService sysOssService) {
        this.sysOssService = sysOssService;
    }

    @GetMapping("/list")
    public R list(@RequestParam(value = "page", defaultValue = "1") int page,
                  @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return R.ok().put("page", new PageUtils(sysOssService.page(page, limit)));
    }

    /**
     * 前端把文件 PUT 到对象存储成功之后调这个，把它登记进列表。
     *
     * <p>{@code objectKey} 必填 —— 删除时要用它，而且<b>不能从 url 反推</b>：
     * url 可能带 CDN 前缀，反推出来的 key 会指向不存在的对象，删除就成了静默的空操作。
     */
    @PostMapping("/confirm")
    public R confirm(@RequestBody SysOssEntity oss) {
        if (oss == null || oss.getObjectKey() == null || oss.getObjectKey().isBlank()) {
            return R.error("objectKey 不能为空");
        }
        if (oss.getUrl() == null || oss.getUrl().isBlank()) {
            return R.error("url 不能为空");
        }
        // id 和 createDate 由服务端定，不接受客户端传入 —— 否则可以覆盖别人的记录
        // 或者伪造上传时间。
        oss.setId(null);
        oss.setCreateDate(new Date());
        JwtService.LoginUser user = CurrentUser.get();
        oss.setCreateBy(user != null ? user.username() : null);
        return R.ok().put("oss", sysOssService.confirm(oss));
    }

    @PostMapping("/delete")
    public R delete(@RequestBody List<Long> ids) {
        try {
            return R.ok().put("deleted", sysOssService.delete(ids));
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    /** 只读的存储配置，不含任何凭据。见类注释里为什么不做 saveConfig。 */
    @GetMapping("/config")
    public R config() {
        return R.ok().put("config", sysOssService.storageConfig());
    }
}
