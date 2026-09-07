package com.gieuning.coupon.global.exception;

import com.gieuning.coupon.global.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.info("BusinessException: code={}, message={}", errorCode.getCode(), errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatus())
                             .body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        ErrorCode errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR;

        log.error("Unhandled exception", e);
        return ResponseEntity.status(errorCode.getStatus())
                             .body(ErrorResponse.of(errorCode));
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        ErrorCode errorCode = CommonErrorCode.INVALID_REQUEST;
        ErrorResponse body = ErrorResponse.of(errorCode, e.getBindingResult());

        String detail = body.getFieldErrors().stream()
                            .map(error -> error.getField() + "=" + error.getReason())
                            .collect(Collectors.joining(", "));
        log.info("Validation failed: {}", detail);

        return handleExceptionInternal(e, body, headers, errorCode.getStatus(), request);
    }

    // 부모(ResponseEntityExceptionHandler)가 처리하는 모든 스프링 MVC 예외가 마지막에 거치는 지점.
    // 기본 바디(ProblemDetail)를 우리 ErrorResponse로 교체해 응답 형태를 통일한다.
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception e, @Nullable Object body,
                                                                       HttpHeaders headers, HttpStatusCode statusCode,
                                                                       WebRequest request) {
        if (!(body instanceof ErrorResponse)) {
            ErrorCode errorCode = resolveErrorCode(statusCode);
            body = ErrorResponse.of(errorCode);

            if (statusCode.is5xxServerError()) {
                log.error("Spring MVC exception: status={}", statusCode.value(), e);
            } else {
                log.info("Spring MVC exception: status={}, type={}, message={}",
                        statusCode.value(), e.getClass().getSimpleName(), e.getMessage());
            }
        }
        return super.handleExceptionInternal(e, body, headers, statusCode, request);
    }

    // 프레임워크 오류에는 개별 코드를 부여하지 않는다. 커스텀 코드는 클라이언트가
    // 상태 코드보다 세밀하게 분기해야 하는 비즈니스 오류에만 사용하고,
    // 404/405/415 등은 HTTP 상태 코드 자체가 의미를 전달한다.
    private ErrorCode resolveErrorCode(HttpStatusCode statusCode) {
        return statusCode.is4xxClientError()
                ? CommonErrorCode.INVALID_REQUEST
                : CommonErrorCode.INTERNAL_SERVER_ERROR;
    }
}
