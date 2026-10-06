package com.github.lechandonga.minifaasruntimejava.core.error;

/**
 * 函数业务代码抛出此异常时，会被映射为 INVALID_ARGUMENT（参数错误），
 * 与业务失败 BUSINESS_ERROR 区分。
 */
public class InvalidArgumentException extends RuntimeException {

    public InvalidArgumentException(String message) {
        super(message);
    }
}
