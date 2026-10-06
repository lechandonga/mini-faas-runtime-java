package com.github.lechandonga.minifaasruntimejava.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.lechandonga.minifaasruntimejava.runtime.FunctionInvoker;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP 触发入口：POST /invoke/{name}[?version=vX]，body 为 JSON 参数。
 */
@RestController
public class InvokeController {

    private final FunctionInvoker invoker;

    public InvokeController(FunctionInvoker invoker) {
        this.invoker = invoker;
    }

    @PostMapping("/invoke/{name}")
    public Object invoke(@PathVariable String name,
                         @RequestParam(required = false) String version,
                         @RequestBody(required = false) JsonNode payload) {
        return invoker.invoke(name, version, payload);
    }
}
