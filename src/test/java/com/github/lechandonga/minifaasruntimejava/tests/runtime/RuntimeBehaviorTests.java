package com.github.lechandonga.minifaasruntimejava.tests.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.lechandonga.minifaasruntimejava.runtime.ErrorCode;
import com.github.lechandonga.minifaasruntimejava.runtime.FaasException;
import com.github.lechandonga.minifaasruntimejava.runtime.FunctionHandler;
import com.github.lechandonga.minifaasruntimejava.runtime.FunctionInvoker;
import com.github.lechandonga.minifaasruntimejava.runtime.FunctionRegistry;
import com.github.lechandonga.minifaasruntimejava.runtime.FunctionRequest;
import com.github.lechandonga.minifaasruntimejava.runtime.RegistrationException;
import com.github.lechandonga.minifaasruntimejava.runtime.RuntimeConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 运行时核心行为测试：冷启动与复用、版本路由、并发上限、超时中断、
 * 初始化失败与错误分类。
 */
class RuntimeBehaviorTests {

    private static final Logger log = LoggerFactory.getLogger(RuntimeBehaviorTests.class);

    private final List<FunctionInvoker> invokers = new ArrayList<>();

    private FunctionInvoker newInvoker(FunctionRegistry registry, RuntimeConfig config) {
        FunctionInvoker invoker = new FunctionInvoker(registry, config);
        invokers.add(invoker);
        return invoker;
    }

    private RuntimeConfig config(int maxConcurrency, long invokeTimeoutMs, long queueTimeoutMs) {
        return new RuntimeConfig(maxConcurrency, invokeTimeoutMs, queueTimeoutMs);
    }

    @AfterEach
    void tearDown() {
        invokers.forEach(FunctionInvoker::close);
    }

    @Test
    @DisplayName("注册规则：重复版本/缺失处理器/非法名字被明确拒绝且原因可区分")
    void registrationRules() {
        FunctionRegistry registry = new FunctionRegistry(config(1, 1000, 100));
        FunctionHandler handler = req -> "ok";

        registry.register("fn", "v1", () -> handler);

        RegistrationException dup = assertThrows(RegistrationException.class,
                () -> registry.register("fn", "v1", () -> handler));
        assertEquals(RegistrationException.Reason.DUPLICATE_VERSION, dup.getReason());
        log.info("用例[重复版本] 拒绝原因={}", dup.getReason());

        RegistrationException missing = assertThrows(RegistrationException.class,
                () -> registry.register("fn", "v2", null));
        assertEquals(RegistrationException.Reason.MISSING_HANDLER, missing.getReason());
        log.info("用例[处理器缺失] 拒绝原因={}", missing.getReason());

        RegistrationException badName = assertThrows(RegistrationException.class,
                () -> registry.register(" ", "v1", () -> handler));
        assertEquals(RegistrationException.Reason.INVALID_NAME, badName.getReason());
        log.info("用例[非法名字] 拒绝原因={}", badName.getReason());

        // 同名不同版本可以共存
        registry.register("fn", "v2", () -> handler);
    }

    @Test
    @DisplayName("版本路由：不指定版本时固定落到最新发布版本")
    void latestVersionRouting() {
        FunctionRegistry registry = new FunctionRegistry(config(1, 1000, 100));
        registry.register("fn", "v1", () -> req -> "from-v1");
        registry.register("fn", "v2", () -> req -> "from-v2");
        FunctionInvoker invoker = newInvoker(registry, config(1, 1000, 100));

        assertEquals("from-v2", invoker.invoke("fn", null, null));
        assertEquals("from-v1", invoker.invoke("fn", "v1", null));
        log.info("用例[版本路由] 默认=v2 指定=v1 均符合预期");
    }

    @Test
    @DisplayName("冷启动与复用：首次调用初始化一次，后续复用同一实例")
    void coldStartOnceAndReuse() {
        AtomicInteger initCount = new AtomicInteger();
        AtomicInteger instanceId = new AtomicInteger();
        FunctionRegistry registry = new FunctionRegistry(config(2, 1000, 100));
        registry.register("fn", "v1", () -> new FunctionHandler() {
            private final int id = instanceId.incrementAndGet();

            @Override
            public void init() {
                initCount.incrementAndGet();
            }

            @Override
            public Object handle(FunctionRequest request) {
                return "instance-" + id;
            }
        });
        FunctionInvoker invoker = newInvoker(registry, config(2, 1000, 100));

        Object first = invoker.invoke("fn", "v1", null);
        Object second = invoker.invoke("fn", "v1", null);
        Object third = invoker.invoke("fn", "v1", null);

        assertEquals(1, initCount.get(), "初始化必须只发生一次");
        assertEquals(first, second);
        assertEquals(second, third);
        log.info("用例[冷启动与复用] initCount={} 三次调用均返回 {}", initCount.get(), first);
    }

