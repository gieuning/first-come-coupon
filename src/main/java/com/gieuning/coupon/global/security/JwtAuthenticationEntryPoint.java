package com.gieuning.coupon.global.security;

import com.gieuning.coupon.domain.auth.exception.AuthErrorCode;
import com.gieuning.coupon.global.exception.ErrorCode;
import com.gieuning.coupon.global.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // attribute 없음(null)과 엉뚱한 타입 모두 instanceof가 걸러 기본값으로 — 캐스팅 500 원천 차단
        ErrorCode errorCode =
                request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE) instanceof ErrorCode code
                        ? code
                        : AuthErrorCode.UNAUTHENTICATED;

        log.info("Authentication failed: code={}, method={}, uri={}",
                errorCode.getCode(), request.getMethod(), request.getRequestURI());

        String challenge = "Bearer realm=\"coupon-api\"";
        if (errorCode == AuthErrorCode.INVALID_TOKEN || errorCode == AuthErrorCode.TOKEN_EXPIRED) {
            challenge += ", error=\"invalid_token\"";
        }

        response.setStatus(errorCode.getStatus().value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(errorCode)));
    }
}

