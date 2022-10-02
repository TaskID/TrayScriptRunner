package net.taskid;

import java.awt.CheckboxMenuItem;
import java.awt.MenuItem;
import java.awt.event.ActionListener;
import java.awt.event.ItemListener;

public class MenuItemBuilder<T extends MenuItem> {

    public static MenuItemBuilder<MenuItem> item(String text) {
        return new MenuItemBuilder<>(new MenuItem(text));
    }

    public static MenuItemBuilder<MenuItem> checkBox(String text) {
        return new MenuItemBuilder<>(new CheckboxMenuItem(text));
    }

    public static MenuItemBuilder<MenuItem> checkBox(String text, boolean state) {
        return new MenuItemBuilder<>(new CheckboxMenuItem(text, state));
    }

    private final T item;

    private MenuItemBuilder(T t) {
        this.item = t;
    }

    public MenuItemBuilder<T> click(ActionListener listener) {
        this.item.addActionListener(listener);
        return this;
    }

    public MenuItemBuilder<T> enabled(boolean enabled) {
        this.item.setEnabled(enabled);
        return this;
    }

    public MenuItemBuilder<T> state(boolean state) {
        ((CheckboxMenuItem) this.item).setState(state);
        return this;
    }

    public MenuItemBuilder<T> stateChange(ItemListener listener) {
        ((CheckboxMenuItem) this.item).addItemListener(listener);
        return this;
    }

    public T build() {
        return this.item;
    }

}