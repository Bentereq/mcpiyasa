package com.mcpiyasa;

import com.mcpiyasa.market.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Sunucunun yerel saatini piyasa zaman sozlesmesine uyarlar. */
public final class SystemClock implements Clock {
    @Override
    public int slotIndex() {
        LocalDateTime now = LocalDateTime.now();
        int mondayBasedDay = now.getDayOfWeek().getValue() - 1;
        return mondayBasedDay * 24 + now.getHour();
    }

    @Override
    public double slotFraction() {
        return LocalDateTime.now().getMinute() / 60.0;
    }

    @Override
    public String dayKey() {
        return LocalDate.now().toString();
    }

    @Override
    public long nowMs() {
        return System.currentTimeMillis();
    }
}
