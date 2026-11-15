package de.test;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Sprache des 1912-Menues waehlen (der Titel bleibt zweisprachig, damit man immer zurueckfindet). */
public class LanguageScreen extends Form {
    public LanguageScreen(Screen parent) {
        super(Component.literal("Sprache / Language"), parent);
    }

    @Override
    protected void build() {
        description("Sprache des 1912-Menüs. „Automatisch“ nimmt die Sprache, die in Minecraft eingestellt ist.");
        section("Sprache");
        for (int i = 0; i < Lang.NAMES.length; i++) {
            final int idx = i;
            wideButton(Lang.NAMES[i], Lang.language == i, () -> {
                Lang.language = idx;
                TestConfig.save();
                this.rebuildWidgets();
            }, true);
        }
        footer(null, null);
    }
}
