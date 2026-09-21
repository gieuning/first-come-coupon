package com.gieuning.coupon.global.security;

import com.gieuning.coupon.domain.auth.exception.AuthErrorCode;
import com.gieuning.coupon.domain.member.entity.MemberRole;
import com.gieuning.coupon.global.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtTokenProvider {

    private static final String ROLE_CLAIM = "role";
    private static final String TYPE_CLAIM = "type";
    private static final String ACCESS_TYPE = "access";
    private static final String REFRESH_TYPE = "refresh";

    private final SecretKey signingKey;
    private final JwtParser jwtParser;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtTokenProvider(JwtProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.secret()));
        // 파서는 상태가 없어 스레드 안전하므로 요청마다 만들지 않고 재사용한다
        this.jwtParser = Jwts.parser()
                             .verifyWith(signingKey)
                             .requireIssuer(properties.issuer())
                             .build();
        this.issuer = properties.issuer();
        this.accessTokenTtl = properties.accessTokenTtl();
        this.refreshTokenTtl = properties.refreshTokenTtl();
    }

    public String createAccessToken(Long memberId, MemberRole role) {
        return baseBuilder(memberId, ACCESS_TYPE, accessTokenTtl)
                .claim(ROLE_CLAIM, role.name())
                .signWith(signingKey)
                .compact();
    }

    // 리프레시 토큰에는 role을 담지 않는다 — 재발급 외의 용도가 없고, 권한 정보는 짧게 사는 액세스 토큰의 몫
    public String createRefreshToken(Long memberId) {
        return baseBuilder(memberId, REFRESH_TYPE, refreshTokenTtl)
                .signWith(signingKey)
                .compact();
    }

    public JwtPayload parseAccessToken(String token) {
        return parse(token, ACCESS_TYPE, claims -> new JwtPayload(
                Long.valueOf(claims.getSubject()),
                MemberRole.valueOf(claims.get(ROLE_CLAIM, String.class))
        ));
    }

    public Long parseRefreshToken(String token) {
        return parse(token, REFRESH_TYPE, claims -> Long.valueOf(claims.getSubject()));
    }

    private JwtBuilder baseBuilder(Long memberId, String type, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
                   .issuer(issuer)
                   .subject(memberId.toString())
                   .claim(TYPE_CLAIM, type)
                   .issuedAt(Date.from(now))
                   .expiration(Date.from(now.plus(ttl)));
    }

    private <T> T parse(String token, String expectedType, Function<Claims, T> mapper) {
        try {
            Claims claims = jwtParser.parseSignedClaims(token).getPayload();
            // type을 확인하지 않으면 수명이 긴 리프레시 토큰을 액세스 토큰처럼 쓸 수 있다
            if (!expectedType.equals(claims.get(TYPE_CLAIM, String.class))) {
                throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
            }
            return mapper.apply(claims);
        } catch (BusinessException e) {
            throw e;
        } catch (ExpiredJwtException e) {
            throw new BusinessException(AuthErrorCode.TOKEN_EXPIRED, e);
        } catch (RuntimeException e) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN, e);
        }
    }
}
