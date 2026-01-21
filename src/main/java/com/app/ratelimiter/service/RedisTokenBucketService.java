package com.app.ratelimiter.service;

import com.app.ratelimiter.config.RateLimiterProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RedisTokenBucketService {

    private final JedisPool jedisPool;
    private final RateLimiterProperties properties;

    private static final String TOKENS_KEY_PREFIX = "rate_limiter:tokens:";
    private static final String LAST_REFILL_KEY_PREFIX = "rate_limiter:last_refill:";

    //ATOMIC LUA SCRIPT
    private static final String LUA_SCRIPT = """
        local tokens_key = KEYS[1]
        local last_refill_key = KEYS[2]

        local capacity = tonumber(ARGV[1])
        local refill_rate = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])

        local tokens = tonumber(redis.call('get', tokens_key))
        local last_refill = tonumber(redis.call('get', last_refill_key))

        if tokens == nil then
            tokens = capacity
        end

        if last_refill == nil then
            last_refill = now
        end

        local elapsed = now - last_refill
        local refill = math.floor((elapsed * refill_rate) / 1000)

        if refill > 0 then
            tokens = math.min(capacity, tokens + refill)
            redis.call('set', last_refill_key, now)
        end

        if tokens <= 0 then
            redis.call('set', tokens_key, tokens)
            return 0
        end

        tokens = tokens - 1
        redis.call('set', tokens_key, tokens)
        redis.call('set', last_refill_key, now)

        return 1
        """;

    public boolean isAllowed(String clientId) {

        String tokensKey = TOKENS_KEY_PREFIX + clientId;
        String lastRefillKey = LAST_REFILL_KEY_PREFIX + clientId;

        try (Jedis jedis = jedisPool.getResource()) {

            Object result = jedis.eval(
                    LUA_SCRIPT,
                    List.of(tokensKey, lastRefillKey),
                    List.of(
                            String.valueOf(properties.getCapacity()),
                            String.valueOf(properties.getRefillRate()),
                            String.valueOf(System.currentTimeMillis())
                    )
            );

            return Long.valueOf(1).equals(result);
        }
    }

    public long getCapacity(String clientId) {
        return properties.getCapacity();
    }

    public long getAvailableTokens(String clientId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String val = jedis.get(TOKENS_KEY_PREFIX + clientId);
            return val != null ? Long.parseLong(val) : properties.getCapacity();
        }
    }
}
