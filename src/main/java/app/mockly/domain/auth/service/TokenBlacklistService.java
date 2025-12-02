package app.mockly.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {
    private static final String BLACKLIST_PREFIX = "blacklist:token:";

    private final RedisTemplate<String, String> redisTemplate;

    public void save(String token, long ttl) {
        redisTemplate.opsForValue()
                .set(BLACKLIST_PREFIX + token, "blacklisted", ttl, TimeUnit.MILLISECONDS);
    }

    public boolean isBlacklisted(String token) {
        return redisTemplate.hasKey(BLACKLIST_PREFIX + token);
    }
}
