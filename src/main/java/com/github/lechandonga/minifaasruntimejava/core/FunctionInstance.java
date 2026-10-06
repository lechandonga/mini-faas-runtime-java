package com.github.lechandonga.minifaasruntimejava.core;

import com.github.lechandonga.minifaasruntimejava.core.error.ErrorCode;
import com.github.lechandonga.minifaasruntimejava.core.error.FaasException;
import com.github.lechandonga.minifaasruntimejava.core.error.InvalidArgumentException;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单个函数版本的运行实例。
 *
 * 语义：
 * - 冷启动：第一次调用时执行 init() 且只执行一次，之后复用同一实例；
 * - 初始化失败：失败结果被缓存，后续调用直接返回 INIT_FAILED，不会重复初始化；
 * - 并发：同一实例同时执行的调用数不超过 maxConcurrency，超出的请求排队等待，
 *   不会被丢弃；
 * - 超时：超时后中断执行线程并返回 TIMEOUT；许可在任务真正结束时才释放，
 *   保证同一实例不会同时执行两次调用，超时也不影响后续调用。
 */
public class FunctionInstance {

    private final String name;
    private final int version;
    private final FunctionHandler handler;
    private final Semaphore permits;
    private final ExecutorService executor;

    private volatile boolean initialized;
    private volatile String initFailureMessage;

    public FunctionInstance(String name, int version, FunctionHandler handler,
                            int maxConcurrency, ExecutorService executor) {
        this.name = name;
        this.version = version;
        this.handler = handler;
        this.permits = new Semaphore(maxConcurrency);
        this.executor = executor;
    }

    private synchronized void ensureInitialized() {
        if (initFailureMessage != null) {
            throw new FaasException(ErrorCode.INIT_FAILED,
                    "function '" + name + "' version " + version + " failed to initialize: "
                            + initFailureMessage);
        }
        if (initialized) {
            return;
        }
        try {
            handler.init();
            initialized = true;
        } catch (Throwable t) {
            initFailureMessage = t.getMessage() == null ? "unknown init failure" : t.getMessage();
            throw new FaasException(ErrorCode.INIT_FAILED,
                    "function '" + name + "' version " + version + " failed to initialize: "
                            + initFailureMessage);
        }
    }

    public String invoke(String input, long timeoutMs) {
        ensureInitialized();
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FaasException(ErrorCode.INTERNAL_ERROR,
                    "interrupted while waiting for an execution slot");
        }
        AtomicBoolean started = new AtomicBoolean(false);
        Future<String> future = executor.submit(() -> {
            started.set(true);
            try {
                return handler.handle(input);
            } finally {
                permits.release();
            }
        });
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // 中断执行线程；若任务尚未开始则取消成功，需要手动归还许可
            if (future.cancel(true) && !started.get()) {
                permits.release();
            }
            throw new FaasException(ErrorCode.TIMEOUT,
                    "invocation of '" + name + "' version " + version
                            + " timed out after " + timeoutMs + "ms and was interrupted");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (future.cancel(true) && !started.get()) {
                permits.release();
            }
            throw new FaasException(ErrorCode.INTERNAL_ERROR,
                    "interrupted while waiting for the invocation result");
        } catch (ExecutionException e) {
            throw mapBusinessFailure(e.getCause());
        }
    }

    private FaasException mapBusinessFailure(Throwable cause) {
        if (cause instanceof InvalidArgumentException iae) {
            return new FaasException(ErrorCode.INVALID_ARGUMENT, iae.getMessage());
        }
        if (cause instanceof FaasException fe) {
            return fe;
        }
        String message = cause == null || cause.getMessage() == null
                ? "business handler failed" : cause.getMessage();
        return new FaasException(ErrorCode.BUSINESS_ERROR, message);
    }

    public boolean isInitialized() {
        return initialized;
    }
}
