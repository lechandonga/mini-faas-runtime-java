package com.github.lechandonga.minifaasruntimejava.core;

import com.github.lechandonga.minifaasruntimejava.core.error.RegistrationException;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * 函数注册表：同一函数（按名字）的多个版本共存，版本号为正整数，
 * 数值越大越新。重复版本、缺失处理器、非法名字的注册会被明确拒绝。
 */
public class FunctionRegistry {

    private final Map<String, ConcurrentSkipListMap<Integer, FunctionHandler>> functions =
            new ConcurrentHashMap<>();

    public void register(String name, int version, FunctionHandler handler) {
        if (name == null || name.isBlank()) {
            throw new RegistrationException(RegistrationException.Reason.INVALID_NAME,
                    "function name must not be null or blank");
        }
        if (version <= 0) {
            throw new RegistrationException(RegistrationException.Reason.INVALID_VERSION,
                    "version must be a positive integer, got: " + version);
        }
        if (handler == null) {
            throw new RegistrationException(RegistrationException.Reason.HANDLER_MISSING,
                    "handler must not be null for function '" + name + "' version " + version);
        }
        ConcurrentSkipListMap<Integer, FunctionHandler> versions =
                functions.computeIfAbsent(name, k -> new ConcurrentSkipListMap<>());
        FunctionHandler previous = versions.putIfAbsent(version, handler);
        if (previous != null) {
            throw new RegistrationException(RegistrationException.Reason.DUPLICATE_VERSION,
                    "function '" + name + "' version " + version + " is already registered");
        }
    }

    /**
     * 解析处理器：version 为 null 时固定落到最新（版本号最大）的已发布版本。
     */
    public Optional<FunctionHandler> resolve(String name, Integer version) {
        ConcurrentSkipListMap<Integer, FunctionHandler> versions = functions.get(name);
        if (versions == null) {
            return Optional.empty();
        }
        if (version == null) {
            Map.Entry<Integer, FunctionHandler> latest = versions.lastEntry();
            return latest == null ? Optional.empty() : Optional.of(latest.getValue());
        }
        return Optional.ofNullable(versions.get(version));
    }

    public boolean contains(String name) {
        return functions.containsKey(name);
    }

    public Optional<Integer> latestVersion(String name) {
        ConcurrentSkipListMap<Integer, FunctionHandler> versions = functions.get(name);
        return versions == null || versions.isEmpty()
                ? Optional.empty() : Optional.of(versions.lastKey());
    }
}
