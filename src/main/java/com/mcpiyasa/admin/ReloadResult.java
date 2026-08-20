package com.mcpiyasa.admin;

/** Bir reload denemesinin canli runtime acisindan kesin sonucudur. */
public enum ReloadResult {
    OK("admin.reload-ok"),
    OK_SAFE_MODE("admin.reload-ok-safe-mode"),
    FAILED_CANDIDATE("admin.reload-failed-candidate"),
    FAILED_ROLLBACK("admin.reload-failed-rollback");

    private final String messageKey;

    ReloadResult(String messageKey) {
        this.messageKey = messageKey;
    }

    public String messageKey() {
        return messageKey;
    }
}
