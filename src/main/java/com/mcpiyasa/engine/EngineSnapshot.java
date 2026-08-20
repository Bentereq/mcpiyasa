package com.mcpiyasa.engine;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reload sirasinda canli engine durumunu degismez olarak tasir. */
public final class EngineSnapshot {
    private final Map<String, State> states;

    EngineSnapshot(Map<String, State> states) {
        this.states = Collections.unmodifiableMap(
            new LinkedHashMap<String, State>(states));
    }

    Map<String, State> states() {
        return states;
    }

    /** Tek bir grubun reload anindaki canli durumudur. */
    static final class State {
        final double stock;
        final double epsilon;

        State(double stock, double epsilon) {
            this.stock = stock;
            this.epsilon = epsilon;
        }
    }
}
