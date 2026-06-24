package com.promoguard.lock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RedisLockService implements ClaimLockService {

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public RedisLockService(StringRedisTemplate redisTemplate, @Value("${promoguard.redis-lock.ttl}") Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    @Override
    public Optional<LockToken> tryLock(String key) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        if (Boolean.TRUE.equals(acquired)) {
            return Optional.of(new LockToken(key, token));
        }
        return Optional.empty();
    }

    @Override
    public void unlock(LockToken lockToken) {
        redisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings({"unchecked", "NullableProblems"})
            public List<Object> execute(RedisOperations operations) {
                operations.watch(lockToken.key());
                Object currentToken = operations.opsForValue().get(lockToken.key());
                if (!lockToken.token().equals(currentToken)) {
                    operations.unwatch();
                    return List.of();
                }
                operations.multi();
                operations.delete(lockToken.key());
                return operations.exec();
            }
        });
    }
}
