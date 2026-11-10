package de.test;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Das Menue selbst anpassen: Farben, Logo, Groesse, Animationen. */
public class DesignScreen extends Form {
    public DesignScreen(Screen parent) {
        super(Component.literal("Design anpassen"), parent);
    }

    @Override
    protected void build() {
        description("Farben und Aussehen des Men\u00fcs.");
        section("Farben");
        choice("Farbschema", Theme.SCHEME_NAMES, () -> Theme.scheme, v -> {
            Theme.scheme = v;
            Theme.headHex = -1;
            Theme.accentHex = -1;
        }, 0);
        hex("Kopf (RRGGBB)", () -> Theme.headHex, v -> Theme.headHex = v);
        hex("Akzent (RRGGBB)", () -> Theme.accentHex, v -> Theme.accentHex = v);
        customRow(22, y -> (g, ox, oy, mx, my) -> {
            for (int i = 0; i < 4; i++) {
                Ui.fill(g, ox + 10 + i * 40, oy + y + 2, ox + 10 + i * 40 + 36, oy + y + 16, Theme.head(i));
            }
            Ui.fill(g, ox + 10 + 4 * 40, oy + y + 2, ox + 10 + 4 * 40 + 36, oy + y + 16, Theme.accent());
        });
        section("Anzeige");
        toggle("Logo", () -> Theme.showLogo, v -> Theme.showLogo = v, true);
        toggle("Animation", () -> Theme.animations, v -> Theme.animations = v, true);
        choice("Logo-Gr\u00f6\u00dfe", Theme.SIZE_NAMES, () -> Theme.logoSize, v -> Theme.logoSize = v, 0);
        choice("Spaltenbreite", Theme.WIDTH_NAMES, () -> Theme.colWidth, v -> Theme.colWidth = v, 1);
        wideButton("Alles zur\u00fccksetzen", false, () -> {
            Theme.reset();
            TestConfig.save();
            this.rebuildWidgets();
        });
        footer(null, null);
    }

    @Override
    public void onClose() {
        Theme.clamp();
        super.onClose();
    }
}
