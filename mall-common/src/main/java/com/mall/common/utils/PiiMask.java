package com.mall.common.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * 个人信息脱敏。
 *
 * <h3>用在哪</h3>
 * 后台的<b>列表</b>接口。详情接口给全量。
 * 理由是列表一次会把上百个人的联系方式铺在屏幕上（会员列表默认 10 条/页，
 * 但一次导出/翻页就是全量），而绝大多数时候看的人只是在找某一个人 ——
 * 让另外 99 个人的手机号顺带出现在屏幕、截图和浏览器缓存里没有必要。
 * 要看某个人的完整信息，就去点开那个人，这个动作本身也是一个可审计的点。
 *
 * <h3>这不是一个合规控制，别把它当成一个</h3>
 * 真实的合规要求是「谁在什么时候看了谁的信息」有记录。
 * 这里没有访问日志，脱敏只降低<b>顺带暴露</b>的量，
 * 挡不住一个已经登录的管理员逐个点开去收集。
 * 补上访问审计之前，不要因为"已经脱敏了"就认为这块做完了。
 *
 * <h3>搜索不受影响</h3>
 * 脱敏发生在<b>查询之后</b>、序列化之前。按手机号搜人打的还是真实列，
 * 所以「输入完整手机号能搜到，但列表里显示成星号」是预期行为，不是 bug。
 */
public final class PiiMask {

    private PiiMask() {
    }

    /**
     * 手机号：保留前 3 后 4，中间打星。
     *
     * <p>长度不足 7 位时<b>整个打星</b>而不是原样返回 ——
     * 一个"太短所以不脱敏"的分支，遇到脏数据（比如只存了后 4 位）时
     * 会把它完整地露出来，而那正是最不该露的那种残缺数据。
     */
    public static String mobile(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() < 7) {
            return StringUtils.repeat('*', trimmed.length());
        }
        return trimmed.substring(0, 3)
                + StringUtils.repeat('*', trimmed.length() - 7)
                + trimmed.substring(trimmed.length() - 4);
    }

    /**
     * 邮箱：本地部分保留首字符，域名原样。
     *
     * <p>域名不脱敏是有意的：它基本没有识别个人的价值，
     * 但对运维有用（一眼看出是不是同一批测试账号、是不是某个企业域）。
     */
    public static String email(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        String trimmed = value.trim();
        int at = trimmed.indexOf('@');
        if (at <= 0) {
            // 没有 @ 说明不是个正常邮箱，按最保守的方式处理：全打星。
            return StringUtils.repeat('*', trimmed.length());
        }
        String local = trimmed.substring(0, at);
        String domain = trimmed.substring(at);
        if (local.length() == 1) {
            return "*" + domain;
        }
        return local.charAt(0) + StringUtils.repeat('*', local.length() - 1) + domain;
    }
}
