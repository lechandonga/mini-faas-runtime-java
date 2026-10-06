package com.github.lechandonga.minifaasruntimejava.config;

import com.github.lechandonga.minifaasruntimejava.core.FaasRuntime;
import com.github.lechandonga.minifaasruntimejava.core.FunctionRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FaasProperties.class)
public class FaasConfig {

    @Bean
    public FunctionRegistry functionRegistry() {
        return new FunctionRegistry();
    }

    @Bean(destroyMethod = "shutdown")
    public FaasRuntime faasRuntime(FunctionRegistry registry, FaasProperties properties) {
        return new FaasRuntime(registry,
                properties.getMaxConcurrencyPerInstance(),
                properties.getInvokeTimeoutMs());
    }
}
