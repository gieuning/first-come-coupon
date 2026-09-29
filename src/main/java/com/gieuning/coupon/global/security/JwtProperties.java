package com.gieuning.coupon.global.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Base64;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(

        @NotBlank
        String secret,

        @NotBlank
        String issuer,

        @NotNull
        @DurationMin(seconds = 1, message = "jwt.access-token-ttl은 1초 이상이어야 합니다")
        Duration accessTokenTtl,

        @NotNull
        @DurationMin(seconds = 1, message = "jwt.refresh-token-ttl은 1초 이상이어야 합니다")
        Duration refreshTokenTtl
) {

    @AssertTrue(message = "jwt.access-token-ttl은 jwt.refresh-token-ttl보다 짧아야 합니다")
    public boolean isAccessTtlShorterThanRefreshTtl() {
        if (accessTokenTtl == null || refreshTokenTtl == null) {
            return true;
        }
        return accessTokenTtl.compareTo(refreshTokenTtl) < 0;
    }

    @AssertTrue(message = "jwt.secret은 Base64로 인코딩된 32바이트 이상의 키여야 합니다 (생성: openssl rand -base64 48)")
    public boolean isSecretAValidBase64Key() {
        if (secret == null) {
            return true;
        }
        try {
            return Base64.getDecoder().decode(secret).length >= 32;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
