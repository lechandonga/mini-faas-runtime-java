package com.github.lechandonga.minifaasruntimejava.core.error;

/**
 * 运行时统一异常，携带对外错误分类。
 */
public class FaasException extends RuntimeException {

    private final ErrorCode code;

    public FaasException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public FaasException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public ErrorCode getCode() {
        return code;
    }
}
