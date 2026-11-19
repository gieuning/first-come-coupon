package com.gieuning.coupon.domain.auth.controller;

import com.gieuning.coupon.domain.auth.dto.TokenPair;
import com.gieuning.coupon.domain.auth.dto.request.LoginRequest;
import com.gieuning.coupon.domain.auth.dto.response.TokenResponse;
import com.gieuning.coupon.domain.auth.service.AuthService;
import com.gieuning.coupon.global.exception.BusinessException;
import com.gieuning.coupon.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        return tokenResponse(authService.login(loginRequest));
    }

    // 쿠키가 없어도 400이 아니라 서비스까지 보내 401 INVALID_REFRESH_TOKEN으로 응답한다 (재발급 실패 코드는 하나)
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<TokenResponse>> reissue(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        try {
            return tokenResponse(authService.reissue(refreshToken));
        } catch (BusinessException e) {
            // 다시 쓸 수 없는 쿠키는 지운다. 에러 응답은 GlobalExceptionHandler가 쓰고, 여기서 붙인 헤더는 유지된다
            // Redis 장애 같은 인프라 예외는 잡지 않는다 — 재시도하면 되는 토큰까지 버리게 된다
            response.addHeader(HttpHeaders.SET_COOKIE, expiredRefreshTokenCookie().toString());
            throw e;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                             .header(HttpHeaders.SET_COOKIE, expiredRefreshTokenCookie().toString())
                             .build();
    }

    private ResponseEntity<ApiResponse<TokenResponse>> tokenResponse(TokenPair tokenPair) {
        // 쿠키 수명도 패밀리의 남은 수명에 맞춘다 — 재발급할 때마다 14일로 늘어나지 않게
        Duration maxAge = Duration.between(Instant.now(), tokenPair.refreshTokenExpiresAt());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie(tokenPair.refreshToken(), maxAge).toString())
                .body(ApiResponse.success(TokenResponse.of(tokenPair.accessToken())));
    }

    // 리프레시 토큰은 XSS 노출을 피해 바디가 아닌 HttpOnly 쿠키로 전달한다
    private ResponseCookie refreshTokenCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
                .httpOnly(true)
                .secure(true)      // 브라우저는 localhost를 예외 취급해 로컬 개발에서도 동작한다
                .sameSite("Strict")
                .path("/api/auth") // 재발급·로그아웃 요청에만 실림
                .maxAge(maxAge)
                .build();
    }

    // 삭제는 이름·path가 같은 쿠키를 Max-Age=0으로 덮어쓰는 것 — path가 다르면 브라우저는 다른 쿠키로 보고 남겨 둔다
    private ResponseCookie expiredRefreshTokenCookie() {
        return refreshTokenCookie("", Duration.ZERO);
    }

}
