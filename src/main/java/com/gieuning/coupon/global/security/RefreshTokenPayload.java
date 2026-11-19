package com.gieuning.coupon.global.security;

import java.time.Instant;

// expiresAt: 재발급 시 새 토큰의 만료와 Redis TTL에 그대로 이어 쓴다 (절대 만료)
public record RefreshTokenPayload(Long memberId, String familyId, Instant expiresAt) {
}
