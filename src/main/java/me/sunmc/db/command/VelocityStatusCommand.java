package me.sunmc.db.command;

import com.velocitypowered.api.command.SimpleCommand;
import me.sunmc.db.SunDB;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;

public class VelocityStatusCommand implements SimpleCommand {

    @Override
    public void execute(@NotNull Invocation invocation) {
        if (!invocation.source().hasPermission("sundb.admin")) {
            invocation.source().sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cYou do not have permission to use this command."));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length > 0 && args[0].equalsIgnoreCase("status")) {
            var report = CommandFormatter.buildStatusReport(SunDB.getManager());
            for (String line : report) {
                invocation.source().sendMessage(LegacyComponentSerializer.legacySection().deserialize(line));
            }
            return;
        }

        invocation.source().sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cUsage: /sundb status"));
    }

    @Override
    public boolean hasPermission(@NotNull Invocation invocation) {
        return invocation.source().hasPermission("sundb.admin");
    }
}