    @Test
    @DisplayName("初始化失败：与调用失败区分，且不会反复初始化")
    void initFailureIsSticky() {
        AtomicInteger initCount = new AtomicInteger();
        FunctionRegistry registry = new FunctionRegistry(config(1, 1000, 100));
        registry.register("bad", "v1", () -> new FunctionHandler() {
            @Override
            public void init() {
                initCount.incrementAndGet();
                throw new IllegalStateException("cannot load model");
            }

            @Override
            public Object handle(FunctionRequest request) {
                return "never";
            }
        });
        FunctionInvoker invoker = newInvoker(registry, config(1, 1000, 100));

        FaasException first = assertThrows(FaasException.class, () -> invoker.invoke("bad", "v1", null));
        FaasException second = assertThrows(FaasException.class, () -> invoker.invoke("bad", "v1", null));

        assertEquals(ErrorCode.INIT_ERROR, first.getCode());
        assertEquals(ErrorCode.INIT_ERROR, second.getCode());
        assertEquals(1, initCount.get(), "初始化失败后不得反复初始化");
        log.info("用例[初始化失败] initCount={} 两次调用均返回 INIT_ERROR", initCount.get());
    }

    @Test
    @DisplayName("并发上限：同一实例并发不超上限，超限请求排队后明确拒绝")
    void concurrencyLimitAndExplicitRejection() throws Exception {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxSeen = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        FunctionRegistry registry = new FunctionRegistry(config(2, 5000, 300));
        registry.register("slow", "v1", () -> request -> {
            int now = running.incrementAndGet();
            maxSeen.accumulateAndGet(now, Math::max);
            entered.countDown();
            try {
                release.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running.decrementAndGet();
            }
            return "done";
        });
        FunctionInvoker invoker = newInvoker(registry, config(2, 5000, 300));

        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<?> f1 = pool.submit(() -> invoker.invoke("slow", "v1", null));
            Future<?> f2 = pool.submit(() -> invoker.invoke("slow", "v1", null));
            assertTrue(entered.await(3, TimeUnit.SECONDS), "前两个请求应已进入执行");

            // 第三个请求：并发已满，排队 300ms 后应被明确拒绝为 OVERLOADED
            FaasException rejected = assertThrows(FaasException.class,
                    () -> invoker.invoke("slow", "v1", null));
            assertEquals(ErrorCode.OVERLOADED, rejected.getCode());
            log.info("用例[并发上限] 超限请求被拒绝 code={}", rejected.getCode());

            release.countDown();
            f1.get(3, TimeUnit.SECONDS);
            f2.get(3, TimeUnit.SECONDS);
            assertEquals(2, maxSeen.get(), "同一实例并发不得超过上限 2");
            log.info("用例[并发上限] 观测到的最大并发={} 上限=2", maxSeen.get());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("超时中断：超时返回 TIMEOUT，执行被中断，后续调用不受污染")
    void timeoutInterruptsAndDoesNotPollute() {
        AtomicInteger interruptedCount = new AtomicInteger();
        FunctionRegistry registry = new FunctionRegistry(config(1, 300, 100));
        registry.register("sleeper", "v1", () -> request -> {
            try {
                Thread.sleep(10_000);
                return "finished";
            } catch (InterruptedException e) {
                interruptedCount.incrementAndGet();
                Thread.currentThread().interrupt();
                return "interrupted";
            }
        });
        registry.register("quick", "v1", () -> request -> "quick-ok");
        FunctionInvoker invoker = newInvoker(registry, config(1, 300, 100));

        long start = System.currentTimeMillis();
        FaasException timeout = assertThrows(FaasException.class,
                () -> invoker.invoke("sleeper", "v1", null));
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(ErrorCode.TIMEOUT, timeout.getCode());
        assertTrue(elapsed < 3000, "超时后应尽快返回，实际耗时 " + elapsed + "ms");
        log.info("用例[超时中断] code={} 耗时={}ms", timeout.getCode(), elapsed);

        // 超时不得污染后续调用：同实例可继续服务，其他函数不受影响
        assertEquals("quick-ok", invoker.invoke("quick", "v1", null));
        Object again = assertThrows(FaasException.class,
                () -> invoker.invoke("sleeper", "v1", null)).getCode();
        assertEquals(ErrorCode.TIMEOUT, again);
        assertTrue(interruptedCount.get() >= 1, "超时必须中断执行中的 handler");
        log.info("用例[超时中断] handler 被中断次数={} 后续调用正常", interruptedCount.get());
    }

    @Test
    @DisplayName("错误分类：业务异常/函数不存在/超时映射为不同错误码")
    void errorClassification() {
        FunctionRegistry registry = new FunctionRegistry(config(1, 300, 100));
        registry.register("boom", "v1", () -> request -> {
            throw new IllegalArgumentException("invalid user input");
        });
        FunctionInvoker invoker = newInvoker(registry, config(1, 300, 100));

        FaasException business = assertThrows(FaasException.class,
                () -> invoker.invoke("boom", "v1", null));
        assertEquals(ErrorCode.BUSINESS_ERROR, business.getCode());
        assertEquals("invalid user input", business.getMessage());
        log.info("用例[错误分类] 业务异常 -> {}", business.getCode());

        FaasException notFound = assertThrows(FaasException.class,
                () -> invoker.invoke("ghost", null, null));
        assertEquals(ErrorCode.FUNCTION_NOT_FOUND, notFound.getCode());
        log.info("用例[错误分类] 函数不存在 -> {}", notFound.getCode());

        FaasException versionNotFound = assertThrows(FaasException.class,
                () -> invoker.invoke("boom", "v9", null));
        assertEquals(ErrorCode.FUNCTION_NOT_FOUND, versionNotFound.getCode());
        log.info("用例[错误分类] 版本不存在 -> {}", versionNotFound.getCode());
    }
}
