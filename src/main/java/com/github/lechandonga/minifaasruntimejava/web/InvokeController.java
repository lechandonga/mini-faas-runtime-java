package com.github.lechandonga.minifaasruntimejava.web;

import com.github.lechandonga.minifaasruntimejava.core.FaasRuntime;
import com.github.lechandonga.minifaasruntimejava.core.error.ErrorCode;
import com.github.lechandonga.minifaasruntimejava.core.error.FaasException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP 触发入口：POST /invoke/{name}?version=1，body 为函数入参。
 */
@RestController
public class InvokeController {

    private static final Logger log = LoggerFactory.getLogger(InvokeController.class);

    private final FaasRuntime runtime;

    public InvokeController(FaasRuntime runtime) {
        this.runtime = runtime;
    }

    @PostMapping("/invoke/{name}")
    public ResponseEntity<InvokeResponse> invoke(@PathVariable String name,
                                                 @RequestParam(required = false) Integer version,
                                                 @RequestBody(required = false) String body) {
        try {
            String result = runtime.invoke(name, version, body);
            return ResponseEntity.ok(InvokeResponse.ok(result));
        } catch (FaasException e) {
            log.warn("invocation of '{}' version {} failed: {} - {}",
                    name, version, e.getCode(), e.getMessage());
            return ResponseEntity.status(toHttpStatus(e.getCode()))
                    .body(InvokeResponse.error(e));
        } catch (Exception e) {
            log.error("unexpected platform error invoking '{}'", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(InvokeResponse.error(new FaasException(ErrorCode.INTERNAL_ERROR,
                            "internal platform error")));
        }
    }

    private HttpStatus toHttpStatus(ErrorCode code) {
        return switch (code) {
            case FUNCTION_NOT_FOUND, VERSION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case INIT_FAILED, BUSINESS_ERROR, INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
