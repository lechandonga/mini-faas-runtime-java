package com.github.lechandonga.minifaasruntimejava.runtime;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 一次函数调用的入参。
 *
 * @param requestId 平台生成的请求 ID，用于日志与错误追踪
 * @param payload   调用方提交的 JSON 参数体，可能为 null
 */
public record FunctionRequest(String requestId, JsonNode payload) {
}
