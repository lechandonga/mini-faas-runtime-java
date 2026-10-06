package com.github.lechandonga.minifaasruntimejava.runtime;

/**
 * 对外统一错误结构。不包含堆栈信息与内部类名。
 */
public record ErrorResponse(String requestId, ErrorCode code, String message) {
}
