package com.github.lechandonga.minifaasruntimejava.runtime;

/**
 * 平台内部使用的异常，携带错误分类。message 只允许包含可对外暴露的信息，
 * 不允许携带堆栈或内部类名。
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
