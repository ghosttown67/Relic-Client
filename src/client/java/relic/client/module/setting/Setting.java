package relic.client.module.setting;

public abstract class Setting<T> {
    private final String name;
    private T value;
    private Runnable changeListener;

    protected Setting(String name, T defaultValue) {
        this.name = name;
        this.value = defaultValue;
    }

    public String getName() {
        return name;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
        notifyChanged();
    }

    public Setting<T> onChanged(Runnable listener) {
        this.changeListener = listener;
        return this;
    }

    protected void notifyChanged() {
        if (changeListener != null) {
            changeListener.run();
        }
    }
}
