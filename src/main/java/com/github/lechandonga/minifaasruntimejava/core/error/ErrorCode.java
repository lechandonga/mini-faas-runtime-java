package com.github.lechandonga.minifaasruntimejava.core.error;

/**
 * 调用错误的对外分类。对外响应只暴露这些类型与业务 message，
 * 不出现堆栈信息或内部类名。
 */
public enum ErrorCode {
    FUNCTION_NOT_FOUND,
    VERSION_NOT_FOUND,
    INVALID_ARGUMENT,
    BUSINESS_ERROR,
    INIT_FAILED,
    TIMEOUT,
    INTERNAL_ERROR
}
