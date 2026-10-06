package com.github.lechandonga.minifaasruntimejava.runtime;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

/**
 * 声明式注册：标注在 FunctionHandler 实现类上即可作为函数版本发布，
 * 无需修改运行时核心。一个类即一个 (name, version)。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface FaasFunction {

    /** 函数名 */
    String name();

    /** 版本号 */
    String version();
}
