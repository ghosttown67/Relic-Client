package relic.client.command.impl;

import net.minecraft.client.MinecraftClient;
import relic.client.command.Command;
import relic.client.command.CommandManager;

public class DropCommand extends Command {

    public DropCommand() {
        super("drop", "Drops your held item stack");
    }

    @Override
    public void execute(String[] args) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            CommandManager.sendMessage("Not in a world.");
            return;
        }

        mc.player.dropSelectedItem(true);
    }
}
