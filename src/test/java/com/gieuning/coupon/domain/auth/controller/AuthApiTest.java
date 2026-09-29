package com.gieuning.coupon.domain.auth.controller;

import com.gieuning.coupon.domain.auth.dto.request.LoginRequest;
import com.gieuning.coupon.domain.auth.exception.AuthErrorCode;
import com.gieuning.coupon.domain.member.dto.request.MemberSignupRequest;
import com.gieuning.coupon.domain.member.repository.MemberRepository;
import com.gieuning.coupon.global.config.MySqlTestContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import(MySqlTestContainerConfig.class)
class AuthApiTest {

    private static final String EMAIL = "test1234@naver.com";
    private static final String PASSWORD = "password1!";

    @Autowired
    RestTestClient restTestClient;

    @Autowired
    MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("가입된 회원이 로그인하면 200, 바디에 accessToken, 쿠키에 refresh_token이 온다")
    void login_success() {
        // given
        signup(EMAIL, PASSWORD);

        // when
        RestTestClient.ResponseSpec response = login(EMAIL, PASSWORD);

        // then: 액세스 토큰은 바디로
        response.expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.accessToken").exists()
                .jsonPath("$.data.refreshToken").doesNotExist();

        // then: 리프레시 토큰은 쿠키로 — 방어 속성까지 계약
        // (sameSite는 CSRF 방어의 유일한 축, secure는 평문 전송 차단 — 지워지면 테스트가 물어야 한다)
        response.expectCookie().exists("refresh_token")
                .expectCookie().httpOnly("refresh_token", true)
                .expectCookie().secure("refresh_token", true)
                .expectCookie().sameSite("refresh_token", "Strict")
                .expectCookie().path("refresh_token", "/api/auth");
    }

    @Test
    @DisplayName("틀린 비밀번호도 없는 이메일도 같은 401 + INVALID_CREDENTIALS로 응답한다")
    void login_fail_indistinguishable() {
        // given
        signup(EMAIL, PASSWORD);

        // when & then: 비밀번호 불일치와 이메일 부재가 같은 응답이어야 한다
        assertInvalidCredentials(login(EMAIL, "wrong-pw-1!"));
        assertInvalidCredentials(login("ghost@naver.com", PASSWORD));
    }

    @Test
    @DisplayName("빈 이메일로 로그인 요청하면 400과 INVALID_REQUEST, fieldErrors에 email이 담긴다")
    void login_blankEmail_returns400() {
        // when & then
        login("", PASSWORD)
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST")
                .jsonPath("$.fieldErrors[0].field").isEqualTo("email");
    }

    private void assertInvalidCredentials(RestTestClient.ResponseSpec response) {
        response.expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody()
                .jsonPath("$.code").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS.getCode())
                .jsonPath("$.message").isEqualTo(AuthErrorCode.INVALID_CREDENTIALS.getMessage());
    }

    private void signup(String email, String password) {
        restTestClient.post().uri("/api/members")
                      .contentType(MediaType.APPLICATION_JSON)
                      .body(MemberSignupRequest.builder()
                              .email(email)
                              .password(password)
                              .nickname("기은")
                              .build())
                      .exchange()
                      .expectStatus().isCreated();
    }

    private RestTestClient.ResponseSpec login(String email, String password) {
        return restTestClient.post().uri("/api/auth/login")
                             .contentType(MediaType.APPLICATION_JSON)
                             .body(LoginRequest.builder()
                                     .email(email)
                                     .password(password)
                                     .build())
                             .exchange();
    }
}
