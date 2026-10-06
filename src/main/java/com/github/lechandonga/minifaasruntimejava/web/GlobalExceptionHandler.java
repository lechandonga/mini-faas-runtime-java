package com.github.lechandonga.minifaasruntimejava.web;

import java.util.UUID;

import com.github.lechandonga.minifaasruntimejava.runtime.ErrorCode;
import com.github.lechandonga.minifaasruntimejava.runtime.ErrorResponse;
import com.github.lechandonga.minifaasruntimejava.runtime.FaasException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 把内部异常映射为统一错误结构。对外响应不包含堆栈与内部类名。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(FaasException.class)
    public ResponseEntity<ErrorResponse> handleFaas(FaasException e) {
        return ResponseEntity.status(statusOf(e.getCode()))
                .body(new ErrorResponse(UUID.randomUUID().toString(), e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleBadBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(UUID.randomUUID().toString(), ErrorCode.BAD_REQUEST,
                        "request body is not valid JSON"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("unexpected error: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(UUID.randomUUID().toString(), ErrorCode.INTERNAL_ERROR,
                        "internal runtime error"));
    }

    private static HttpStatus statusOf(ErrorCode code) {
        return switch (code) {
            case FUNCTION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case OVERLOADED -> HttpStatus.TOO_MANY_REQUESTS;
            case BUSINESS_ERROR, INIT_ERROR, INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
