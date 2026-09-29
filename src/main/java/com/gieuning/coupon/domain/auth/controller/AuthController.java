package com.gieuning.coupon.domain.auth.controller;

import com.gieuning.coupon.domain.auth.dto.TokenPair;
import com.gieuning.coupon.domain.auth.dto.request.LoginRequest;
import com.gieuning.coupon.domain.auth.dto.response.TokenResponse;
import com.gieuning.coupon.domain.auth.service.AuthService;
import com.gieuning.coupon.global.response.ApiResponse;
import com.gieuning.coupon.global.security.JwtProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        TokenPair tokenPair = authService.login(loginRequest);

        // 리프레시 토큰은 XSS 노출을 피해 바디가 아닌 HttpOnly 쿠키로 전달한다
        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", tokenPair.refreshToken())
                .httpOnly(true)
                .secure(true)      // 브라우저는 localhost를 예외 취급해 로컬 개발에서도 동작한다
                .sameSite("Strict")
                .path("/api/auth") // 재발급·로그아웃 요청에만 실림
                .maxAge(jwtProperties.refreshTokenTtl())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(TokenResponse.of(tokenPair.accessToken())));
    }

}
