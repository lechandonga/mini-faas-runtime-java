package com.github.lechandonga.minifaasruntimejava.core;

import com.github.lechandonga.minifaasruntimejava.core.error.ErrorCode;
import com.github.lechandonga.minifaasruntimejava.core.error.FaasException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 运行时入口：按名字与版本解析函数，懒创建每版本实例并发起调用。
 */
public class FaasRuntime {

    private final FunctionRegistry registry;
    private final int maxConcurrencyPerInstance;
    private final long invokeTimeoutMs;
    private final Map<FunctionHandler, FunctionInstance> instances = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    public FaasRuntime(FunctionRegistry registry, int maxConcurrencyPerInstance, long invokeTimeoutMs) {
        this.registry = registry;
        this.maxConcurrencyPerInstance = maxConcurrencyPerInstance;
        this.invokeTimeoutMs = invokeTimeoutMs;
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "faas-worker-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
        this.executor = Executors.newCachedThreadPool(factory);
    }

    public void register(String name, int version, FunctionHandler handler) {
        registry.register(name, version, handler);
    }

    /**
     * 调用函数。version 为 null 时固定落到最新发布版本。
     */
    public String invoke(String name, Integer version, String input) {
        if (!registry.contains(name)) {
            throw new FaasException(ErrorCode.FUNCTION_NOT_FOUND,
                    "function '" + name + "' is not registered");
        }
        Integer resolvedVersion = version == null
                ? registry.latestVersion(name).orElse(null)
                : version;
        FunctionHandler handler = registry.resolve(name, resolvedVersion)
                .orElseThrow(() -> new FaasException(ErrorCode.VERSION_NOT_FOUND,
                        "function '" + name + "' has no version " + resolvedVersion));
        FunctionInstance instance = instances.computeIfAbsent(handler,
                h -> new FunctionInstance(name, resolvedVersion, h,
                        maxConcurrencyPerInstance, executor));
        return instance.invoke(input, invokeTimeoutMs);
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
