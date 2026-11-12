package de.test;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Einstellungs-Menue fuer ein Modul; der Inhalt kommt als Lambda (siehe Movement). */
public class ModuleScreen extends Form {
    @FunctionalInterface
    public interface Builder {
        void build(ModuleScreen form);
    }

    private final Builder builder;

    public ModuleScreen(Screen parent, String title, Builder builder) {
        super(Component.literal(title), parent);
        this.builder = builder;
    }

    @Override
    protected void build() {
        builder.build(this);
    }
}
