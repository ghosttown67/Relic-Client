package relic.client.command;

public abstract class Command {

    private final String name;
    private final String description;
    private final String[] aliases;

    protected Command(String name, String description, String... aliases) {
        this.name = name;
        this.description = description;
        this.aliases = aliases;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String[] getAliases() {
        return aliases;
    }

    public boolean matches(String label) {
        if (name.equalsIgnoreCase(label)) return true;
        for (String alias : aliases) {
            if (alias.equalsIgnoreCase(label)) return true;
        }
        return false;
    }

    public abstract void execute(String[] args);
}
