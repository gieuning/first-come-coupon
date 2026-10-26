package com.gieuning.coupon.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.*;

@DataRedisTest
@Import(RedisTestContainerConfig.class)
class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("Redis에 저장한 값을 같은 키로 다시 꺼낼 수 있다")
    void setAndGet() {
        // given
        String key = "connection-test";
        String value = "hello";

        // when
        redisTemplate.opsForValue().set(key, value);
        String found = redisTemplate.opsForValue().get(key);

        // then
        assertThat(found).isEqualTo(value);
    }

}
