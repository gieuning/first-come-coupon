package com.gieuning.coupon.global.response;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    private final T data;

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data);
    }
}
