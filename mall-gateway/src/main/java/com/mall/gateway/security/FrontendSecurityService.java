package com.mall.gateway.security;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public interface FrontendSecurityService {

    Mono<FrontendAccessDecision> check(ServerWebExchange exchange);
}
