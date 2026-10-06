package com.github.lechandonga.minifaasruntimejava.runtime;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 调用入口：路由到目标版本实例，执行并发准入、超时中断与错误分类。
 */
public class FunctionInvoker implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(FunctionInvoker.class);

    private final FunctionRegistry registry;
    private final RuntimeConfig config;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public FunctionInvoker(FunctionRegistry registry, RuntimeConfig config) {
        this.registry = registry;
        this.config = config;
    }

    /**
     * 同步调用函数。
     *
     * @return 业务返回值（可序列化为 JSON）
     * @throws FaasException 携带错误分类，调用方据此映射对外错误结构
     */
    public Object invoke(String name, String version, JsonNode payload) {
        String requestId = UUID.randomUUID().toString();
        FunctionInstance instance = registry.resolve(name, version);
        FunctionHandler handler = instance.acquire(config.queueTimeoutMs());
        try {
            log.info("invoke start requestId={} function={}", requestId, instance.getKey());
            Future<Object> future =
                    executor.submit(() -> handler.handle(new FunctionRequest(requestId, payload)));
            Object result;
            try {
                result = future.get(config.invokeTimeoutMs(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException te) {
                future.cancel(true); // 中断执行中的 handler 线程
                throw te;
            }
            log.info("invoke success requestId={} function={}", requestId, instance.getKey());
            return result;
        } catch (TimeoutException e) {
            // 中断执行中的 handler；许可在 finally 释放，实例可继续服务后续调用
            log.warn("invoke timeout requestId={} function={} after {}ms",
                    requestId, instance.getKey(), config.invokeTimeoutMs());
            throw new FaasException(ErrorCode.TIMEOUT,
                    "function " + instance.getKey() + " timed out after " + config.invokeTimeoutMs() + "ms", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            String detail = cause.getMessage() == null ? "function execution failed" : cause.getMessage();
            log.warn("invoke business error requestId={} function={}: {}", requestId, instance.getKey(), detail);
            throw new FaasException(ErrorCode.BUSINESS_ERROR, detail, cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FaasException(ErrorCode.INTERNAL_ERROR, "interrupted while invoking function", e);
        } catch (FaasException e) {
            throw e;
        } catch (Exception e) {
            log.error("invoke internal error requestId={} function={}", requestId, instance.getKey(), e);
            throw new FaasException(ErrorCode.INTERNAL_ERROR, "internal runtime error", e);
        } finally {
            instance.release();
        }
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
