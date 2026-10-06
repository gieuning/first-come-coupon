package com.gieuning.coupon.global.security;

import com.gieuning.coupon.domain.auth.dto.request.LoginRequest;
import com.gieuning.coupon.domain.auth.exception.AuthErrorCode;
import com.gieuning.coupon.domain.member.dto.request.MemberSignupRequest;
import com.gieuning.coupon.domain.member.entity.MemberRole;
import com.gieuning.coupon.domain.member.repository.MemberRepository;
import com.gieuning.coupon.global.config.MySqlTestContainerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import java.time.Duration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("test")
@Import(MySqlTestContainerConfig.class)
class JwtAuthFlowApiTest {

    private static final String EMAIL = "test1234@naver.com";
    private static final String PASSWORD = "password1!";
    private static final String ATTACKER_SECRET = "yJn6KMLo4u2aM0AZVtMH1C8lg3FAThtY5hPDIl1H6ETDIQTTbhG8ZO8KIzUl4V40";

    private record LoginBody(TokenData data) {}
    private record TokenData(String accessToken) {}

    @Autowired
    RestTestClient restTestClient;

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("유효한 토큰으로 /me를 호출하면 200과 내 정보가 온다")
    void me_withValidToken_returns200() {
        // given
        signup(EMAIL, PASSWORD);
        String accessToken = login(EMAIL, PASSWORD)
                .expectBody(LoginBody.class)
                .returnResult()
                .getResponseBody()
                .data().accessToken();

        // when
        RestTestClient.ResponseSpec response = restTestClient.get().uri("/api/members/me")
                                                             .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                                             .exchange();
        // then
        response.expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.email").isEqualTo(EMAIL);
    }

    @Test
    @DisplayName("토큰 없이 /me를 호출하면 401 UNAUTHENTICATED")
    void me_withoutToken_returns401Unauthenticated() {
        // when
        RestTestClient.ResponseSpec response = restTestClient.get().uri("/api/members/me").exchange();

        // then
        response.expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody()
                .jsonPath("$.code").isEqualTo(AuthErrorCode.UNAUTHENTICATED.getCode())
                .jsonPath("$.message").isEqualTo(AuthErrorCode.UNAUTHENTICATED.getMessage());
    }

    @Test
    @DisplayName("만료된 토큰으로 /me를 호출하면 401 TOKEN_EXPIRED")
    void me_withExpiredToken_returns401TokenExpired() {
        // given: 서버와 같은 시크릿·issuer, TTL만 음수 — 만료된 채 태어난 토큰
        JwtTokenProvider expiredTokenFactory = new JwtTokenProvider(new JwtProperties(
                jwtProperties.secret(),
                jwtProperties.issuer(),
                Duration.ofMinutes(-1),
                jwtProperties.refreshTokenTtl()));

        String expiredToken = expiredTokenFactory.createAccessToken(1L, MemberRole.USER);

        // when
        RestTestClient.ResponseSpec response = restTestClient.get().uri("/api/members/me")
                                                             .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                                                             .exchange();

        // then
        response.expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody()
                .jsonPath("$.code").isEqualTo(AuthErrorCode.TOKEN_EXPIRED.getCode())
                .jsonPath("$.message").isEqualTo(AuthErrorCode.TOKEN_EXPIRED.getMessage());
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰으로 /me를 호출하면 401 INVALID_TOKEN")
    void me_withForgedToken_returns401InvalidToken() {
        // given
        JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(new JwtProperties(
                ATTACKER_SECRET,
                jwtProperties.issuer(),
                jwtProperties.accessTokenTtl(),
                jwtProperties.refreshTokenTtl()));
        
        String accessToken = jwtTokenProvider.createAccessToken(1L, MemberRole.USER);

        // when
        RestTestClient.ResponseSpec response = restTestClient.get().uri("/api/members/me")
                                                             .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                                             .exchange();
        // then
        response.expectStatus().isEqualTo(HttpStatus.UNAUTHORIZED)
                .expectBody()
                .jsonPath("$.code").isEqualTo(AuthErrorCode.INVALID_TOKEN.getCode())
                .jsonPath("$.message").isEqualTo(AuthErrorCode.INVALID_TOKEN.getMessage());
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
