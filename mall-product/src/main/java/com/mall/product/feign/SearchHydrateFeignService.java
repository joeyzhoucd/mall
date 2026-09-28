package com.mall.product.feign;

import com.mall.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 按 SPU 补全展示信息（每个 SPU 一个有货 SKU，按入参顺序）。给「搭配购买」用。
 *
 * <p>没放进 {@link SearchFeignService} 是为了超时：那个接口上还有 productUp（上架批量写 ES，慢），
 * 共用 mall-search 的 read-timeout=5000。详情页这条要短，contextId = searchHydrate 单独配。
 */
@FeignClient(name = "mall-search", contextId = "searchHydrate")
public interface SearchHydrateFeignService {

    @GetMapping("/search/spus")
    R bySpus(@RequestParam("ids") List<Long> ids);
}
