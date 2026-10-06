package com.github.lechandonga.minifaasruntimejava.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.lechandonga.minifaasruntimejava.core.error.ErrorCode;
import com.github.lechandonga.minifaasruntimejava.core.error.FaasException;

/**
 * 统一对外响应。错误只包含分类与业务 message，不含堆栈与内部类名。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InvokeResponse {

    private boolean success;
    private String result;
    private ErrorBody error;

    public static InvokeResponse ok(String result) {
        InvokeResponse response = new InvokeResponse();
        response.success = true;
        response.result = result;
        return response;
    }

    public static InvokeResponse error(FaasException e) {
        InvokeResponse response = new InvokeResponse();
        response.success = false;
        response.error = new ErrorBody(e.getCode(), e.getMessage());
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getResult() {
        return result;
    }

    public ErrorBody getError() {
        return error;
    }

    public record ErrorBody(ErrorCode type, String message) {
    }
}
