package com.github.lechandonga.minifaasruntimejava.runtime;

/**
 * 函数某一版本的唯一标识。
 */
public record FunctionKey(String name, String version) {

    public FunctionKey {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("function name must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("function version must not be blank");
        }
    }

    @Override
    public String toString() {
        return name + ":" + version;
    }
}
