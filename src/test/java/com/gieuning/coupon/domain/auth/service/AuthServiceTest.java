package com.gieuning.coupon.domain.auth.service;

import com.gieuning.coupon.domain.auth.dto.TokenPair;
import com.gieuning.coupon.domain.auth.dto.request.LoginRequest;
import com.gieuning.coupon.domain.auth.exception.AuthErrorCode;
import com.gieuning.coupon.domain.member.entity.Member;
import com.gieuning.coupon.domain.member.entity.MemberRole;
import com.gieuning.coupon.domain.member.repository.MemberRepository;
import com.gieuning.coupon.global.exception.BusinessException;
import com.gieuning.coupon.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String ENCODED_NOT_FOUND_PASSWORD = "$2a$10$encodedNotFoundPasswordForTimingEqualization";

    @Mock
    MemberRepository memberRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @Mock
    JwtTokenProvider jwtTokenProvider;

    AuthService authService;

    @BeforeEach
    void setUp() {
        given(passwordEncoder.encode(anyString())).willReturn(ENCODED_NOT_FOUND_PASSWORD);
        authService = new AuthService(memberRepository, passwordEncoder, jwtTokenProvider);
    }

    @Test
    @DisplayName("이메일과 비밀번호가 맞으면 토큰 쌍을 발급한다")
    void login_success() {
        // given
        LoginRequest request = loginRequest();

        Member member = memberWithId(1L);
        given(memberRepository.findByEmail(request.getEmail())).willReturn(Optional.of(member));
        given(passwordEncoder.matches(request.getPassword(), member.getPassword())).willReturn(true);
        given(jwtTokenProvider.createAccessToken(1L, MemberRole.USER)).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(1L)).willReturn("refresh-token");

        // when
        TokenPair result = authService.login(request);

        // then
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
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
