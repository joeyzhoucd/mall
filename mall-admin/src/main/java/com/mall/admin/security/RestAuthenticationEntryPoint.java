package com.mall.admin.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未认证时的响应。
 *
 * <h3>这个类存在的唯一目的，就是不要返回真正的 HTTP 401</h3>
 * 前端（mall-frontend 的后台 UI，不重写）的响应拦截器是这样的：
 * <pre>
 * http.interceptors.response.use(
 *   response =&gt; { if (response.data.code === 401) { 清登录态; 跳登录页 } return response },
 *   error    =&gt; { return Promise.reject(error) }        // ← 这里什么都不做
 * )
 * </pre>
 * axios 把 4xx/5xx 交给第二个回调，而那个回调既不清登录态也不跳登录页。
 * 所以一旦返回真正的 401，前端的表现是【页面永远停在加载中，点什么都没反应】，
 * 控制台里只有一个未处理的 Promise 拒绝 —— 从现象完全看不出是登录过期。
 * <p>
 * 必须返回 <b>HTTP 200，body 里写 code: 401</b>，前端才会走进第一个回调、
 * 识别出登录失效并跳转。
 * <p>
 * 这条约定从 HTTP 语义上讲是"错"的（用 200 表达未授权），但契约的定义方是那个不重写的前端。
 * 这也是整份契约里最容易被"顺手改成规范做法"而搞坏的一条，所以单独一个类、
 * 把原因写在这里，而不是散在过滤器里。见 openapi/admin-api.yaml 第 2 条。
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    /** 手写而不是序列化一个对象：内容固定，省一次序列化，也让"回的到底是什么"一目了然。 */
    private static final String BODY = "{\"code\":401,\"msg\":\"invalid token\"}";

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(BODY);
    }
}
