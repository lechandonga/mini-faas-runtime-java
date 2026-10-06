package com.github.lechandonga.minifaasruntimejava.tests;

import com.github.lechandonga.minifaasruntimejava.core.FaasRuntime;
import com.github.lechandonga.minifaasruntimejava.core.FunctionHandler;
import com.github.lechandonga.minifaasruntimejava.core.FunctionRegistry;
import com.github.lechandonga.minifaasruntimejava.core.error.ErrorCode;
import com.github.lechandonga.minifaasruntimejava.core.error.FaasException;
import com.github.lechandonga.minifaasruntimejava.core.error.InvalidArgumentException;
import com.github.lechandonga.minifaasruntimejava.core.error.RegistrationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 运行时核心行为测试：冷启动与复用、并发上限、超时中断、初始化失败、错误映射、注册规则。
 */
class FaasRuntimeTests {

    private static final Logger log = LoggerFactory.getLogger(FaasRuntimeTests.class);

    private FaasRuntime runtime;

    private FaasRuntime newRuntime(int maxConcurrency, long timeoutMs) {
        runtime = new FaasRuntime(new FunctionRegistry(), maxConcurrency, timeoutMs);
        return runtime;
    }

    @AfterEach
    void tearDown() {
        if (runtime != null) {
            runtime.shutdown();
        }
    }

    @Test
    @DisplayName("冷启动与复用：首次调用初始化一次，后续复用同一实例")
    void coldStartAndReuse() {
        log.info("[冷启动与复用] 用例1: 首次调用前不初始化，两次调用 init 只执行一次且结果复用");
        FaasRuntime rt = newRuntime(2, 2000);
        AtomicInteger initCount = new AtomicInteger();
        AtomicInteger handleCount = new AtomicInteger();
        rt.register("counter", 1, new FunctionHandler() {
            @Override
            public void init() {
                initCount.incrementAndGet();
            }

            @Override
            public String handle(String input) {
                return "call-" + handleCount.incrementAndGet();
            }
        });
        assertEquals(0, initCount.get(), "注册后、首次调用前不应初始化");
        assertEquals("call-1", rt.invoke("counter", null, "x"));
        assertEquals("call-2", rt.invoke("counter", null, "x"));
        assertEquals(1, initCount.get(), "init 必须只执行一次");
        log.info("[冷启动与复用] 通过: initCount={}, handleCount={}", initCount.get(), handleCount.get());
    }

    @Test
    @DisplayName("版本路由：不指定版本固定落到最新发布版本")
    void latestVersionRouting() {
        log.info("[版本路由] 用例1: 多版本共存，默认路由到最新版本，指定版本各归各");
        FaasRuntime rt = newRuntime(2, 2000);
        rt.register("echo", 1, input -> "v1");
        rt.register("echo", 3, input -> "v3");
        rt.register("echo", 2, input -> "v2");
        assertEquals("v3", rt.invoke("echo", null, "x"), "默认应落到最大版本号");
        assertEquals("v1", rt.invoke("echo", 1, "x"));
        assertEquals("v2", rt.invoke("echo", 2, "x"));
        log.info("[版本路由] 通过: 默认=v3, 指定版本正确");
    }

    @Test
    @DisplayName("注册拒绝：重复版本、缺失处理器、非法名字分别给出可区分原因")
    void registrationRejection() {
        log.info("[注册拒绝] 用例1: 重复版本; 用例2: 处理器缺失; 用例3: 非法名字; 用例4: 非法版本号");
        FaasRuntime rt = newRuntime(2, 2000);
        rt.register("f", 1, input -> "ok");

        RegistrationException dup = assertThrows(RegistrationException.class,
                () -> rt.register("f", 1, input -> "override"));
        assertEquals(RegistrationException.Reason.DUPLICATE_VERSION, dup.getReason());
        assertEquals("ok", rt.invoke("f", 1, "x"), "重复注册不得覆盖已有版本");

        RegistrationException noHandler = assertThrows(RegistrationException.class,
                () -> rt.register("g", 1, null));
        assertEquals(RegistrationException.Reason.HANDLER_MISSING, noHandler.getReason());

        RegistrationException badName = assertThrows(RegistrationException.class,
                () -> rt.register("  ", 1, input -> "x"));
        assertEquals(RegistrationException.Reason.INVALID_NAME, badName.getReason());

        RegistrationException badVersion = assertThrows(RegistrationException.class,
                () -> rt.register("h", 0, input -> "x"));
        assertEquals(RegistrationException.Reason.INVALID_VERSION, badVersion.getReason());
        log.info("[注册拒绝] 通过: 4 种拒绝原因均可区分，已有版本未被覆盖");
    }

