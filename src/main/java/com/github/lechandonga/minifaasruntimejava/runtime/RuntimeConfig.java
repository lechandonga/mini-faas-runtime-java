package com.github.lechandonga.minifaasruntimejava.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 运行时行为配置。
 *
 * @param maxConcurrencyPerInstance 单个版本实例允许的最大并发执行数
 * @param invokeTimeoutMs           单次调用超时时间（毫秒），超时后中断执行
 * @param queueTimeoutMs            并发已满时请求排队等待的最长时间（毫秒），
 *                                  超时后明确拒绝（OVERLOADED），不静默丢弃
 */
@ConfigurationProperties(prefix = "faas")
public record RuntimeConfig(int maxConcurrencyPerInstance,
                            long invokeTimeoutMs,
                            long queueTimeoutMs) {

    public RuntimeConfig {
        if (maxConcurrencyPerInstance <= 0) {
            maxConcurrencyPerInstance = 4;
        }
        if (invokeTimeoutMs <= 0) {
            invokeTimeoutMs = 10_000;
        }
        if (queueTimeoutMs <= 0) {
            queueTimeoutMs = 5_000;
        }
    }
}
