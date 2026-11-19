package com.gieuning.coupon.domain.auth.dto;

import java.time.Instant;

// refreshTokenExpiresAt: 쿠키 Max-Age를 패밀리의 남은 수명에 맞추기 위함
public record TokenPair(String accessToken, String refreshToken, Instant refreshTokenExpiresAt){

}
