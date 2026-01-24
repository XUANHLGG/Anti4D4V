package com.tendoarisu.anti4D4V;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import org.bukkit.plugin.java.JavaPlugin;

public class FoliaScheduler {
    private static TaskScheduler universalScheduler;

    public static void init(JavaPlugin plugin) {
        universalScheduler = UniversalScheduler.getScheduler(plugin);
    }

    public static TaskScheduler getScheduler() {
        if (universalScheduler == null) {
            throw new IllegalStateException("scheduler not initialized");
        }
        return universalScheduler;
    }
}