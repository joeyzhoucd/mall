package com.mall.admin.config;

import com.mall.admin.security.RestAuthenticationEntryPoint;
import com.mall.admin.security.TokenAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置。
 * <p>
 * 用 &#64;Configuration 而不是 &#64;Component：这个类的职责是"提供若干个 &#64;Bean 定义"，
 * &#64;Configuration 会被 CGLIB 代理以保证 bean 方法之间互相调用时仍走容器（单例语义），
 * &#64;Component 不会。装配类一律用 &#64;Configuration。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           TokenAuthenticationFilter tokenFilter,
                                           RestAuthenticationEntryPoint entryPoint) throws Exception {
        http
                // 关掉 CSRF：这是一个纯 JSON 接口服务，凭证是请求头里的 JWT 而不是 Cookie 会话。
                // CSRF 攻击的前提是浏览器会自动带上凭证，而自定义请求头不会被自动带上，
                // 所以这里关掉是安全的、也是必要的（不关的话所有 POST 都会被拒）。
                .csrf(csrf -> csrf.disable())

                // 完全无状态：不创建也不使用 HttpSession。
                // 每个请求靠 token 头自证身份，这样多副本之间不需要共享会话。
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 不需要 Spring Security 自带的表单登录页和 basic 认证弹窗——
                // 登录走我们自己的 /sys/login（要校验验证码，签发 JWT）。
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())

                .authorizeHttpRequests(auth -> auth
                        // 登录和验证码必须匿名可访问，否则永远登不进来。
                        .requestMatchers("/sys/login", "/captcha.jpg").permitAll()
                        // 登出也放开：令牌已失效时前端仍会调一次登出，这时要求认证会拿到 401，
                        // 反而让前端多走一次无意义的跳转分支。
                        .requestMatchers("/sys/logout").permitAll()
                        // actuator 给 K8s 探针和 Consul 健康检查用，必须匿名。
                        // 这些端点只在 pod 端口上暴露、网关只转发 /api/**，集群外访问不到。
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())

                // 未认证时【返回 HTTP 200 + body code:401】，不是真正的 401。
                // 原因见 RestAuthenticationEntryPoint 的类注释——这条约定写错会让前端页面永久卡住。
                .exceptionHandling(ex -> ex.authenticationEntryPoint(entryPoint))

                // 自定义的 token 头过滤器放在用户名密码过滤器之前。
                .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class)

                .cors(Customizer.withDefaults());
        return http.build();
    }

    /**
     * 密码编码器。
     * <p>
     * 用 BCrypt 而不是沿用旧实现的 Shiro 风格 sha256(salt + password)：后者是快哈希、
     * 没有工作因子，离线爆破成本极低。已验证种子数据里的 admin 就是那个方案
     * （salt=YzcmCZNvbXocrsz9dm8e，密码 admin123），换方案时密码本身保持不变、
     * 只把种子里的哈希换成 BCrypt，用户感知不到。
     * <p>
     * 也和 mall-member 已经在用的 BCryptPasswordEncoder 保持一致，全项目一种口令哈希。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
