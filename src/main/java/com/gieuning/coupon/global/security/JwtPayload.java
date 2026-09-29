package com.gieuning.coupon.global.security;

import com.gieuning.coupon.domain.member.entity.MemberRole;

public record JwtPayload(Long memberId, MemberRole role) {
}
