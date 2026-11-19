package com.gieuning.coupon.domain.auth.controller;

import com.gieuning.coupon.domain.auth.dto.request.LoginRequest;
import com.gieuning.coupon.domain.auth.exception.AuthErrorCode;
import com.gieuning.coupon.domain.member.dto.request.MemberSignupRequest;
import com.gieuning.coupon.domain.member.repository.MemberRepository;
import com.gieuning.coupon.global.config.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class RefreshTokenFlowApiTest {

    private static final String EMAIL = "test1234@naver.com";
    private static final String PASSWORD = "password1!";
    private static final String REFRESH_COOKIE = "refresh_token";

    private record TokenBody(TokenData data) {}
    private record TokenData(String accessToken) {}
    private record Tokens(String accessToken, String refreshToken) {}

    @Autowired
    RestTestClient restTestClient;

    @Autowired
    MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        signup();
    }

    @Test
    @DisplayName("로그인 쿠키로 재발급하면 200, 새 리프레시 쿠키가 오고 새 액세스 토큰으로 /me를 호출할 수 있다")
    void reissue_success() {
        // given
        Tokens login = login();

        // when
        RestTestClient.ResponseSpec response = reissue(login.refreshToken());

        // then
        response.expectStatus().isOk()
                .expectCookie().httpOnly(REFRESH_COOKIE, true)
                .expectCookie().secure(REFRESH_COOKIE, true)
                .expectCookie().sameSite(REFRESH_COOKIE, "Strict")
                .expectCookie().path(REFRESH_COOKIE, "/api/auth")
                // 쿠키 수명은 남은 패밀리 수명 — 재발급으로 14일을 넘기지 않는다
                .expectCookie().maxAge(REFRESH_COOKIE, maxAge ->
                        assertThat(maxAge).isPositive().isLessThanOrEqualTo(Duration.ofDays(14).toSeconds()));

        Tokens reissued = tokensOf(response);
        assertThat(reissued.refreshToken()).isNotEqualTo(login.refreshToken());
        me(reissued.accessToken()).expectStatus().isOk();
    }

    @Test
    @DisplayName("이미 쓴 리프레시 토큰으로 재발급하면 401과 쿠키 삭제, 그 패밀리의 최신 토큰도 함께 끊긴다")
    void reissue_withReusedToken_revokesFamily() {
        // given
        Tokens login = login();
        Tokens reissued = tokensOf(reissue(login.refreshToken()));

        // when — 탈취된 이전 토큰이 다시 들어온 상황
        RestTestClient.ResponseSpec reuse = reissue(login.refreshToken());

        // then
        assertInvalidRefreshTokenWithCookieCleared(reuse);
        // 서버는 누가 정상 사용자인지 모르므로 최신 토큰 쪽도 재로그인해야 한다
        assertInvalidRefreshTokenWithCookieCleared(reissue(reissued.refreshToken()));
    }

    @Test
    @DisplayName("한 기기에서 재사용이 감지돼도 다른 기기(다른 패밀리)의 재발급은 영향받지 않는다")
    void reissue_reuseOnOneDevice_doesNotAffectOtherDevice() {
        // given — 같은 회원이 두 기기에서 로그인
        Tokens deviceA = login();
        Tokens deviceB = login();
        reissue(deviceA.refreshToken()).expectStatus().isOk();

        // when
        reissue(deviceA.refreshToken()).expectStatus().isUnauthorized();

        // then
        reissue(deviceB.refreshToken()).expectStatus().isOk();
    }

    @Test
    @DisplayName("리프레시 쿠키 없이 재발급하면 401 INVALID_REFRESH_TOKEN")
    void reissue_withoutCookie_returns401() {
        // when
        RestTestClient.ResponseSpec response = restTestClient.post().uri("/api/auth/reissue").exchange();

        // then
        assertInvalidRefreshTokenWithCookieCleared(response);
    }

    @Test
    @DisplayName("로그아웃하면 204와 쿠키 삭제, 이후 그 리프레시 토큰으로는 재발급할 수 없다")
    void logout_revokesFamily() {
        // given
        Tokens login = login();

        // when
        RestTestClient.ResponseSpec response = logout(login.refreshToken());

        // then
        response.expectStatus().isNoContent()
                .expectCookie().maxAge(REFRESH_COOKIE, Duration.ZERO)
                .expectCookie().path(REFRESH_COOKIE, "/api/auth");
        assertInvalidRefreshTokenWithCookieCleared(reissue(login.refreshToken()));
    }

    @Test
    @DisplayName("쿠키 없이 로그아웃해도 204 (멱등)")
    void logout_withoutCookie_returns204() {
        // when
        RestTestClient.ResponseSpec response = restTestClient.post().uri("/api/auth/logout").exchange();

        // then
        response.expectStatus().isNoContent();
    }

    private void assertInvalidRefreshTokenWithCookieCleared(RestTestClient.ResponseSpec response) {
        response.expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                // 삭제 쿠키는 원래 쿠키와 path가 같아야 브라우저가 지운다
                .expectCookie().maxAge(REFRESH_COOKIE, Duration.ZERO)
                .expectCookie().path(REFRESH_COOKIE, "/api/auth")
                .expectBody()
                .jsonPath("$.code").isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN.getCode());
    }

    private Tokens tokensOf(RestTestClient.ResponseSpec response) {
        EntityExchangeResult<TokenBody> result = response.expectStatus().isOk().returnResult(TokenBody.class);
        String refreshToken = result.getResponseCookies().getFirst(REFRESH_COOKIE).getValue();
        return new Tokens(result.getResponseBody().data().accessToken(), refreshToken);
    }

    private void signup() {
        restTestClient.post().uri("/api/members")
                      .contentType(MediaType.APPLICATION_JSON)
                      .body(MemberSignupRequest.builder()
                                               .email(EMAIL)
                                               .password(PASSWORD)
                                               .nickname("기은")
                                               .build())
                      .exchange()
                      .expectStatus().isCreated();
    }

    private Tokens login() {
        return tokensOf(restTestClient.post().uri("/api/auth/login")
                                      .contentType(MediaType.APPLICATION_JSON)
                                      .body(LoginRequest.builder()
                                                        .email(EMAIL)
                                                        .password(PASSWORD)
                                                        .build())
                                      .exchange());
    }

    private RestTestClient.ResponseSpec reissue(String refreshToken) {
        return restTestClient.post().uri("/api/auth/reissue")
                             .cookie(REFRESH_COOKIE, refreshToken)
                             .exchange();
    }

    private RestTestClient.ResponseSpec logout(String refreshToken) {
        return restTestClient.post().uri("/api/auth/logout")
                             .cookie(REFRESH_COOKIE, refreshToken)
                             .exchange();
    }

    private RestTestClient.ResponseSpec me(String accessToken) {
        return restTestClient.get().uri("/api/members/me")
                             .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                             .exchange();
    }
}
