package me.sunmc.db.command;

import me.sunmc.db.SunDB;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class BukkitStatusCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("sundb.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("status")) {
            var report = CommandFormatter.buildStatusReport(SunDB.getManager());
            for (String line : report) {
                sender.sendMessage(line);
            }
            return true;
        }

        sender.sendMessage("§cUsage: /sundb status");
        return true;
    }
}
