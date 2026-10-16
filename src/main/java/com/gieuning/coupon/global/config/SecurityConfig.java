package com.gieuning.coupon.global.config;

import com.gieuning.coupon.global.security.JwtAuthenticationEntryPoint;
import com.gieuning.coupon.global.security.JwtAuthenticationFilter;
import com.gieuning.coupon.global.security.JwtProperties;
import com.gieuning.coupon.global.security.JwtTokenProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtTokenProvider jwtTokenProvider,
                                                   JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint) throws Exception {
        http
                // 액세스 토큰은 헤더로 직접 전송하므로 CSRF 전제(자동 전송)가 없다.
                // 자동 전송되는 쿠키는 리프레시 토큰 하나뿐이며, SameSite=Strict(타 사이트발 요청에 미전송)와
                // 응답 비가독성(재발급 응답은 동일 출처 정책 때문에 공격자가 읽지 못함)으로 방어한다.
                // 따라서 CSRF 토큰 방식은 비용만 있고 얻는 것이 없어 켜지 않는다.
                .csrf(AbstractHttpConfigurer::disable)
                // 서버가 세션을 만들지도, 참조하지도 않음 — 인증 상태는 매 요청의 토큰으로만 판단
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                // 폼 로그인·HTTP Basic은 세션/브라우저 전제 방식이라 REST API에선 비활성화
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/members").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}
