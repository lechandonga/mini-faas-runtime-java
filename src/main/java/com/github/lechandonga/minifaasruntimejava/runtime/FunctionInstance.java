package com.github.lechandonga.minifaasruntimejava.runtime;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 一个函数版本对应的运行实例。负责冷启动（仅一次）、并发准入与实例复用。
 *
 * <p>生命周期语义：
 * <ul>
 *   <li>首次调用时才创建并初始化 handler，且只初始化一次；</li>
 *   <li>初始化失败进入 FAILED 状态，后续调用直接返回 INIT_ERROR，不会反复初始化；</li>
 *   <li>初始化成功后所有调用复用同一实例；</li>
 *   <li>同一实例同时执行的调用数不超过配置的并发上限，超出后排队等待，
 *       排队超时则明确拒绝（OVERLOADED）。</li>
 * </ul>
 */
public class FunctionInstance {

    private static final Logger log = LoggerFactory.getLogger(FunctionInstance.class);

    private enum State {COLD, READY, FAILED}

    private final FunctionKey key;
    private final Supplier<FunctionHandler> handlerFactory;
    private final Semaphore permits;

    private volatile State state = State.COLD;
    private volatile FunctionHandler handler;
    private volatile String initFailureMessage;

    public FunctionInstance(FunctionKey key, Supplier<FunctionHandler> handlerFactory, int maxConcurrency) {
        this.key = key;
        this.handlerFactory = handlerFactory;
        this.permits = new Semaphore(maxConcurrency, true);
    }

    public FunctionKey getKey() {
        return key;
    }

    /**
     * 冷启动：只在 COLD 状态下执行一次初始化。并发调用下只有一个线程执行初始化，
     * 其余线程等待初始化结果。
     */
    private FunctionHandler ensureInitialized() {
        if (state == State.READY) {
            return handler;
        }
        if (state == State.FAILED) {
            throw new FaasException(ErrorCode.INIT_ERROR,
                    "function " + key + " failed to initialize: " + initFailureMessage);
        }
        synchronized (this) {
            if (state == State.READY) {
                return handler;
            }
            if (state == State.FAILED) {
                throw new FaasException(ErrorCode.INIT_ERROR,
                        "function " + key + " failed to initialize: " + initFailureMessage);
            }
            try {
                log.info("cold start: initializing function instance {}", key);
                FunctionHandler created = handlerFactory.get();
                if (created == null) {
                    throw new IllegalStateException("handler factory returned null");
                }
                created.init();
                handler = created;
                state = State.READY;
                log.info("cold start: function instance {} initialized", key);
                return created;
            } catch (Exception e) {
                state = State.FAILED;
                initFailureMessage = e.getMessage() == null ? "initialization failed" : e.getMessage();
                log.error("cold start: function instance {} initialization failed: {}", key, initFailureMessage);
                throw new FaasException(ErrorCode.INIT_ERROR,
                        "function " + key + " failed to initialize: " + initFailureMessage, e);
            }
        }
    }

    /**
     * 准入：获取一个并发许可。并发已满时最多等待 queueTimeoutMs，
     * 拿不到许可则明确拒绝，不静默丢弃请求。
     */
    public FunctionHandler acquire(long queueTimeoutMs) {
        FunctionHandler h = ensureInitialized();
        try {
            if (!permits.tryAcquire(queueTimeoutMs, TimeUnit.MILLISECONDS)) {
                log.warn("function {} concurrency limit reached, request rejected after queue timeout", key);
                throw new FaasException(ErrorCode.OVERLOADED,
                        "function " + key + " is at max concurrency and queue wait timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FaasException(ErrorCode.INTERNAL_ERROR, "interrupted while waiting for execution slot", e);
        }
        return h;
    }

    public void release() {
        permits.release();
    }
}
