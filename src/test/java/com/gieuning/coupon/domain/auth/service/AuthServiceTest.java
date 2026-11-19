package com.gieuning.coupon.domain.auth.service;

import com.gieuning.coupon.domain.auth.dto.TokenPair;
import com.gieuning.coupon.domain.auth.dto.request.LoginRequest;
import com.gieuning.coupon.domain.auth.exception.AuthErrorCode;
import com.gieuning.coupon.domain.auth.repository.RefreshTokenRepository;
import com.gieuning.coupon.domain.member.entity.Member;
import com.gieuning.coupon.domain.member.entity.MemberRole;
import com.gieuning.coupon.domain.member.repository.MemberRepository;
import com.gieuning.coupon.global.exception.BusinessException;
import com.gieuning.coupon.global.security.JwtProperties;
import com.gieuning.coupon.global.security.JwtTokenProvider;
import com.gieuning.coupon.global.security.RefreshTokenPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String ENCODED_NOT_FOUND_PASSWORD = "$2a$10$encodedNotFoundPasswordForTimingEqualization";
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);
    private static final String FAMILY_ID = "family-1";

    @Mock
    MemberRepository memberRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    JwtTokenProvider jwtTokenProvider;
    @Mock
    RefreshTokenRepository refreshTokenRepository;

    AuthService authService;

    @BeforeEach
    void setUp() {
        given(passwordEncoder.encode(anyString())).willReturn(ENCODED_NOT_FOUND_PASSWORD);
        JwtProperties jwtProperties = new JwtProperties("unused-secret", "test-issuer", Duration.ofMinutes(30), REFRESH_TOKEN_TTL);
        authService = new AuthService(memberRepository, passwordEncoder, jwtTokenProvider, refreshTokenRepository, jwtProperties);
    }

    @Test
    @DisplayName("이메일과 비밀번호가 맞으면 토큰 쌍을 발급하고, 리프레시 토큰을 새 패밀리로 저장한다")
    void login_success() {
        // given
        LoginRequest request = loginRequest();

        Member member = memberWithId(1L);
        given(memberRepository.findByEmail(request.getEmail())).willReturn(Optional.of(member));
        given(passwordEncoder.matches(request.getPassword(), member.getPassword())).willReturn(true);
        given(jwtTokenProvider.createAccessToken(1L, MemberRole.USER)).willReturn("access-token");
        // familyId는 로그인마다 새로 만드는 UUID라 값을 미리 알 수 없다
        given(jwtTokenProvider.createRefreshToken(eq(1L), anyString(), any(Instant.class))).willReturn("refresh-token");

        // when
        TokenPair result = authService.login(request);

        // then
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.refreshTokenExpiresAt()).isCloseTo(Instant.now().plus(REFRESH_TOKEN_TTL), within(5, ChronoUnit.SECONDS));

        // 토큰에 넣은 패밀리·만료 시각과 Redis에 저장한 것이 같아야 재발급 때 대조할 수 있다
        ArgumentCaptor<String> familyId = ArgumentCaptor.forClass(String.class);
        then(jwtTokenProvider).should().createRefreshToken(eq(1L), familyId.capture(), eq(result.refreshTokenExpiresAt()));
        then(refreshTokenRepository).should().save(familyId.getValue(), "refresh-token", result.refreshTokenExpiresAt());
    }

    @Test
    @DisplayName("없는 이메일이면 INVALID_CREDENTIALS, 토큰 발급도 없다")
    void login_emailNotFound_throwsInvalidCredentials() {
        // given
        LoginRequest request = loginRequest();
        given(memberRepository.findByEmail(request.getEmail())).willReturn(Optional.empty());

        // when
        BusinessException thrown = catchThrowableOfType(BusinessException.class, () -> authService.login(request));

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        // 실패했는데 토큰이 발급되는 사고 방지 — 프로바이더에 아무 요청도 없었어야 한다
        then(jwtTokenProvider).shouldHaveNoInteractions();
        then(refreshTokenRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("없는 이메일이어도 비밀번호 비교를 거친다 — 응답 시간으로 이메일 존재 여부가 새지 않도록")
    void login_emailNotFound_stillComparesPasswordToEqualizeTiming() {
        // given
        LoginRequest request = loginRequest();
        given(memberRepository.findByEmail(request.getEmail())).willReturn(Optional.empty());

        // when
        catchThrowableOfType(BusinessException.class, () -> authService.login(request));

        // then
        then(passwordEncoder).should().matches(request.getPassword(), ENCODED_NOT_FOUND_PASSWORD);
    }

    @Test
    @DisplayName("비밀번호가 틀려도 같은 INVALID_CREDENTIALS다 (이메일 없음과 구분 안 됨)")
    void login_wrongPassword_throwsInvalidCredentials() {
        // given
        LoginRequest request = loginRequest();

        Member member = memberWithId(1L);
        given(memberRepository.findByEmail(request.getEmail())).willReturn(Optional.of(member));
        given(passwordEncoder.matches(request.getPassword(), member.getPassword())).willReturn(false);

        // when
        BusinessException thrown = catchThrowableOfType(BusinessException.class, () -> authService.login(request));

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
        then(jwtTokenProvider).shouldHaveNoInteractions();
        then(refreshTokenRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("최신 리프레시 토큰이면 같은 패밀리·같은 만료 시각으로 새 토큰 쌍을 발급하고 저장한다")
    void reissue_success() {
        // given
        Instant expiresAt = Instant.now().plus(Duration.ofDays(3));
        given(jwtTokenProvider.parseRefreshToken("old-refresh")).willReturn(new RefreshTokenPayload(1L, FAMILY_ID, expiresAt));
        given(refreshTokenRepository.matches(FAMILY_ID, "old-refresh")).willReturn(true);
        given(memberRepository.findById(1L)).willReturn(Optional.of(memberWithId(1L)));
        given(jwtTokenProvider.createAccessToken(1L, MemberRole.USER)).willReturn("new-access");
        given(jwtTokenProvider.createRefreshToken(1L, FAMILY_ID, expiresAt)).willReturn("new-refresh");

        // when
        TokenPair result = authService.reissue("old-refresh");

        // then
        assertThat(result.accessToken()).isEqualTo("new-access");
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        assertThat(result.refreshTokenExpiresAt()).isEqualTo(expiresAt);
        then(refreshTokenRepository).should().save(FAMILY_ID, "new-refresh", expiresAt);
    }

    @Test
    @DisplayName("이미 쓴 리프레시 토큰이면 패밀리를 삭제하고 INVALID_REFRESH_TOKEN, 새 토큰은 발급하지 않는다")
    void reissue_reusedToken_revokesFamily() {
        // given
        given(jwtTokenProvider.parseRefreshToken("used-refresh"))
                .willReturn(new RefreshTokenPayload(1L, FAMILY_ID, Instant.now().plus(Duration.ofDays(3))));
        given(refreshTokenRepository.matches(FAMILY_ID, "used-refresh")).willReturn(false);

        // when
        BusinessException thrown = catchThrowableOfType(BusinessException.class, () -> authService.reissue("used-refresh"));

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
        then(refreshTokenRepository).should().delete(FAMILY_ID);
        then(refreshTokenRepository).should(never()).save(any(), any(), any());
        then(jwtTokenProvider).should(never()).createAccessToken(any(), any());
    }

    @Test
    @DisplayName("만료·위조로 파싱에 실패한 토큰도 INVALID_REFRESH_TOKEN 하나로 응답한다")
    void reissue_unparsableToken_throwsInvalidRefreshToken() {
        // given
        BusinessException expired = new BusinessException(AuthErrorCode.TOKEN_EXPIRED);
        given(jwtTokenProvider.parseRefreshToken("expired-refresh")).willThrow(expired);

        // when
        BusinessException thrown = catchThrowableOfType(BusinessException.class, () -> authService.reissue("expired-refresh"));

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
        assertThat(thrown.getCause()).isSameAs(expired);
        then(refreshTokenRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("쿠키가 없으면(null) INVALID_REFRESH_TOKEN")
    void reissue_nullToken_throwsInvalidRefreshToken() {
        // when
        BusinessException thrown = catchThrowableOfType(BusinessException.class, () -> authService.reissue(null));

        // then
        assertThat(thrown.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN);
        then(jwtTokenProvider).shouldHaveNoInteractions();
        then(refreshTokenRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("로그아웃하면 토큰이 속한 패밀리를 삭제한다")
    void logout_deletesFamily() {
        // given
        given(jwtTokenProvider.parseRefreshToken("refresh"))
                .willReturn(new RefreshTokenPayload(1L, FAMILY_ID, Instant.now().plus(Duration.ofDays(3))));

        // when
        authService.logout("refresh");

        // then
        then(refreshTokenRepository).should().delete(FAMILY_ID);
    }

    @Test
    @DisplayName("무효한 토큰이나 토큰 없이 로그아웃해도 예외 없이 끝나고 아무것도 삭제하지 않는다 (멱등)")
    void logout_invalidOrMissingToken_succeeds() {
        // given
        given(jwtTokenProvider.parseRefreshToken("forged-refresh")).willThrow(new BusinessException(AuthErrorCode.INVALID_TOKEN));

        // when
        Throwable forged = catchThrowable(() -> authService.logout("forged-refresh"));
        Throwable missing = catchThrowable(() -> authService.logout(null));

        // then
        assertThat(forged).isNull();
        assertThat(missing).isNull();
        then(refreshTokenRepository).shouldHaveNoInteractions();
    }

    private LoginRequest loginRequest() {
        return LoginRequest.builder()
                           .email("test@example.com")
                           .password("password1!")
                           .build();
    }

    private Member memberWithId(Long id) {
        Member member = Member.create("test@example.com", "encoded-password", "테스터");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

}
