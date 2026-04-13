package me.sunmc.db.command;

import me.sunmc.db.SunDB;
import me.sunmc.db.api.AsyncDatabase;
import me.sunmc.db.api.DatabaseManager;
import me.sunmc.db.api.DatabaseMetrics;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class CommandFormatter {

    private CommandFormatter() {
    }

    /**
     * Generates the multi-line text output for /sundb status
     */
    public static @NotNull List<String> buildStatusReport(@NotNull DatabaseManager manager) {
        List<String> report = new ArrayList<>();
        report.add("§b=== §lSunDB Status §b===");

        var registry = manager.getRegistered();
        if (registry.isEmpty()) {
            report.add("§7No databases registered.");
            return report;
        }

        for (AsyncDatabase db : registry.values()) {
            DatabaseMetrics m = db.metrics();
            boolean isAlive = db.isConnected();

            String statusColor = isAlive ? "§a" : "§c";
            String statusText = isAlive ? "CONNECTED" : "OFFLINE";

            report.add("§8[§e" + db.name() + "§8] §7Type: " + db.type() + " | Status: " + statusColor + statusText);

            if (isAlive && m.totalConnections() > 0) {
                report.add("  §7Active: §a" + m.activeConnections() + " §7| Idle: §b" + m.idleConnections() + " §7| Waiters: §c" + m.threadsAwaitingConnection());
            } else if (isAlive) {
                report.add("  §7(Native connection metrics unavailable)");
            }
        }
        report.add("§b======================");
        return report;
    }

    /**
     * Generates a report of the most recent slow queries.
     */
    public static @NotNull List<String> buildTopReport() {
        List<String> report = new ArrayList<>();
        report.add("§c=== §lSlow Query Log (Last 100) §c===");

        var logs = SunDB.getSlowQueryLog();
        if (logs.isEmpty()) {
            report.add("§7No slow queries recorded yet.");
            return report;
        }

        int size = logs.size();
        for (int i = Math.max(0, size - 10); i < size; i++) {
            var info = logs.get(i);
            report.add("§8[§f" + info.ms() + "ms§8] §7" + info.sql());
        }

        report.add("§c==============================");
        return report;
    }
}
