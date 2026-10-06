package com.github.lechandonga.minifaasruntimejava.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 FunctionHandler 实现类（同时为 Spring Bean）上，
 * 启动时自动注册到运行时，新增函数无需改动运行时核心。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FaasFunction {

    String name();

    int version();
}
