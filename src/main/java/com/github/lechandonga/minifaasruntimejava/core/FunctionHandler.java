package com.github.lechandonga.minifaasruntimejava.core;

/**
 * 进程内函数处理器。新增函数只需实现该接口并注册，无需改动运行时核心。
 *
 * 生命周期：实例在注册时创建，init() 延迟到该版本第一次被调用时执行且只执行一次；
 * 之后的调用复用同一实例。
 */
public interface FunctionHandler {

    /**
     * 冷启动初始化，在该版本第一次被调用时执行且仅执行一次。
     * 抛异常则视为初始化失败，后续调用直接返回 INIT_FAILED，不会重复初始化。
     */
    default void init() throws Exception {
    }

    /**
     * 处理一次调用。抛
     * {@link com.github.lechandonga.minifaasruntimejava.core.error.InvalidArgumentException}
     * 映射为参数错误；抛其他异常映射为业务失败。
     */
    String handle(String input) throws Exception;
}
