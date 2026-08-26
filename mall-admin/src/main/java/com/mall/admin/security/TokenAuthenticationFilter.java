package com.mall.admin.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 从【裸 token 请求头】里取令牌并建立认证上下文。
 * <p>
 * 为什么不是 Authorization: Bearer：前端（mall-frontend 的后台 UI，不重写）在
 * src/utils/httpRequest.js 里写死了 {@code config.headers['token'] = cookie.get('token')}。
 * 这是契约的一部分，见 openapi/admin-api.yaml 第 1 条。
 * <p>
 * 也正因为如此，没有使用 spring-boot-starter-security-oauth2-resource-server：
 * 它的自动配置默认从 Authorization 头按 Bearer 格式取值，套上之后还得绕开它的默认行为，
 * 不如直接写这十几行来得直白。
 * <p>
 * 注意这里对"没带令牌"和"令牌无效"的处理是【一样的】：都不建立认证上下文、直接放行给
 * 后面的过滤器链，由 {@link RestAuthenticationEntryPoint} 统一回 code:401。
 * 不在这里直接写响应，是为了让"未认证要怎么回"只有一个地方决定——
 * 那条约定（HTTP 200 + body code:401）太容易写错，不能散落在多处。
 */
@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String TOKEN_HEADER = "token";

    private final JwtService jwtService;

    public TokenAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        JwtService.LoginUser user = jwtService.parse(request.getHeader(TOKEN_HEADER));
        if (user != null) {
            // 这里没有装载具体权限（authorities 为空）。权限控制目前在业务层按
            // sys_role_menu 的 perms 判断，没有映射成 Spring Security 的 authority。
            // 如果以后要用 @PreAuthorize，需要在这里把 perms 查出来放进 authorities。
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, AuthorityUtils.NO_AUTHORITIES);
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            // 必须清理：Tomcat 的线程是复用的，不清会让下一个请求继承上一个请求的身份。
            SecurityContextHolder.clearContext();
        }
    }
}
