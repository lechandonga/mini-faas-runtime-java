package com.github.lechandonga.minifaasruntimejava.spring;

import com.github.lechandonga.minifaasruntimejava.core.FaasRuntime;
import com.github.lechandonga.minifaasruntimejava.core.FunctionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 启动时扫描所有带 {@link FaasFunction} 注解的 FunctionHandler Bean 并自动注册。
 * 注册冲突（重复版本等）会导致启动失败，问题在部署期暴露而不是静默覆盖。
 */
@Component
public class FaasFunctionRegistrar implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(FaasFunctionRegistrar.class);

    private final ApplicationContext applicationContext;
    private final FaasRuntime runtime;

    public FaasFunctionRegistrar(ApplicationContext applicationContext, FaasRuntime runtime) {
        this.applicationContext = applicationContext;
        this.runtime = runtime;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, FunctionHandler> handlers =
                applicationContext.getBeansOfType(FunctionHandler.class);
        for (FunctionHandler handler : handlers.values()) {
            FaasFunction annotation = handler.getClass().getAnnotation(FaasFunction.class);
            if (annotation == null) {
                continue;
            }
            runtime.register(annotation.name(), annotation.version(), handler);
            log.info("registered function '{}' version {} -> {}",
                    annotation.name(), annotation.version(), handler.getClass().getSimpleName());
        }
    }
}
