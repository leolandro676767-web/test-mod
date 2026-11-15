package de.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Alle vergebenen Modul-Tasten auf einen Blick: Halten/Umschalten waehlen oder Taste entfernen. */
public class BindsScreen extends Form {
    public BindsScreen(Screen parent) {
        super(Component.literal("Tasten-Übersicht"), parent);
    }

    @Override
    protected void build() {
        description("Alle Tasten, die du im Hauptmenü mit „Tasten zuweisen“ vergeben hast. "
                + "Halten = Modul ist nur an, solange die Taste gedrückt ist.");
        List<String> names = new ArrayList<>(Keybinds.BINDS.keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        if (names.isEmpty()) {
            section("Tasten");
            note("Noch keine Tasten vergeben. Im Hauptmenü auf „Tasten zuweisen“ klicken, "
                    + "dann ein Modul anklicken und eine Taste drücken.");
        } else {
            section("Halten statt Umschalten");
            for (String n : names) {
                Integer code = Keybinds.get(n);
                String label = n + " (" + (code == null ? "?" : Keybinds.keyName(code)) + ")";
                toggle(label, () -> Keybinds.HOLD.contains(n), v -> {
                    if (v) {
                        Keybinds.HOLD.add(n);
                    } else {
                        Keybinds.HOLD.remove(n);
                    }
                }, false);
            }
            sectionClosed("Tasten entfernen");
            for (String n : names) {
                wideButton("Taste von „" + n + "“ entfernen", false, () -> {
                    Keybinds.clear(n);
                    this.rebuildWidgets();
                }, true);
            }
        }
        footer(null, null);
    }
}
