package me.sunmc.db.command;

import com.velocitypowered.api.command.SimpleCommand;
import me.sunmc.db.SunDB;
import me.sunmc.db.api.migration.Exporter;
import me.sunmc.db.util.SunDBLogger;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

public class VelocityCommand implements SimpleCommand {

    private final Path dataDirectory;

    public VelocityCommand(Path dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    @Override
    public void execute(@NotNull Invocation invocation) {
        if (!invocation.source().hasPermission("sundb.admin")) {
            send(invocation, "§cYou do not have permission to use this command.");
            return;
        }

        String[] args = invocation.arguments();
        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "status" -> {
                    CommandFormatter.buildStatusReport(SunDB.getManager())
                            .forEach(line -> send(invocation, line));
                    return;
                }
                case "top" -> {
                    CommandFormatter.buildTopReport().forEach(line -> send(invocation, line));
                    return;
                }
                case "migrate-db" -> {
                    if (args.length < 3) {
                        send(invocation, "§cUsage: /sundb migrate-db <source> <target>");
                        return;
                    }
                    String source = args[1];
                    String target = args[2];
                    send(invocation, "§eMigrating data from §f" + source + " §eto §f" + target + "§e...");
                    try {
                        Exporter.export(SunDB.get(source), SunDB.get(target))
                                .thenAccept(v -> send(invocation, "§aMigration completed successfully from " + source + " to " + target))
                                .exceptionally(ex -> {
                                    send(invocation, "§cMigration failed: " + ex.getMessage());
                                    return null;
                                });
                    } catch (Exception e) {
                        send(invocation, "§cError: " + e.getMessage());
                    }
                    return;
                }
                case "reload" -> {
                    send(invocation, "§eSunDB is reloading...");
                    try {
                        SunDB.reload(dataDirectory);
                        send(invocation, "§aSunDB reloaded successfully.");
                    } catch (Exception e) {
                        send(invocation, "§cReload failed: " + e.getMessage());
                        SunDBLogger.error("SunDB reload failed: " + e.getMessage());
                    }
                    return;
                }
            }
        }

        send(invocation, "§cUsage: /sundb <status|reload|top|migrate-db>");
    }

    @Override
    public boolean hasPermission(@NotNull Invocation invocation) {
        return invocation.source().hasPermission("sundb.admin");
    }

    private void send(@NotNull Invocation inv, String msg) {
        inv.source().sendMessage(LegacyComponentSerializer
                .legacySection().deserialize(msg));
    }
}