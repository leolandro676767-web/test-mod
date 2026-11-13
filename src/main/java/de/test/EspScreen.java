package de.test;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Einstellungen fuer das ESP. */
public class EspScreen extends Form {
    public EspScreen(Screen parent) {
        super(Component.literal("ESP"), parent);
    }

    private static String count(int n, String allText) {
        return n == 0 ? allText : n + " gewählt";
    }

    @Override
    protected void build() {
        description("Zeigt Ziele durch Wände an.\nNur in der Ego-Ansicht.");

        section("Ziele");
        toggle("Spieler", () -> Esp.players, v -> Esp.players = v, true);
        toggle("Mobs", () -> Esp.mobs, v -> Esp.mobs = v, true);
        toggle("Items am Boden", () -> Esp.items, v -> Esp.items = v, false);
        toggle("Blöcke", () -> Esp.blocks, v -> {
            Esp.blocks = v;
            Esp.rescan();
        }, false);

        section("Darstellung");
        toggle("Umriss", () -> Esp.box, v -> Esp.box = v, true);
        choice("Umriss-Stil", Render3D.STYLES, () -> RenderMods.outlineStyle, v -> RenderMods.outlineStyle = v, 2);
        slider("Scan-Tempo", 1, 10, () -> RenderMods.outlineScanSpeed, v -> RenderMods.outlineScanSpeed = v, 4);
        toggle("Welle", () -> RenderMods.outlineWave, v -> RenderMods.outlineWave = v, true);
        toggle("Linien", () -> Esp.tracers, v -> Esp.tracers = v, false);
        toggle("Name + Entfernung", () -> Esp.labels, v -> Esp.labels = v, true);

        section("Farben (leer = Standard)");
        hex("Spieler", () -> Esp.colPlayer, v -> Esp.colPlayer = v);
        hex("Mobs", () -> Esp.colMob, v -> Esp.colMob = v);
        hex("Items", () -> Esp.colItem, v -> Esp.colItem = v);
        hex("Bl\u00f6cke", () -> Esp.colBlock, v -> Esp.colBlock = v);

        section("Reichweite");
        slider("Radius", 8, 256, () -> Esp.radius, v -> Esp.radius = v, 64);
        slider("Block-Radius", 4, 48, () -> Esp.blockRadius, v -> {
            Esp.blockRadius = v;
            Esp.rescan();
        }, 24);

        wideButton("Mobs wählen (" + count(Esp.mobTypes.size(), "alle") + ")", false,
                () -> this.minecraft.gui.setScreen(new PickerScreen(this, "ESP: Mobs",
                        PickerScreen.Kind.ENTITY, PickerScreen.ofSet(Esp.mobTypes), null, null)));
        wideButton("Items wählen (" + count(Esp.itemTypes.size(), "alle") + ")", false,
                () -> this.minecraft.gui.setScreen(new PickerScreen(this, "ESP: Items",
                        PickerScreen.Kind.ITEM, PickerScreen.ofSet(Esp.itemTypes), null, null)));
        wideButton("Blöcke wählen (" + count(Esp.blockTypes.size(), "keine") + ")", false,
                () -> this.minecraft.gui.setScreen(new PickerScreen(this, "ESP: Blöcke",
                        PickerScreen.Kind.BLOCK, PickerScreen.ofSet(Esp.blockTypes), null, null)));
        note("Leere Mob- oder Item-Auswahl = alle anzeigen.");

        footer(() -> Esp.enabled, v -> Esp.enabled = v);
    }

    @Override
    public void onClose() {
        Esp.rescan();
        super.onClose();
    }
}
