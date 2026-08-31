package com.mall.admin.feign;

import com.mall.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * 后台 -> mall-thirdparty 的存储操作。
 *
 * <h3>为什么后台不自己连对象存储</h3>
 * 那需要把 endpoint / region / accessKey / secretKey 再配一份到 mall-admin。
 * 同一份凭据出现在两个服务里，轮换时就会漏掉一个 —— 而漏掉的那个不会立刻报错，
 * 会在下一次真正用到时才失败。存储凭据只放在 mall-thirdparty 一处。
 */
@FeignClient("mall-thirdparty")
public interface StorageFeignService {

    /** 按 key 批量删除对象。对不存在的 key 是幂等的。 */
    @PostMapping("/thirdparty/oss/delete")
    R deleteObjects(@RequestBody List<String> keys);

    /** 当前生效的存储配置，只含非密部分。 */
    @GetMapping("/thirdparty/oss/config")
    R storageConfig();
}
