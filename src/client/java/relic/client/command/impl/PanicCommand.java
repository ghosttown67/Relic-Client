package relic.client.command.impl;

import relic.client.command.Command;
import relic.client.module.Module;
import relic.client.module.ModuleManager;

public class PanicCommand extends Command {

    public PanicCommand() {
        super("panic", "Disables all modules");
    }

    @Override
    public void execute(String[] args) {
        for (Module module : ModuleManager.getInstance().getAllModules()) {
            if (module.isEnabled()) {
                module.setEnabled(false);
            }
        }
    }
}
