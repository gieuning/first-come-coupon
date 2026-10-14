package com.gieuning.coupon.global.security;

import com.gieuning.coupon.global.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // 파싱 실패 사유를 EntryPoint에 전달하는 request attribute 키
    public static final String AUTH_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".AUTH_ERROR";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            JwtPayload payload = jwtTokenProvider.parseAccessToken(token);

            // ROLE_ 접두사는 Security 내부 규약이라 security 계층인 여기서 붙인다
            List<SimpleGrantedAuthority> authorities =
                    List.of(new SimpleGrantedAuthority("ROLE_" + payload.role().name()));

            // 3인자 생성자만 authenticated=true로 만든다
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(payload, null, authorities);

            // 기존 컨텍스트 수정 대신 새로 만들어 교체 — 컨텍스트가 스레드 간 공유되는 전략에서도 안전
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (BusinessException e) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, e.getErrorCode());
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        // RFC 9110상 auth-scheme은 대소문자를 구분하지 않는다 — 토큰은 구분하므로 스킴 구간만 비교
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }

        int tokenStart = BEARER_PREFIX.length();
        while (tokenStart < header.length() && header.charAt(tokenStart) == ' ') {
            tokenStart++;
        }
        return header.substring(tokenStart);
    }
}
