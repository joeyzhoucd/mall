package com.mall.order.feign;

import com.mall.common.utils.R;
import com.mall.order.vo.MemberAddressVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient("mall-member")
public interface MemberFeignService {

    @GetMapping("/member/memberreceiveaddress/{memberId}/list")
    R getAddress(@PathVariable("memberId") Long memberId);

    @PostMapping("/member/memberreceiveaddress/save")
    R saveAddress(@RequestBody MemberAddressVo addressVo);
}

