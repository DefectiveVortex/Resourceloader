package org.vortex.resourceloader.management;

import org.bukkit.configuration.ConfigurationSection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PackStatistics {
    private final AtomicInteger totalDownloads;
    private final AtomicInteger successfulDownloads;
    private final AtomicInteger failedDownloads;
    private final AtomicLong totalLoadTime;
    private final AtomicLong lastUsed;

    public PackStatistics() {
        this.totalDownloads = new AtomicInteger(0);
        this.successfulDownloads = new AtomicInteger(0);
        this.failedDownloads = new AtomicInteger(0);
        this.totalLoadTime = new AtomicLong(0);
        this.lastUsed = new AtomicLong(System.currentTimeMillis());
    }

    public void recordUsage(boolean success, long loadTime) {
        totalDownloads.incrementAndGet();
        if (success) {
            successfulDownloads.incrementAndGet();
            totalLoadTime.addAndGet(loadTime);
        } else {
            failedDownloads.incrementAndGet();
        }
        lastUsed.set(System.currentTimeMillis());
    }

    public void loadFromConfig(ConfigurationSection config) {
        if (config == null) return;
        
        totalDownloads.set(config.getInt("total_downloads", 0));
        successfulDownloads.set(config.getInt("successful_downloads", 0));
        failedDownloads.set(config.getInt("failed_downloads", 0));
        totalLoadTime.set(config.getLong("total_load_time", 0));
        lastUsed.set(config.getLong("last_used", System.currentTimeMillis()));
    }

    public void saveToConfig(ConfigurationSection config) {
        config.set("total_downloads", totalDownloads.get());
        config.set("successful_downloads", successfulDownloads.get());
        config.set("failed_downloads", failedDownloads.get());
        config.set("total_load_time", totalLoadTime.get());
        config.set("last_used", lastUsed.get());
    }

    public int getTotalDownloads() {
        return totalDownloads.get();
    }

    public int getSuccessfulDownloads() {
        return successfulDownloads.get();
    }

    public int getFailedDownloads() {
        return failedDownloads.get();
    }

    public long getAverageLoadTime() {
        int successful = successfulDownloads.get();
        return successful > 0 ? totalLoadTime.get() / successful : 0;
    }

    public long getLastUsed() {
        return lastUsed.get();
    }
} 