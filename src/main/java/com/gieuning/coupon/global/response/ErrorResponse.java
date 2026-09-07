package com.gieuning.coupon.global.response;

import com.gieuning.coupon.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;

import java.time.Instant;
import java.util.List;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ErrorResponse {

    private final String code;
    private final String message;
    private final List<FieldError> fieldErrors;
    private final Instant timestamp;

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), List.of(), Instant.now());
    }

    public static ErrorResponse of(ErrorCode errorCode, BindingResult bindingResult) {
        List<FieldError> fieldErrors = bindingResult.getAllErrors().stream()
                .map(error -> {
                    String field = error instanceof org.springframework.validation.FieldError fieldError
                            ? fieldError.getField()
                            : error.getObjectName();
                    return new FieldError(field, error.getDefaultMessage());
                })
                .toList();
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), fieldErrors, Instant.now());
    }

    @Getter
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    public static class FieldError {

        private final String field;
        private final String reason;
    }
}
