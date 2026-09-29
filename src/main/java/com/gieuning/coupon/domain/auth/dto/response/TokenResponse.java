package com.gieuning.coupon.domain.auth.dto.response;


import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class TokenResponse {

    private final String accessToken;

    public static TokenResponse of(String accessToken) {
        return new TokenResponse(accessToken);
    }


}
