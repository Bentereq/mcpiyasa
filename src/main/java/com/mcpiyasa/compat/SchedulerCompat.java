package com.mcpiyasa.compat;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class SchedulerCompat {
    private SchedulerCompat() {
    }

    public static BukkitTask repeatSync(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        return Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
    }

    public static BukkitTask runSync(Plugin plugin, Runnable runnable) {
        return Bukkit.getScheduler().runTask(plugin, runnable);
    }

    /** Tik olayi icinde envanter acmamak icin bir sonraki tike erteler. */
    public static BukkitTask runLater(
            Plugin plugin, Runnable runnable, long delayTicks) {
        return Bukkit.getScheduler().runTaskLater(
            plugin, runnable, delayTicks);
    }
}
