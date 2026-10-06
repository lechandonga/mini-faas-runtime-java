package com.github.lechandonga.minifaasruntimejava.functions;

import com.github.lechandonga.minifaasruntimejava.core.FunctionHandler;
import com.github.lechandonga.minifaasruntimejava.core.error.InvalidArgumentException;
import com.github.lechandonga.minifaasruntimejava.spring.FaasFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 示例函数 echo 的 v1：原样回显。
 */
@Component
@FaasFunction(name = "echo", version = 1)
public class EchoFunctionV1 implements FunctionHandler {

    private static final Logger log = LoggerFactory.getLogger(EchoFunctionV1.class);

    @Override
    public void init() {
        log.info("echo v1 cold start init");
    }

    @Override
    public String handle(String input) {
        if (input == null || input.isBlank()) {
            throw new InvalidArgumentException("input must not be blank");
        }
        return "v1:" + input;
    }
}
