package com.mcpiyasa.diag;

/** Acilis teshisindeki tek bir kontrolun degismez sonucu. */
public final class DiagCheck {
    public final String id;
    public final boolean ok;
    public final String messageKey;

    public DiagCheck(String id, boolean ok, String messageKey) {
        this.id = id;
        this.ok = ok;
        this.messageKey = messageKey;
    }
}
