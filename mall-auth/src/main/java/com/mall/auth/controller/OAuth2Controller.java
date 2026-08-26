package com.mall.auth.controller;

import tools.jackson.databind.ObjectMapper;
import com.mall.auth.feign.MemberFeignService;
import com.mall.auth.vo.SocialUser;
import com.mall.common.utils.R;
import com.mall.session.vo.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
public class OAuth2Controller {

    @Autowired
    private MemberFeignService memberFeignService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${mall.auth.weibo.appKey}")
    private String appKey;

    @Value("${mall.auth.weibo.appSecret}")
    private String appSecret;

    @GetMapping("/oauth2.0/weibo/success")
    public String weibo(@RequestParam("code") String code, HttpSession session) throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("client_id", appKey);
        map.put("client_secret", appSecret);
        map.put("grant_type", "authorization_code");
        map.put("redirect_uri", "http://auth.mall.com/oauth2.0/weibo/success");
        map.put("code", code);

        // Exchange code for access token
        org.apache.http.impl.client.CloseableHttpClient httpClient = org.apache.http.impl.client.HttpClients
                .createDefault();
        org.apache.http.client.methods.HttpPost post = new org.apache.http.client.methods.HttpPost(
                "https://api.weibo.com/oauth2/access_token");

        List<org.apache.http.NameValuePair> params = new ArrayList<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            params.add(new org.apache.http.message.BasicNameValuePair(entry.getKey(), entry.getValue()));
        }
        post.setEntity(new org.apache.http.client.entity.UrlEncodedFormEntity(params, "UTF-8"));

        HttpResponse response = httpClient.execute(post);

        if (response.getStatusLine().getStatusCode() == 200) {
            // Success
            String json = EntityUtils.toString(response.getEntity());
            SocialUser socialUser = objectMapper.readValue(json, SocialUser.class);

            // Login or Register
            R r = memberFeignService.oauthLogin(socialUser);
            if (r.getCode() == 0) {
                // Login successful
                Object data = r.get("data");

                LoginUser loginUser = objectMapper.convertValue(r.get("member"), LoginUser.class);

                log.info("Login successful: User info: {}", loginUser);
                session.setAttribute("loginUser", loginUser);

                // Redirect to homepage
                return "redirect:http://mall.com";
            } else {
                return "redirect:http://auth.mall.com/login.html";
            }
        } else {
            return "redirect:http://auth.mall.com/login.html";
        }
    }
}
