package com.mall.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "mall.frontend.security")
public class FrontendSecurityProperties {

    private boolean enabled = true;

    private String loginPageUrl = "http://auth.mall.com/login.html";

    private Session session = new Session();

    private Access access = new Access();

    private RateLimit rateLimit = new RateLimit();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getLoginPageUrl() {
        return loginPageUrl;
    }

    public void setLoginPageUrl(String loginPageUrl) {
        this.loginPageUrl = loginPageUrl;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Access getAccess() {
        return access;
    }

    public void setAccess(Access access) {
        this.access = access;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public static class Session {
        private String cookieName = "MALLSESSION";
        private String redisKeyPrefix = "spring:session:sessions:";
        private String loginAttribute = "sessionAttr:loginUser";

        public String getCookieName() {
            return cookieName;
        }

        public void setCookieName(String cookieName) {
            this.cookieName = cookieName;
        }

        public String getRedisKeyPrefix() {
            return redisKeyPrefix;
        }

        public void setRedisKeyPrefix(String redisKeyPrefix) {
            this.redisKeyPrefix = redisKeyPrefix;
        }

        public String getLoginAttribute() {
            return loginAttribute;
        }

        public void setLoginAttribute(String loginAttribute) {
            this.loginAttribute = loginAttribute;
        }
    }

    public static class Access {
        private List<String> protectedPathPatterns = new ArrayList<>(List.of(
                "/order/**",
                "/pay/mock/success",
                "/pay/mock/fail",
                "/pay/mock/close",
                "/coupon/seckill/grab/**",
                "/coupon/seckill/message/*",
                "/coupon/seckill/message/*/address",
                "/coupon/seckill/address/**"
        ));
        private List<String> bypassPathPatterns = new ArrayList<>(List.of(
                "/pay/mock/notify",
                "/coupon/seckill/activate/**",
                "/coupon/seckill/message/*/order-created"
        ));
        private List<String> allowedIps = new ArrayList<>();
        private List<String> deniedIps = new ArrayList<>();
        private List<String> allowedDeviceIds = new ArrayList<>();
        private List<String> deniedDeviceIds = new ArrayList<>();
        private List<Long> allowedUserIds = new ArrayList<>();
        private List<Long> deniedUserIds = new ArrayList<>();

        /**
         * 把 {@code /actuator/**} 挡在网关之外。
         *
         * <h3>为什么需要它</h3>
         * 2026-09-16 实测：{@code http://mall.com/actuator/health} 和
         * {@code /actuator/prometheus} 从<b>公网无鉴权</b>就能打到，返回 200，
         * health 里还带着 Consul leader 的内网 IP 和 config server 的文件路径。
         * <p>
         * 原因是 actuator 没有专属路由，会一路掉到按 Host 匹配的那些路由上
         * （{@code mall.com} 命中兜底的 {@code Host=**.mall.com}，
         * {@code seckill.mall.com} 命中 mall-coupon），于是<b>每个域名下都可达</b>。
         * 而 {@link DefaultFrontendSecurityService#shouldSkip} 又刻意跳过
         * {@code /actuator/} —— 那是为了别让监控被限流，副作用是它也不设防。
         *
         * <h3>为什么不用别的办法</h3>
         * <ul>
         *   <li><b>ingress 上拦</b>：要开 {@code allow-snippet-annotations}，
         *       而它在 ingress-nginx 1.11 里是<b>为 CVE 刻意关掉</b>的默认值 ——
         *       为一件小事开一个更大的口子。</li>
         *   <li><b>挪到独立管理端口</b>（Spring 的标准答案）：
         *       Consul 的健康检查走注册端口，actuator 一挪<b>全部实例变 critical</b>，
         *       Feign 会整体失效。mall-common-default.properties 里有前人留的同一条警告。</li>
         * </ul>
         */
        private boolean blockManagementEndpoints = true;

        public boolean isBlockManagementEndpoints() {
            return blockManagementEndpoints;
        }

        public void setBlockManagementEndpoints(boolean blockManagementEndpoints) {
            this.blockManagementEndpoints = blockManagementEndpoints;
        }

        public List<String> getProtectedPathPatterns() {
            return protectedPathPatterns;
        }

        public void setProtectedPathPatterns(List<String> protectedPathPatterns) {
            this.protectedPathPatterns = protectedPathPatterns;
        }

        public List<String> getBypassPathPatterns() {
            return bypassPathPatterns;
        }

        public void setBypassPathPatterns(List<String> bypassPathPatterns) {
            this.bypassPathPatterns = bypassPathPatterns;
        }

        public List<String> getAllowedIps() {
            return allowedIps;
        }

        public void setAllowedIps(List<String> allowedIps) {
            this.allowedIps = allowedIps;
        }

        public List<String> getDeniedIps() {
            return deniedIps;
        }

        public void setDeniedIps(List<String> deniedIps) {
            this.deniedIps = deniedIps;
        }

        public List<String> getAllowedDeviceIds() {
            return allowedDeviceIds;
        }

        public void setAllowedDeviceIds(List<String> allowedDeviceIds) {
            this.allowedDeviceIds = allowedDeviceIds;
        }

        public List<String> getDeniedDeviceIds() {
            return deniedDeviceIds;
        }

        public void setDeniedDeviceIds(List<String> deniedDeviceIds) {
            this.deniedDeviceIds = deniedDeviceIds;
        }

        public List<Long> getAllowedUserIds() {
            return allowedUserIds;
        }

        public void setAllowedUserIds(List<Long> allowedUserIds) {
            this.allowedUserIds = allowedUserIds;
        }

        public List<Long> getDeniedUserIds() {
            return deniedUserIds;
        }

        public void setDeniedUserIds(List<Long> deniedUserIds) {
            this.deniedUserIds = deniedUserIds;
        }
    }

    public static class RateLimit {
        private boolean enabled = true;
        private String redisKeyPrefix = "mall:security:rate";
        private Limit ip = new Limit(true, 600, Duration.ofMinutes(1));
        private Limit device = new Limit(true, 300, Duration.ofMinutes(1));
        private Limit user = new Limit(true, 120, Duration.ofMinutes(1));

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getRedisKeyPrefix() {
            return redisKeyPrefix;
        }

        public void setRedisKeyPrefix(String redisKeyPrefix) {
            this.redisKeyPrefix = redisKeyPrefix;
        }

        public Limit getIp() {
            return ip;
        }

        public void setIp(Limit ip) {
            this.ip = ip;
        }

        public Limit getDevice() {
            return device;
        }

        public void setDevice(Limit device) {
            this.device = device;
        }

        public Limit getUser() {
            return user;
        }

        public void setUser(Limit user) {
            this.user = user;
        }
    }

    public static class Limit {
        private boolean enabled;
        private int capacity;
        private Duration window;

        public Limit() {
        }

        public Limit(boolean enabled, int capacity, Duration window) {
            this.enabled = enabled;
            this.capacity = capacity;
            this.window = window;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }
}
