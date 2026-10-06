package com.github.lechandonga.minifaasruntimejava.tests;

import com.github.lechandonga.minifaasruntimejava.core.FunctionHandler;
import com.github.lechandonga.minifaasruntimejava.spring.FaasFunction;
import org.springframework.stereotype.Component;

/**
 * 测试专用慢函数：sleep 10s（可被中断），用于验证 HTTP 层超时映射。
 */
@Component
@FaasFunction(name = "slowhttp", version = 1)
public class SlowHttpFunction implements FunctionHandler {

    @Override
    public String handle(String input) throws InterruptedException {
        Thread.sleep(10_000);
        return "never";
    }
}
