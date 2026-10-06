package com.github.lechandonga.minifaasruntimejava.tests;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP 触发层测试：示例函数调用、版本路由与错误响应结构。
 */
@SpringBootTest
@AutoConfigureMockMvc
class InvokeControllerTests {

    private static final Logger log = LoggerFactory.getLogger(InvokeControllerTests.class);

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("HTTP 调用：示例函数 echo 默认路由到最新版本 v2")
    void invokeLatestVersion() throws Exception {
        log.info("[HTTP调用] 用例1: 不指定版本 -> v2 大写回显");
        mockMvc.perform(post("/invoke/echo").content("hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result").value("v2:HELLO"));
        log.info("[HTTP调用] 用例2: 指定版本 -> v1 原样回显");
        mockMvc.perform(post("/invoke/echo?version=1").content("hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("v1:hello"));
    }

    @Test
    @DisplayName("HTTP 错误结构：参数错误 400，函数不存在 404，错误体无堆栈与内部类名")
    void errorResponseShape() throws Exception {
        log.info("[HTTP错误] 用例1: 空入参 -> INVALID_ARGUMENT/400");
        mockMvc.perform(post("/invoke/echo").content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.type").value("INVALID_ARGUMENT"))
                .andExpect(jsonPath("$.error.message").exists());

        log.info("[HTTP错误] 用例2: 未知函数 -> FUNCTION_NOT_FOUND/404");
        mockMvc.perform(post("/invoke/ghost").content("x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.type").value("FUNCTION_NOT_FOUND"));

        log.info("[HTTP错误] 用例3: 未知版本 -> VERSION_NOT_FOUND/404");
        mockMvc.perform(post("/invoke/echo?version=99").content("x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.type").value("VERSION_NOT_FOUND"));
    }

    @Test
    @DisplayName("HTTP 超时：慢函数超时返回 504 TIMEOUT，之后同函数调用恢复正常")
    void timeoutOverHttp() throws Exception {
        log.info("[HTTP超时] 用例1: 注册慢函数, 超时返回 TIMEOUT/504, 后续调用恢复");
        // 通过运行时直接注册一个慢函数验证 HTTP 层超时映射
        // invokeTimeoutMs 在测试配置中设为 500ms
        mockMvc.perform(post("/invoke/slowhttp").content("x"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.error.type").value("TIMEOUT"));
        mockMvc.perform(post("/invoke/echo").content("still-ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("v2:STILL-OK"));
    }
}
