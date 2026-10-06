package com.github.lechandonga.minifaasruntimejava.runtime;

/**
 * 函数处理器。每个发布的版本对应一个独立的 handler 实例，
 * 实例由运行时在冷启动时创建并初始化一次，之后被复用。
 */
public interface FunctionHandler {

    /**
     * 冷启动时调用一次，用于加载资源。抛出异常视为初始化失败，
     * 该版本实例将被标记为不可用，后续调用直接返回 INIT_ERROR。
     */
    default void init() throws Exception {
    }

    /**
     * 处理一次调用。抛出的异常归类为业务错误（BUSINESS_ERROR）。
     */
    Object handle(FunctionRequest request) throws Exception;
}
