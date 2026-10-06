package com.github.lechandonga.minifaasruntimejava.core.error;

/**
 * 注册被拒绝时抛出，reason 区分具体原因，绝不静默覆盖已有版本。
 */
public class RegistrationException extends RuntimeException {

    public enum Reason {
        /** 函数名为空或非法 */
        INVALID_NAME,
        /** 版本号非法（必须为正整数） */
        INVALID_VERSION,
        /** 处理器缺失 */
        HANDLER_MISSING,
        /** 同一函数的同一版本已存在，拒绝覆盖 */
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
