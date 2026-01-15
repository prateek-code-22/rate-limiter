package com.app.ratelimiter.service;
import com.app.ratelimiter.config.RateLimiterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

//store token bucket state in redis
//manage tokens per client
//handles token refill based on time
//provide rate limiting logic

@Service
@RequiredArgsConstructor //creates a constructor for final fields
public class RedisTokenBucketService {

    private final JedisPool jedisPool;
    private final RateLimiterProperties properties;

    //prefix to avoid collision
    private final String TOKENS_KEY_PREFIX = "rate-limiter:tokens:";
    private static final String LAST_REFILL_KEY_PREFIX = "rate-limiter:last_refill:";

    //Pattern
    //rate_limiter:{type}:{clientId}
    //ex: rate_limiter:tokens:192.164.2.35 - "7" (current token count left)
    //    rate_limiter:las_refill:192.164.35.66 - "12345678" (last refill timestamp)


    public boolean isAllowed(String clientId){
        String tokenKey = TOKENS_KEY_PREFIX + clientId;

        // “Borrow Redis connection → use → return to pool”
        try(Jedis jedis = jedisPool.getResource()){
            refillTokens(clientId, jedis);

            //no of token left
            String tokenStr = jedis.get(tokenKey);

            // Redis already has token count → use it ,If first request → give full bucket
//            long currentTokens = tokenStr != null ? Long.parseLong(tokenStr): properties.getCapacity();
//
//            // token count less than required
//            if(currentTokens <= 0) return false;
//
//            //decrement by 1 assuming request require 1 token
//            long decremented = jedis.decr(tokenKey);
//
//            //should not be negative
//            return decremented >= 0;


            long currentTokens = tokenStr != null ? Long.parseLong(tokenStr) : properties.getCapacity();

            if(currentTokens <= 0) {
                return false;
            }

            long decremented = jedis.decr(tokenKey);
            return decremented >= 0;
        }
    }

    public long getCapacity(String clientId){
        return properties.getCapacity();
    }

    public long getAvailableTokens(String clientId){
        String tokenKey = TOKENS_KEY_PREFIX + clientId;

        try(Jedis jedis = jedisPool.getResource()){
            refillTokens(clientId, jedis);
            String tokenStr = jedis.get(tokenKey);
            return tokenStr != null ? Long.parseLong(tokenStr) : properties.getCapacity();

        }
    }

    public void refillTokens(String clientId, Jedis jedis) {

        String tokenKey = TOKENS_KEY_PREFIX + clientId;
        String lastRefillKey = LAST_REFILL_KEY_PREFIX + clientId;

        long now = System.currentTimeMillis();
        String lastRefillStr = jedis.get(lastRefillKey);

        //if bucket is empty
        if(lastRefillStr == null){
            jedis.set(tokenKey, String.valueOf(properties.getCapacity()));
            jedis.set(lastRefillKey, String.valueOf(now));
            return;
        }

        long lastRefillTime = Long.parseLong(lastRefillStr);
        long elapsedTime = now - lastRefillTime;

        if(elapsedTime <= 0) return;

        //elapsed time in msec, refillrate - token/sec
        long tokenToAdd = (elapsedTime * properties.getRefillRate()) / 1000;
        if(tokenToAdd <= 0) return;

        String tokenStr = jedis.get(tokenKey);
        long currentTokens = tokenStr != null ? Long.parseLong(tokenStr): properties.getCapacity();
        long newTokens = Math.min(properties.getCapacity(), currentTokens + tokenToAdd );

        jedis.set(tokenKey, String.valueOf(newTokens));
        jedis.set(lastRefillKey, String.valueOf(now));

    }

}

