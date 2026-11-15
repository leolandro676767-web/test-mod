package de.test;

import java.util.List;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Profile: alle Einstellungen unter einem Namen speichern und spaeter wieder laden. */
public class ProfileScreen extends Form {
    private static String name = "";
    private static String message = "";

    public ProfileScreen(Screen parent) {
        super(Component.literal("Profile"), parent);
    }

    @Override
    protected void build() {
        description("Speichert alle Einstellungen (Module, Design, Tasten) unter einem Namen, "
                + "z.B. PvP, Bauen oder Farmen. Laden ersetzt die aktuellen Einstellungen.");

        section("Neues Profil");
        text("Name", () -> name, v -> name = v, "");
        wideButton("Aktuelle Einstellungen speichern", true, () -> {
            String err = TestConfig.saveProfile(name);
            String n = TestConfig.cleanProfileName(name);
            message = err != null ? "§c" + err : "Gespeichert: „" + n + "“";
            if (err == null) name = "";
            this.rebuildWidgets();
        }, true);
        note(() -> message);

        List<String> all = TestConfig.profiles();
        section("Laden");
        if (all.isEmpty()) {
            note("Noch keine Profile gespeichert.");
        }
        for (String p : all) {
            wideButton("Laden: „" + p + "“", false, () -> {
                String err = TestConfig.loadProfile(p);
                message = err != null ? "§c" + err : "Geladen: „" + p + "“";
                this.rebuildWidgets();
            }, true);
        }
        if (!all.isEmpty()) {
            sectionClosed("Löschen");
            for (String p : all) {
                wideButton("Löschen: „" + p + "“", false, () -> {
                    TestConfig.deleteProfile(p);
                    message = "Gelöscht: „" + p + "“";
                    this.rebuildWidgets();
                }, true);
            }
        }
        footer(null, null);
    }
}
