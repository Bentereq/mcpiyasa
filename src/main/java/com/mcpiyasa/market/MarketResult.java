package com.mcpiyasa.market;

import com.mcpiyasa.engine.Quote;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bir market isteginin oyuncuya gosterilecek degismez sonucu. */
public final class MarketResult {
    /**
     * Bakim, bilinmeyen item ve gecersiz adet gibi yurutme oncesi retlerde
     * {@code null} olur; cagiranlar sonucu kullanmadan once null kontrol etmelidir.
     */
    public final TradeOutcome outcome;
    public final Quote quote;
    public final String messageKey;
    public final Map<String, String> vars;

    public MarketResult(TradeOutcome outcome,
                        Quote quote,
                        String messageKey,
                        Map<String, String> vars) {
        this.outcome = outcome;
        this.quote = quote;
        this.messageKey = messageKey;
        Map<String, String> safeVars = vars == null
            ? Collections.<String, String>emptyMap()
            : vars;
        this.vars = Collections.unmodifiableMap(
            new LinkedHashMap<String, String>(safeVars)
        );
    }
}
