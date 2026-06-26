package relic.client.command.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import relic.client.command.Command;
import relic.client.command.CommandManager;

public class SayCommand extends Command {

    public SayCommand() {
        super("say", "Send a chat message", "chat");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            CommandManager.sendMessage("Usage: .say <message>");
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayNetworkHandler net = mc.getNetworkHandler();
        if (net == null) {
            CommandManager.sendMessage("Not connected.");
            return;
        }
        net.sendChatMessage(String.join(" ", args));
    }
}
