package com.github.lechandonga.minifaasruntimejava.functions;

import com.github.lechandonga.minifaasruntimejava.core.FunctionHandler;
import com.github.lechandonga.minifaasruntimejava.core.error.InvalidArgumentException;
import com.github.lechandonga.minifaasruntimejava.spring.FaasFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 示例函数 echo 的 v2：回显并转大写。不指定版本时默认落到该最新版本。
 */
@Component
@FaasFunction(name = "echo", version = 2)
public class EchoFunctionV2 implements FunctionHandler {

    private static final Logger log = LoggerFactory.getLogger(EchoFunctionV2.class);

    @Override
    public void init() {
        log.info("echo v2 cold start init");
    }

    @Override
    public String handle(String input) {
        if (input == null || input.isBlank()) {
            throw new InvalidArgumentException("input must not be blank");
        }
        return "v2:" + input.toUpperCase();
    }
}
