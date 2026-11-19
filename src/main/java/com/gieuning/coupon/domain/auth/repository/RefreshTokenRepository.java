package com.gieuning.coupon.domain.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh:family:";

    private final StringRedisTemplate redisTemplate;

    public void save(String familyId, String refreshToken, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        redisTemplate.opsForValue().set(key(familyId), hash(refreshToken), ttl);
    }

    public boolean matches(String familyId, String refreshToken) {
        String storedHash = redisTemplate.opsForValue().get(key(familyId));
        return hash(refreshToken).equals(storedHash);
    }

    public boolean delete(String familyId) {
        return Boolean.TRUE.equals(redisTemplate.delete(key(familyId)));
    }

    private String key(String familyId) {
        return KEY_PREFIX + familyId;
    }

    private String hash(String refreshToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                                         .digest(refreshToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
