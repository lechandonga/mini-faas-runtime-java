package com.github.lechandonga.minifaasruntimejava.runtime;

import java.util.Map;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 运行时装配：收集所有 @FaasFunction 标注的 handler 并注册到注册表。
 * 新增函数只需新增一个实现类，无需改动这里。
 */
@Configuration
@EnableConfigurationProperties(RuntimeConfig.class)
public class RuntimeConfiguration {

    @Bean
    public FunctionRegistry functionRegistry(RuntimeConfig config,
                                             Map<String, FunctionHandler> handlers) {
        FunctionRegistry registry = new FunctionRegistry(config);
        handlers.forEach((beanName, handler) -> {
            FaasFunction meta = handler.getClass().getAnnotation(FaasFunction.class);
            if (meta != null) {
                registry.register(meta.name(), meta.version(), () -> instantiate(handler.getClass()));
            }
        });
        return registry;
    }

    @Bean
    public FunctionInvoker functionInvoker(FunctionRegistry registry, RuntimeConfig config) {
        return new FunctionInvoker(registry, config);
    }

    private static FunctionHandler instantiate(Class<? extends FunctionHandler> type) {
        try {
            var ctor = type.getDeclaredConstructor();
            ctor.trySetAccessible();
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new FaasException(ErrorCode.INTERNAL_ERROR,
                    "cannot instantiate handler " + type.getSimpleName(), e);
        }
    }
}
