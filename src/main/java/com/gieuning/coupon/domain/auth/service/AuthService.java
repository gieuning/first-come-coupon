package com.gieuning.coupon.domain.auth.service;

import com.gieuning.coupon.domain.auth.dto.TokenPair;
import com.gieuning.coupon.domain.auth.dto.request.LoginRequest;
import com.gieuning.coupon.domain.auth.exception.AuthErrorCode;
import com.gieuning.coupon.domain.auth.repository.RefreshTokenRepository;
import com.gieuning.coupon.domain.member.entity.Member;
import com.gieuning.coupon.domain.member.repository.MemberRepository;
import com.gieuning.coupon.global.exception.BusinessException;
import com.gieuning.coupon.global.security.JwtProperties;
import com.gieuning.coupon.global.security.JwtTokenProvider;
import com.gieuning.coupon.global.security.RefreshTokenPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class AuthService {

    private static final String USER_NOT_FOUND_PASSWORD = "user-not-found-password";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final String userNotFoundEncodedPassword;

    public AuthService(MemberRepository memberRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       RefreshTokenRepository refreshTokenRepository,
                       JwtProperties jwtProperties) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
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

        // 로그인 1회 = 새 패밀리. 수명은 여기서 정해지고 재발급해도 늘지 않는다 (절대 만료)
        String familyId = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(jwtProperties.refreshTokenTtl());

        return issueTokens(member, familyId, expiresAt);
    }

    public TokenPair reissue(String refreshToken) {
        RefreshTokenPayload payload = parseRefreshToken(refreshToken);

        if (!refreshTokenRepository.matches(payload.familyId(), refreshToken)) {
            // 서명은 유효한데 최신 토큰이 아니다 = 이미 쓴 토큰의 재사용. 누가 보냈는지 알 수 없으니 패밀리째 끊는다
            if (refreshTokenRepository.delete(payload.familyId())) {
                log.warn("Refresh token reuse detected: memberId={}, familyId={}", payload.memberId(), payload.familyId());
            }
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        Member member = memberRepository.findById(payload.memberId())
                                        .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        // 권한이 바뀌었을 수 있어 role은 토큰이 아니라 DB에서 다시 읽는다
        return issueTokens(member, payload.familyId(), payload.expiresAt());
    }

    // 토큰이 없거나 무효여도 예외 없이 끝낸다 (멱등) — 로그아웃의 목적은 "이 기기를 끊는 것"이고 이미 끊긴 상태도 성공이다
    public void logout(String refreshToken) {
        if (refreshToken == null) {
            return;
        }
        try {
            RefreshTokenPayload payload = jwtTokenProvider.parseRefreshToken(refreshToken);
            refreshTokenRepository.delete(payload.familyId());
        } catch (BusinessException e) {
            // 만료된 토큰은 Redis 키도 함께 만료됐고, 위조 토큰의 sid는 믿을 수 없으니 지울 것이 없다
        }
    }

    private TokenPair issueTokens(Member member, String familyId, Instant expiresAt) {
        String accessToken = jwtTokenProvider.createAccessToken(member.getId(), member.getRole());
        String refreshToken = jwtTokenProvider.createRefreshToken(member.getId(), familyId, expiresAt);
        refreshTokenRepository.save(familyId, refreshToken, expiresAt);

        return new TokenPair(accessToken, refreshToken, expiresAt);
    }

    // 만료·위조 등 파싱 실패 사유는 재발급 응답에서 구분하지 않는다
    private RefreshTokenPayload parseRefreshToken(String refreshToken) {
        if (refreshToken == null) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        try {
            return jwtTokenProvider.parseRefreshToken(refreshToken);
        } catch (BusinessException e) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN, e);
        }
    }

    // 트레이드오프: 존재하지 않는 이메일 요청에도 BCrypt 비용이 든다
    private void mitigateAgainstTimingAttack(String rawPassword) {
        passwordEncoder.matches(rawPassword, userNotFoundEncodedPassword);
    }
}
