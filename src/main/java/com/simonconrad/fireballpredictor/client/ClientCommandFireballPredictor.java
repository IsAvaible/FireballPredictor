package com.simonconrad.fireballpredictor.client;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

import java.util.Collections;
import java.util.List;

/**
 * Client-side {@code /fireballpredictor} command: opens the in-game configuration
 * screen (no arguments, or {@code gui} / {@code config}). Any other subcommand
 * (e.g. {@code reload}, which only exists on servers) is forwarded to the server.
 */
public class ClientCommandFireballPredictor extends CommandBase {

    @Override
    public String getCommandName() {
        return "fireballpredictor";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/fireballpredictor [gui]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0 || "gui".equals(args[0]) || "config".equals(args[0])) {
            if (sender instanceof EntityPlayer) {
                Minecraft mc = Minecraft.getMinecraft();
                mc.displayGuiScreen(
                        new com.simonconrad.fireballpredictor.client.gui.ModConfigGui(mc.currentScreen));
            }
            return;
        }

        // Unknown subcommand: let the server handle it (e.g. /fireballpredictor reload).
        if (sender instanceof EntityPlayer && Minecraft.getMinecraft().thePlayer != null) {
            StringBuilder forwarded = new StringBuilder("/fireballpredictor");
            for (String arg : args) {
                forwarded.append(' ').append(arg);
            }
            Minecraft.getMinecraft().thePlayer.sendChatMessage(forwarded.toString());
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
        return args.length == 1
                ? getListOfStringsMatchingLastWord(args, "gui", "config")
                : Collections.<String>emptyList();
    }
}
