package com.mall.common.config;

import com.mall.common.constant.TraceConstants;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignTraceConfig {

    @Bean
    public RequestInterceptor traceRequestInterceptor() {
        return template -> {
            String traceId = MDC.get(TraceConstants.MDC_TRACE_ID);
            if (traceId != null && !traceId.trim().isEmpty()) {
                template.header(TraceConstants.TRACE_ID_HEADER, traceId);
            }
        };
    }
}

