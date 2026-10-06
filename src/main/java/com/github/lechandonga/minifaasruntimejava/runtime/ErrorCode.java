package com.github.lechandonga.minifaasruntimejava.runtime;

/**
 * 平台对外的错误分类。每一类错误都有稳定的 code，调用方可以据此区分
 * 业务失败、参数错误、超时、初始化失败与平台内部错误。
 */
public enum ErrorCode {
    /** 函数或版本不存在 */
    FUNCTION_NOT_FOUND,
    /** 请求参数不合法（如 body 不是合法 JSON、缺少必需字段） */
    BAD_REQUEST,
    /** 函数业务代码抛出的异常 */
    BUSINESS_ERROR,
    /** 调用超时，执行已被中断 */
    TIMEOUT,
    /** 冷启动初始化失败（该版本实例已标记为不可用，不会反复初始化） */
    INIT_ERROR,
    /** 实例并发已满且排队超时，请求被明确拒绝（未静默丢弃） */
    OVERLOADED,
    /** 平台内部错误 */
    INTERNAL_ERROR
}
