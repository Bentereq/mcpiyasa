package com.mcpiyasa.market;

/** Market zamanini test edilebilir bicimde disaridan saglar. */
public interface Clock {
    int slotIndex();

    double slotFraction();

    /**
     * Sunucunun yerel gununu ISO {@code yyyy-MM-dd} biciminde dondurur.
     * Bu bicim {@link java.time.LocalDate#toString()} ile ayni olmak zorundadir:
     * API anahtari parse eder, snapshot sorgulari ise kronolojik siralama icin
     * anahtarlari metin olarak karsilastirir.
     */
    String dayKey();

    long nowMs();
}
