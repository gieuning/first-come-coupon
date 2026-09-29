package com.gieuning.coupon.domain.auth.service;

import com.gieuning.coupon.domain.auth.dto.TokenPair;
import com.gieuning.coupon.domain.auth.dto.request.LoginRequest;
import com.gieuning.coupon.domain.auth.exception.AuthErrorCode;
import com.gieuning.coupon.domain.member.entity.Member;
import com.gieuning.coupon.domain.member.repository.MemberRepository;
import com.gieuning.coupon.global.exception.BusinessException;
import com.gieuning.coupon.global.security.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final String USER_NOT_FOUND_PASSWORD = "user-not-found-password";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final String userNotFoundEncodedPassword;

    public AuthService(MemberRepository memberRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userNotFoundEncodedPassword = passwordEncoder.encode(USER_NOT_FOUND_PASSWORD);
    }

    public TokenPair login(LoginRequest request) {
        Member member = memberRepository.findByEmail(request.getEmail()).orElse(null);

        if (member == null) {
            mitigateAgainstTimingAttack(request.getPassword());
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        if (!passwordEncoder.matches(request.getPassword(), member.getPassword())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtTokenProvider.createAccessToken(member.getId(), member.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getId());

        return new TokenPair(accessToken, refreshToken);
    }

    // 트레이드오프: 존재하지 않는 이메일 요청에도 BCrypt 비용이 든다
    private void mitigateAgainstTimingAttack(String rawPassword) {
        passwordEncoder.matches(rawPassword, userNotFoundEncodedPassword);
    }
}
