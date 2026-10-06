package com.github.lechandonga.minifaasruntimejava.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 运行时配置。
 */
@ConfigurationProperties(prefix = "faas")
public class FaasProperties {

    /** 每个函数版本实例的最大并发执行数，超出的请求排队等待 */
    private int maxConcurrencyPerInstance = 4;

    /** 单次调用超时时间（毫秒），超时后中断执行并返回 TIMEOUT */
    private long invokeTimeoutMs = 3000;

    public int getMaxConcurrencyPerInstance() {
        return maxConcurrencyPerInstance;
    }

    public void setMaxConcurrencyPerInstance(int maxConcurrencyPerInstance) {
        this.maxConcurrencyPerInstance = maxConcurrencyPerInstance;
    }

    public long getInvokeTimeoutMs() {
        return invokeTimeoutMs;
    }

    public void setInvokeTimeoutMs(long invokeTimeoutMs) {
        this.invokeTimeoutMs = invokeTimeoutMs;
    }
}
