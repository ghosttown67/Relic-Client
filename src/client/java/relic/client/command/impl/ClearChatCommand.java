package relic.client.command.impl;

import net.minecraft.client.MinecraftClient;
import relic.client.command.Command;

public class ClearChatCommand extends Command {

    public ClearChatCommand() {
        super("clearchat", "Clears the chat");
    }

    @Override
    public void execute(String[] args) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.inGameHud == null) return;

        client.inGameHud.getChatHud().clear(false);
    }
}
