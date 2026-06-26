package relic.client.command.impl;

import net.minecraft.client.MinecraftClient;
import relic.client.command.Command;
import relic.client.command.CommandManager;

public class VClipCommand extends Command {

    public VClipCommand() {
        super("vclip", "Teleport vertically through blocks", "v");
    }

    @Override
    public void execute(String[] args) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            CommandManager.sendMessage("Not in a world.");
            return;
        }
        if (args.length < 1) {
            CommandManager.sendMessage("Usage: .vclip <distance>");
            return;
        }
        double dy;
        try {
            dy = Double.parseDouble(args[0]);
        } catch (NumberFormatException e) {
            CommandManager.sendMessage("Invalid distance: " + args[0]);
            return;
        }
        mc.player.setPosition(mc.player.getX(), mc.player.getY() + dy, mc.player.getZ());
        CommandManager.sendMessage("VClip " + dy);
    }
}
