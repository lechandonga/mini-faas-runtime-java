package com.github.lechandonga.minifaasruntimejava.example;

import java.util.Map;

import com.github.lechandonga.minifaasruntimejava.runtime.FaasFunction;
import com.github.lechandonga.minifaasruntimejava.runtime.FunctionHandler;
import com.github.lechandonga.minifaasruntimejava.runtime.FunctionRequest;

/**
 * 示例函数：回显入参，用于本地调用验证。
 * POST /invoke/echo  {"message": "hello"}
 */
@FaasFunction(name = "echo", version = "v1")
public class EchoFunction implements FunctionHandler {

    @Override
    public Object handle(FunctionRequest request) {
        return Map.of(
                "requestId", request.requestId(),
                "echo", request.payload() == null ? "null" : request.payload());
    }
}
