package com.simonconrad.fireballpredictor.server;

import com.simonconrad.fireballpredictor.config.ServerConfig;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;

import java.util.Collections;
import java.util.List;

/**
 * {@code /fireballpredictor reload} - reloads {@code config/fireballpredictor-server.json}
 * and re-syncs the tracking restrictions and the mobGriefing gamerule to all connected
 * players (1.8.9 equivalent of master's command registration).
 */
public class CommandFireballPredictor extends CommandBase {

    @Override
    public String getCommandName() {
        return "fireballpredictor";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/fireballpredictor reload";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2; // game masters
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length >= 1 && "reload".equals(args[0])) {
            ServerConfig.reload();
            MinecraftServer server = MinecraftServer.getServer();
            if (server != null) {
                FireballPredictorServer.broadcastRules(server);
            }
            sender.addChatMessage(new ChatComponentText(
                    "Reloaded Fireball Predictor server config and re-synced tracking restrictions"
                            + " and gamerules to all players."));
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        return args.length == 1
                ? getListOfStringsMatchingLastWord(args, "reload")
                : Collections.<String>emptyList();
    }
}
