package com.github.lechandonga.minifaasruntimejava.tests;

import java.util.Map;

import com.github.lechandonga.minifaasruntimejava.runtime.FaasFunction;
import com.github.lechandonga.minifaasruntimejava.runtime.FunctionHandler;
import com.github.lechandonga.minifaasruntimejava.runtime.FunctionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTTP 触发端到端测试：示例函数调用、错误结构对外不泄露堆栈与内部类名。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"faas.invoke-timeout-ms=500"})
class InvokeApiTests {

    private static final Logger log = LoggerFactory.getLogger(InvokeApiTests.class);

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("示例函数：POST /invoke/echo 正常回显")
    void echoFunction() {
        ResponseEntity<Map> resp = rest.postForEntity("/invoke/echo", Map.of("message", "hello"), Map.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        log.info("用例[示例函数] 响应={}", resp.getBody());
        assertTrue(String.valueOf(resp.getBody()).contains("hello"));
    }

    @Test
    @DisplayName("错误映射：函数不存在返回 FUNCTION_NOT_FOUND，无堆栈与内部类名")
    void notFoundErrorShape() {
        ResponseEntity<Map> resp = rest.postForEntity("/invoke/ghost", Map.of(), Map.class);
        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertEquals("FUNCTION_NOT_FOUND", resp.getBody().get("code"));
        assertNoInternals(resp.getBody());
        log.info("用例[错误映射-不存在] 响应={}", resp.getBody());
    }

    @Test
    @DisplayName("错误映射：业务异常返回 BUSINESS_ERROR，无堆栈与内部类名")
    void businessErrorShape() {
        ResponseEntity<Map> resp = rest.postForEntity("/invoke/api-boom", Map.of(), Map.class);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resp.getStatusCode());
        assertEquals("BUSINESS_ERROR", resp.getBody().get("code"));
        assertEquals("user level message", resp.getBody().get("message"));
        assertNoInternals(resp.getBody());
        log.info("用例[错误映射-业务异常] 响应={}", resp.getBody());
    }

    @Test
    @DisplayName("错误映射：超时返回 TIMEOUT(504)，无堆栈与内部类名")
    void timeoutErrorShape() {
        ResponseEntity<Map> resp = rest.postForEntity("/invoke/api-slow", Map.of(), Map.class);
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, resp.getStatusCode());
        assertEquals("TIMEOUT", resp.getBody().get("code"));
        assertNoInternals(resp.getBody());
        log.info("用例[错误映射-超时] 响应={}", resp.getBody());
    }

    @Test
    @DisplayName("错误映射：非法 JSON 返回 BAD_REQUEST")
    void badRequestShape() {
        ResponseEntity<Map> resp = rest.postForEntity("/invoke/echo",
                new org.springframework.http.HttpEntity<>("not-json",
                        org.springframework.http.HttpHeaders.readOnlyHttpHeaders(
                                new org.springframework.http.HttpHeaders() {{
                                    setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                                }})),
                Map.class);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("BAD_REQUEST", resp.getBody().get("code"));
        log.info("用例[错误映射-参数错误] 响应={}", resp.getBody());
    }

    private void assertNoInternals(Map body) {
        String text = String.valueOf(body);
        assertFalse(text.contains("Exception"), "响应不得包含异常类名: " + text);
        assertFalse(text.contains("com.github"), "响应不得包含内部包名: " + text);
        assertFalse(text.contains("\tat "), "响应不得包含堆栈: " + text);
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class TestFunctions {
        @org.springframework.context.annotation.Bean
        BoomFunction boomFunction() {
            return new BoomFunction();
        }

        @org.springframework.context.annotation.Bean
        SlowFunction slowFunction() {
            return new SlowFunction();
        }
    }

    /** 测试用函数：抛出业务异常 */
    @FaasFunction(name = "api-boom", version = "v1")
    static class BoomFunction implements FunctionHandler {
        @Override
        public Object handle(FunctionRequest request) {
            throw new IllegalStateException("user level message");
        }
    }

    /** 测试用函数：长时间阻塞，用于触发超时 */
    @FaasFunction(name = "api-slow", version = "v1")
    static class SlowFunction implements FunctionHandler {
        @Override
        public Object handle(FunctionRequest request) throws Exception {
            Thread.sleep(30_000);
            return "done";
        }
    }
}
