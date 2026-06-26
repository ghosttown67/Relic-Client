package relic.client.command.impl;

import net.minecraft.client.MinecraftClient;
import relic.client.command.Command;
import relic.client.command.CommandManager;

public class DismountCommand extends Command {

    public DismountCommand() {
        super("dismount", "Dismount your current vehicle");
    }

    @Override
    public void execute(String[] args) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            CommandManager.sendMessage("Not in a world.");
            return;
        }
        if (!mc.player.hasVehicle()) {
            CommandManager.sendMessage("Not riding anything.");
            return;
        }
        mc.player.dismountVehicle();
    }
}
