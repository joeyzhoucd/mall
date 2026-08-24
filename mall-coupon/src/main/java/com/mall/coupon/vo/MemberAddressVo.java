package com.mall.coupon.vo;

import lombok.Data;

/**
 * 会员收货地址视图，对应 mall-member 的 /member/memberreceiveaddress 接口返回的地址字段。
 */
@Data
public class MemberAddressVo {
    private Long id;
    private Long memberId;
    private String name;
    private String phone;
    private String postCode;
    private String province;
    private String city;
    private String region;
    private String detailAddress;
    private String areacode;
    private Integer defaultStatus;
}
