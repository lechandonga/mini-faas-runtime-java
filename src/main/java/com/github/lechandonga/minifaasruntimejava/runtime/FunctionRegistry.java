package com.github.lechandonga.minifaasruntimejava.runtime;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 函数注册表。支持同一函数的多版本共存；不指定版本时固定路由到最新发布版本。
 * 重复版本、缺失处理器、非法名字都会被明确拒绝，绝不静默覆盖。
 */
public class FunctionRegistry {

    private static final Logger log = LoggerFactory.getLogger(FunctionRegistry.class);

    private final Map<FunctionKey, Supplier<FunctionHandler>> factories = new ConcurrentHashMap<>();
    private final RuntimeConfig config;

    public FunctionRegistry(RuntimeConfig config) {
        this.config = config;
    }

    /**
     * 注册函数的一个版本。
     *
     * @throws RegistrationException 名字非法 / 版本非法 / 处理器缺失 / 版本重复
     */
    public void register(String name, String version, Supplier<FunctionHandler> handlerFactory) {
        if (name == null || name.isBlank()) {
            throw new RegistrationException(RegistrationException.Reason.INVALID_NAME,
                    "function name must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new RegistrationException(RegistrationException.Reason.INVALID_VERSION,
                    "function version must not be blank");
        }
        if (handlerFactory == null) {
            throw new RegistrationException(RegistrationException.Reason.MISSING_HANDLER,
                    "handler factory is required for function " + name + ":" + version);
        }
        FunctionKey key = new FunctionKey(name, version);
        Supplier<FunctionHandler> previous = factories.putIfAbsent(key, handlerFactory);
        if (previous != null) {
            throw new RegistrationException(RegistrationException.Reason.DUPLICATE_VERSION,
                    "function " + key + " is already registered, refusing to overwrite");
        }
        log.info("registered function {}", key);
    }

    /**
     * 解析调用目标：指定版本时精确匹配，否则固定落到最新发布版本。
     */
    public FunctionInstance resolve(String name, String version) {
        FunctionKey key;
        if (version != null && !version.isBlank()) {
            key = new FunctionKey(name, version);
            if (!factories.containsKey(key)) {
                throw new FaasException(ErrorCode.FUNCTION_NOT_FOUND,
                        "function " + key + " is not registered");
            }
        } else {
            key = latestVersionOf(name).orElseThrow(() -> new FaasException(ErrorCode.FUNCTION_NOT_FOUND,
                    "function " + name + " is not registered"));
        }
        return instanceFor(key);
    }

    private Optional<FunctionKey> latestVersionOf(String name) {
        return factories.keySet().stream()
                .filter(k -> k.name().equals(name))
                .max(Comparator.comparing(FunctionKey::version));
    }

    /** 实例缓存：同一版本共享一个 FunctionInstance，保证复用与并发上限语义。 */
    private final Map<FunctionKey, FunctionInstance> instances = new ConcurrentHashMap<>();

    FunctionInstance instanceFor(FunctionKey key) {
        return instances.computeIfAbsent(key,
                k -> new FunctionInstance(k, factories.get(k), config.maxConcurrencyPerInstance()));
    }
}
