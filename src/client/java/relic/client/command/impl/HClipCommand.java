package relic.client.command.impl;

import net.minecraft.client.MinecraftClient;
import relic.client.command.Command;
import relic.client.command.CommandManager;

public class HClipCommand extends Command {

    public HClipCommand() {
        super("hclip", "Teleport horizontally through blocks", "h");
    }

    @Override
    public void execute(String[] args) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            CommandManager.sendMessage("Not in a world.");
            return;
        }
        if (args.length < 1) {
            CommandManager.sendMessage("Usage: .hclip <distance>");
            return;
        }
        double dist;
        try {
            dist = Double.parseDouble(args[0]);
        } catch (NumberFormatException e) {
            CommandManager.sendMessage("Invalid distance: " + args[0]);
            return;
        }
        double rad = Math.toRadians(mc.player.getYaw());
        double dx = -Math.sin(rad) * dist;
        double dz = Math.cos(rad) * dist;
        mc.player.setPosition(mc.player.getX() + dx, mc.player.getY(), mc.player.getZ() + dz);
        CommandManager.sendMessage("HClip " + dist);
    }
}