    @Test
    @DisplayName("并发上限：同一实例并发执行不超过上限，请求排队不丢失")
    void concurrencyLimit() throws Exception {
        log.info("[并发上限] 用例1: 上限2, 6个并发请求全部成功且峰值并发不超过2");
        FaasRuntime rt = newRuntime(2, 10_000);
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(2);
        rt.register("slow", 1, input -> {
            int now = inFlight.incrementAndGet();
            maxObserved.accumulateAndGet(now, Math::max);
            entered.countDown();
            Thread.sleep(150);
            inFlight.decrementAndGet();
            return "done-" + input;
        });

        int total = 6;
        ExecutorService pool = Executors.newFixedThreadPool(total);
        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            int idx = i;
            futures.add(pool.submit(() -> rt.invoke("slow", null, String.valueOf(idx))));
        }
        assertTrue(entered.await(2, TimeUnit.SECONDS), "前两个请求应先进入执行");
        for (int i = 0; i < total; i++) {
            assertEquals("done-" + i, futures.get(i).get(5, TimeUnit.SECONDS),
                    "超出上限的请求应排队执行而不是被丢弃");
        }
        pool.shutdown();
        assertTrue(maxObserved.get() <= 2, "峰值并发不得超过上限, observed=" + maxObserved.get());
        assertEquals(2, maxObserved.get(), "应确实打满过并发上限");
        log.info("[并发上限] 通过: {} 个请求全部完成, 峰值并发={}", total, maxObserved.get());
    }

    @Test
    @DisplayName("超时中断：超时返回 TIMEOUT 并中断执行，后续调用不受污染")
    void timeoutInterruptsAndDoesNotPollute() {
        log.info("[超时中断] 用例1: 慢函数超时返回 TIMEOUT; 用例2: 超时后同实例后续调用正常");
        FaasRuntime rt = newRuntime(1, 300);
        AtomicInteger interrupted = new AtomicInteger();
        rt.register("sleeper", 1, input -> {
            if ("hang".equals(input)) {
                try {
                    Thread.sleep(30_000);
                } catch (InterruptedException e) {
                    interrupted.incrementAndGet();
                    throw e;
                }
            }
            return "ok-" + input;
        });

        long start = System.currentTimeMillis();
        FaasException timeout = assertThrows(FaasException.class,
                () -> rt.invoke("sleeper", null, "hang"));
        long elapsed = System.currentTimeMillis() - start;
        assertEquals(ErrorCode.TIMEOUT, timeout.getCode());
        assertTrue(elapsed < 5_000, "超时必须快速返回而不是等业务执行完, elapsed=" + elapsed);
        log.info("[超时中断] 超时用例通过: elapsed={}ms, message={}", elapsed, timeout.getMessage());

        // 等待被中断的 worker 退出并释放许可，验证超时未污染后续调用
        awaitTrue(() -> interrupted.get() == 1, 3000);
        assertEquals("ok-ping", rt.invoke("sleeper", null, "ping"),
                "超时后的后续调用必须正常");
        assertEquals(1, interrupted.get(), "超时必须中断执行线程");
        log.info("[超时中断] 通过: 执行线程被中断, 后续调用结果正常");
    }

    @Test
    @DisplayName("初始化失败：与调用失败区分，失败被缓存且不会反复初始化")
    void initFailureIsCached() {
        log.info("[初始化失败] 用例1: init 抛异常返回 INIT_FAILED; 用例2: 后续调用不重复初始化");
        FaasRuntime rt = newRuntime(2, 2000);
        AtomicInteger initAttempts = new AtomicInteger();
        rt.register("fragile", 1, new FunctionHandler() {
            @Override
            public void init() {
                initAttempts.incrementAndGet();
                throw new IllegalStateException("cannot connect to resource");
            }

            @Override
            public String handle(String input) {
                return "never";
            }
        });
        FaasException first = assertThrows(FaasException.class, () -> rt.invoke("fragile", null, "x"));
        FaasException second = assertThrows(FaasException.class, () -> rt.invoke("fragile", null, "x"));
        assertEquals(ErrorCode.INIT_FAILED, first.getCode());
        assertEquals(ErrorCode.INIT_FAILED, second.getCode());
        assertTrue(first.getMessage().contains("cannot connect to resource"));
        assertEquals(1, initAttempts.get(), "初始化失败后不得被后续调用反复初始化");
        log.info("[初始化失败] 通过: initAttempts={}, 两次调用均返回 INIT_FAILED", initAttempts.get());
    }

    @Test
    @DisplayName("错误映射：业务异常、参数错误、函数不存在、版本不存在映射为不同错误")
    void errorClassification() {
        log.info("[错误映射] 用例1: 业务异常->BUSINESS_ERROR; 用例2: 参数错误->INVALID_ARGUMENT; "
                + "用例3: 函数不存在->FUNCTION_NOT_FOUND; 用例4: 版本不存在->VERSION_NOT_FOUND");
        FaasRuntime rt = newRuntime(2, 2000);
        rt.register("biz", 1, input -> {
            if ("bad".equals(input)) {
                throw new InvalidArgumentException("input 'bad' is not allowed");
            }
            if ("boom".equals(input)) {
                throw new IllegalStateException("order 123 balance not enough");
            }
            return "fine";
        });

        FaasException business = assertThrows(FaasException.class, () -> rt.invoke("biz", null, "boom"));
        assertEquals(ErrorCode.BUSINESS_ERROR, business.getCode());
        assertEquals("order 123 balance not enough", business.getMessage());
        assertFalse(business.getMessage().contains("IllegalStateException"),
                "错误信息不得包含内部类名");

        FaasException invalid = assertThrows(FaasException.class, () -> rt.invoke("biz", null, "bad"));
        assertEquals(ErrorCode.INVALID_ARGUMENT, invalid.getCode());

        FaasException noFunc = assertThrows(FaasException.class, () -> rt.invoke("ghost", null, "x"));
        assertEquals(ErrorCode.FUNCTION_NOT_FOUND, noFunc.getCode());

        FaasException noVersion = assertThrows(FaasException.class, () -> rt.invoke("biz", 9, "x"));
        assertEquals(ErrorCode.VERSION_NOT_FOUND, noVersion.getCode());

        assertEquals("fine", rt.invoke("biz", null, "ok"), "错误调用不得影响后续正常调用");
        log.info("[错误映射] 通过: 4 类错误映射正确且不含内部类名");
    }

    private void awaitTrue(Check check, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (check.pass()) {
                return;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private interface Check {
        boolean pass();
    }
}
