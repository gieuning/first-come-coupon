package com.gieuning.coupon.global.security;

import com.gieuning.coupon.domain.auth.exception.AuthErrorCode;
import com.gieuning.coupon.domain.member.entity.MemberRole;
import com.gieuning.coupon.global.exception.BusinessException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest {

    // Base64 계약을 따르는 테스트 키 (openssl rand -base64 48 출력 — 디코딩하면 48바이트)
    private static final String SERVER_SECRET = "OodI43z5y+PhFkWDKrBOKYnxqa5gu/Rz3Umf/7cqHx1JN2B4DOpbrxuqEcR//hjS";
    private static final String ATTACKER_SECRET = "5soFj9sMAEpn3vjVHskePxPeHw/mzaM93Im9pVJ4F3rmHbHdlqZrwoOaLW8bJCAg";
    private static final String ISSUER = "test-issuer";
    private static final String FAMILY_ID = "family-1";

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = providerWith(SERVER_SECRET, Duration.ofMinutes(30));
    }

    private JwtTokenProvider providerWith(String secret, Duration accessTtl) {
        return new JwtTokenProvider(
                new JwtProperties(secret, ISSUER, accessTtl, Duration.ofDays(14))
        );
    }

    @Test
    @DisplayName("발급한 액세스 토큰을 파싱하면 memberId와 role이 그대로 나온다")
    void accessToken_roundTrip() {
        // given
        Long memberId = 1L;

        // when
        String token = tokenProvider.createAccessToken(memberId, MemberRole.USER);
        JwtPayload payload = tokenProvider.parseAccessToken(token);

        // then
        assertThat(payload.memberId()).isEqualTo(memberId);
        assertThat(payload.role()).isEqualTo(MemberRole.USER);
    }

    @Test
    @DisplayName("리프레시 토큰을 액세스 토큰으로 파싱하면 INVALID_TOKEN으로 거부한다")
    void refreshToken_cannotBeUsedAsAccessToken() {
        // given
        String refreshToken = tokenProvider.createRefreshToken(1L, FAMILY_ID);

        // when
        BusinessException thrown = catchThrowableOfType(BusinessException.class,
                () -> tokenProvider.parseAccessToken(refreshToken));

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("다른 시크릿으로 서명된 토큰은 INVALID_TOKEN으로 거부한다")
    void tokenSignedWithOtherSecret_isRejected() {
        // given
        JwtTokenProvider attacker = providerWith(ATTACKER_SECRET, Duration.ofMinutes(30));
        String forgedToken = attacker.createAccessToken(1L, MemberRole.USER);

        // when
        BusinessException thrown = catchThrowableOfType(BusinessException.class,
                () -> tokenProvider.parseAccessToken(forgedToken));

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("필수 클레임(role)이 없는 토큰은 INVALID_TOKEN으로 거부한다")
    void tokenWithoutRoleClaim_isRejected() {
        // given — 서명·type·만료는 전부 유효하지만 role 클레임만 없는 토큰.
        //         프로바이더의 공개 API로는 못 만드는 비정상 토큰이라 jjwt로 직접 빌드한다
        Instant now = Instant.now();
        String tokenWithoutRole = Jwts.builder()
                .issuer(ISSUER)
                .subject("1")
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(30))))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SERVER_SECRET)))
                .compact();

        BusinessException thrown = catchThrowableOfType(BusinessException.class,
                () -> tokenProvider.parseAccessToken(tokenWithoutRole));

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("만료된 액세스 토큰은 TOKEN_EXPIRED로 거부한다")
    void expiredAccessToken_isRejected() {
        // given — 서명은 유효하고 만료 시각만 과거인 토큰 (TTL이 음수라 발급 즉시 만료)
        JwtTokenProvider expiredProvider = providerWith(SERVER_SECRET, Duration.ofSeconds(-1));
        String expiredToken = expiredProvider.createAccessToken(1L, MemberRole.USER);

        // when
        BusinessException thrown = catchThrowableOfType(BusinessException.class,
                () -> tokenProvider.parseAccessToken(expiredToken));

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(AuthErrorCode.TOKEN_EXPIRED);
    }

    @Test
    @DisplayName("발급한 리프레시 토큰을 파싱하면 memberId·familyId가 그대로 나오고, 만료는 지금부터 14일 뒤다")
    void refreshToken_roundTrip() {
        // given
        Long memberId = 1L;

        // when
        String token = tokenProvider.createRefreshToken(memberId, FAMILY_ID);
        RefreshTokenPayload payload = tokenProvider.parseRefreshToken(token);

        // then
        assertThat(payload.memberId()).isEqualTo(memberId);
        assertThat(payload.familyId()).isEqualTo(FAMILY_ID);
        assertThat(payload.expiresAt()).isCloseTo(Instant.now().plus(Duration.ofDays(14)), within(5, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("같은 회원·패밀리로 연달아 발급해도 리프레시 토큰 문자열은 매번 다르다")
    void refreshToken_isUniqueEvenWithinSameSecond() {
        // given — iat·exp는 초 단위라, 같은 초에 발급되면 jti 없이는 같은 문자열이 나온다
        Long memberId = 1L;

        // when
        String first = tokenProvider.createRefreshToken(memberId, FAMILY_ID);
        String second = tokenProvider.createRefreshToken(memberId, FAMILY_ID);

        // then
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("만료 시각을 지정해 발급하면 그 시각이 그대로 유지된다 (재발급 시 절대 만료)")
    void refreshToken_keepsGivenExpiration() {
        // given — JWT의 exp는 초 단위라 비교 기준도 초로 자른다
        Instant expiresAt = Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS);

        // when
        String token = tokenProvider.createRefreshToken(1L, FAMILY_ID, expiresAt);
        RefreshTokenPayload payload = tokenProvider.parseRefreshToken(token);

        // then
        assertThat(payload.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    @DisplayName("액세스 토큰을 리프레시 토큰으로 파싱하면 INVALID_TOKEN으로 거부한다")
    void accessToken_cannotBeUsedAsRefreshToken() {
        // given
        String accessToken = tokenProvider.createAccessToken(1L, MemberRole.USER);

        // when
        BusinessException thrown = catchThrowableOfType(BusinessException.class,
                () -> tokenProvider.parseRefreshToken(accessToken));

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN);
    }

    @Test
    @DisplayName("패밀리(sid) 클레임이 없는 리프레시 토큰은 INVALID_TOKEN으로 거부한다")
    void refreshTokenWithoutFamilyId_isRejected() {
        // given — 이 변경 전에 발급된 형태의 토큰. 공개 API로는 못 만들어 jjwt로 직접 빌드한다
        Instant now = Instant.now();
        String tokenWithoutFamilyId = Jwts.builder()
                .issuer(ISSUER)
                .subject("1")
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofDays(14))))
                .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SERVER_SECRET)))
                .compact();

        // when
        BusinessException thrown = catchThrowableOfType(BusinessException.class,
                () -> tokenProvider.parseRefreshToken(tokenWithoutFamilyId));

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN);
    }
}
