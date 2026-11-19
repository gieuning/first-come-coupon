package com.gieuning.coupon.domain.auth.repository;

import com.gieuning.coupon.global.config.RedisTestContainerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@DataRedisTest
@Import({RedisTestContainerConfig.class, RefreshTokenRepository.class})
class RefreshTokenRepositoryTest {

    private static final String TOKEN = "refresh-token-1";
    private static final String OTHER_TOKEN = "refresh-token-2";

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("저장한 토큰과 같은 토큰이면 일치한다")
    void matches_sameToken() {
        // given
        String familyId = newFamilyId();
        refreshTokenRepository.save(familyId, TOKEN, inDays(14));

        // when
        boolean matched = refreshTokenRepository.matches(familyId, TOKEN);

        // then
        assertThat(matched).isTrue();
    }

    @Test
    @DisplayName("저장한 토큰과 다른 토큰이면 일치하지 않는다")
    void matches_otherToken() {
        // given
        String familyId = newFamilyId();
        refreshTokenRepository.save(familyId, TOKEN, inDays(14));

        // when
        boolean matched = refreshTokenRepository.matches(familyId, OTHER_TOKEN);

        // then
        assertThat(matched).isFalse();
    }

    @Test
    @DisplayName("저장된 적 없는 패밀리면 일치하지 않는다")
    void matches_unknownFamily() {
        // given
        String familyId = newFamilyId();

        // when
        boolean matched = refreshTokenRepository.matches(familyId, TOKEN);

        // then
        assertThat(matched).isFalse();
    }

    @Test
    @DisplayName("남아 있는 패밀리에 replace하면 true이고, 이전 토큰은 더 이상 일치하지 않는다 (로테이션)")
    void replace_existingFamily() {
        // given
        String familyId = newFamilyId();
        refreshTokenRepository.save(familyId, TOKEN, inDays(14));

        // when
        boolean replaced = refreshTokenRepository.replace(familyId, OTHER_TOKEN, inDays(14));

        // then
        assertThat(replaced).isTrue();
        assertThat(refreshTokenRepository.matches(familyId, TOKEN)).isFalse();
        assertThat(refreshTokenRepository.matches(familyId, OTHER_TOKEN)).isTrue();
    }

    @Test
    @DisplayName("없는(삭제된) 패밀리에 replace하면 false이고 키가 새로 생기지 않는다")
    void replace_unknownFamily_doesNotCreateKey() {
        // given
        String familyId = newFamilyId();

        // when
        boolean replaced = refreshTokenRepository.replace(familyId, TOKEN, inDays(14));

        // then
        assertThat(replaced).isFalse();
        assertThat(redisTemplate.hasKey("refresh:family:" + familyId)).isFalse();
    }

    @Test
    @DisplayName("replace도 TTL을 만료 시각까지 남은 시간으로 설정한다 (SET은 TTL을 지우므로 함께 넘겨야 한다)")
    void replace_setsTtlUntilExpiresAt() {
        // given
        String familyId = newFamilyId();
        refreshTokenRepository.save(familyId, TOKEN, inDays(14));

        // when
        refreshTokenRepository.replace(familyId, OTHER_TOKEN, inDays(3));

        // then
        Long ttlSeconds = redisTemplate.getExpire("refresh:family:" + familyId, TimeUnit.SECONDS);
        assertThat(ttlSeconds).isCloseTo(Duration.ofDays(3).toSeconds(), within(5L));
    }

    @Test
    @DisplayName("만료 시각이 이미 지났으면 replace는 false이고 저장된 토큰은 바뀌지 않는다")
    void replace_expiredFamily_returnsFalse() {
        // given
        String familyId = newFamilyId();
        refreshTokenRepository.save(familyId, TOKEN, inDays(14));

        // when
        boolean replaced = refreshTokenRepository.replace(familyId, OTHER_TOKEN, Instant.now().minusSeconds(1));

        // then
        assertThat(replaced).isFalse();
        assertThat(refreshTokenRepository.matches(familyId, TOKEN)).isTrue();
    }

    @Test
    @DisplayName("패밀리를 삭제하면 true를 반환하고, 이후 그 패밀리의 토큰은 일치하지 않는다")
    void delete_family() {
        // given
        String familyId = newFamilyId();
        refreshTokenRepository.save(familyId, TOKEN, inDays(14));

        // when
        boolean deleted = refreshTokenRepository.delete(familyId);

        // then
        assertThat(deleted).isTrue();
        assertThat(refreshTokenRepository.matches(familyId, TOKEN)).isFalse();
    }

    @Test
    @DisplayName("없는 패밀리를 삭제해도 예외 없이 false를 반환한다 (로그아웃 멱등성)")
    void delete_unknownFamily() {
        // given
        String familyId = newFamilyId();

        // when
        boolean deleted = refreshTokenRepository.delete(familyId);

        // then
        assertThat(deleted).isFalse();
    }

    @Test
    @DisplayName("Redis에는 토큰 원문이 아니라 SHA-256 해시(16진수 64자)가 저장된다")
    void save_storesHashNotRawToken() {
        // given
        String familyId = newFamilyId();

        // when
        refreshTokenRepository.save(familyId, TOKEN, inDays(14));

        // then
        String stored = redisTemplate.opsForValue().get("refresh:family:" + familyId);
        assertThat(stored).isNotEqualTo(TOKEN)
                          .matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("TTL은 만료 시각까지 남은 시간으로 설정된다")
    void save_setsTtlUntilExpiresAt() {
        // given
        String familyId = newFamilyId();

        // when
        refreshTokenRepository.save(familyId, TOKEN, inDays(3));

        // then
        Long ttlSeconds = redisTemplate.getExpire("refresh:family:" + familyId, TimeUnit.SECONDS);
        assertThat(ttlSeconds).isCloseTo(Duration.ofDays(3).toSeconds(), within(5L));
    }

    private String newFamilyId() {
        return UUID.randomUUID().toString();
    }

    private Instant inDays(long days) {
        return Instant.now().plus(Duration.ofDays(days));
    }
}
