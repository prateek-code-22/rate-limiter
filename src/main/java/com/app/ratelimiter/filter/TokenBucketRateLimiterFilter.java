package com.app.ratelimiter.filter;

import com.app.ratelimiter.service.RateLimiterService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;


@Component
public class TokenBucketRateLimiterFilter extends AbstractGatewayFilterFactory<TokenBucketRateLimiterFilter.Config>{

    private final RateLimiterService rateLimiterService;
    public TokenBucketRateLimiterFilter(RateLimiterService rateLimiterService){
        super(Config.class);
        this.rateLimiterService= rateLimiterService;
    }

    @Override
    public TokenBucketRateLimiterFilter.Config newConfig(){
        return new Config();
    }

    @Override
    public GatewayFilter apply(Config config){
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            String clientId = getClientId(request);

            if(!rateLimiterService.isAllowed(clientId)){
                response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                addRateLimiterHeaders(response, clientId);
                String errorBody = String.format(
                        "{\"error\":\"Rate limited exceeded\",\"clientId\":\"%s\"}",
                        clientId
                );

                return response.writeWith(
                        Mono.just(response.bufferFactory().wrap(errorBody.getBytes(StandardCharsets.UTF_8)))
                );
            }
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                addRateLimiterHeaders(response, clientId);
            }));
        };
    }

    private void addRateLimiterHeaders(ServerHttpResponse response, String clientId) {
        response.getHeaders().add("X-RateLimit-Limit", String.valueOf(rateLimiterService.getCapacity(clientId)));
        response.getHeaders().add("X-RateLimit-Remaining", String.valueOf(rateLimiterService.getAvailableTokens(clientId)));
    }

    public static class Config{ }

    public String getClientId(ServerHttpRequest request){
        //X-forwarded-for : 192.167.255.5, 10.0.0.1 uses -> 192.167.255.5
        String xForwardFor = request.getHeaders().getFirst("X-Forwarded-For");
        if(xForwardFor != null && !xForwardFor.isEmpty()){
            return xForwardFor.split(",")[0].trim();
        }

        //fallback to direct connection IP
        var remoteAddress = request.getRemoteAddress();
        if(remoteAddress!=null && remoteAddress.getHostName() != null){
            return remoteAddress.getAddress().getHostAddress();
        }

        //default fallback
        return "unknown";
    }

}
