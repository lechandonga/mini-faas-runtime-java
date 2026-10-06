package com.github.lechandonga.minifaasruntimejava.runtime;

/**
 * 注册被拒绝时抛出，reason 区分拒绝原因，绝不静默覆盖已有版本。
 */
public class RegistrationException extends RuntimeException {

    public enum Reason {
        /** 函数名为空或包含非法字符 */
        INVALID_NAME,
        /** 版本号为空 */
        INVALID_VERSION,
        /** 处理器工厂缺失 */
        MISSING_HANDLER,
        /** 同名同版本已存在，拒绝覆盖 */
        DUPLICATE_VERSION
    }

    private final Reason reason;

    public RegistrationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
