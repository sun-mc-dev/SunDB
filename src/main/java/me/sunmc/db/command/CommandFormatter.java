package me.sunmc.db.command;

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
}
