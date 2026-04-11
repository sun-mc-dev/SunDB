package me.sunmc.db.command;

import me.sunmc.db.SunDB;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class PaperCommand implements CommandExecutor {

    private final JavaPlugin plugin;

    public PaperCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("sundb.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "status" -> {
                    var report = CommandFormatter.buildStatusReport(SunDB.getManager());
                    report.forEach(sender::sendMessage);
                    return true;
                }
                case "reload" -> {
                    sender.sendMessage("§eSunDB is reloading...");
                    try {
                        SunDB.reload(plugin.getDataFolder().toPath());
                        sender.sendMessage("§aSunDB reloaded successfully.");
                    } catch (Exception e) {
                        sender.sendMessage("§cReload failed: " + e.getMessage());
                        plugin.getLogger().severe("SunDB reload failed: " + e.getMessage());
                    }
                    return true;
                }
            }
        }

        sender.sendMessage("§cUsage: /sundb <status|reload>");
        return true;
    }
}