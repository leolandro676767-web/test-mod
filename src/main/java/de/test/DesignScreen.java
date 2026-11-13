package de.test;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Das Menue selbst anpassen: Farben, Groesse, Logo, Animationen - mit Live-Vorschau. */
public class DesignScreen extends Form {
    public DesignScreen(Screen parent) {
        super(Component.literal("Design anpassen"), parent);
    }

    @Override
    protected void build() {
        description("So sieht dein Menü aus. Änderungen siehst du sofort.");

        section("Module");
        choice("Modus", Theme.MODE_NAMES, () -> Theme.menuMode, v -> Theme.menuMode = v, 0);
        note("Standard zeigt nur die einfachen Module. Experte zeigt alle.");

        section("Anzeige im Spiel");
        toggle("Aktive Module anzeigen", () -> Theme.hudList, v -> Theme.hudList = v, true);
        choice("Gr\u00f6\u00dfe der Liste", Theme.HUD_SIZE_NAMES, () -> Theme.hudListSize, v -> Theme.hudListSize = v, 1);
        note("Zeigt oben links alle Module, die gerade an sind.");

        section("Farben");
        swatches("Farbschema", Theme.SCHEME_NAMES,
                i -> new int[] {Theme.schemeHead(i, 0), Theme.schemeHead(i, 3)},
                () -> Theme.scheme, v -> {
                    Theme.scheme = v;
                    Theme.headHex = -1;
                    Theme.accentHex = -1;
                }, 0);
        // Vorschau: so sehen Kopfzeile und ein eingeschaltetes Modul gerade aus
        customRow(52, y -> (g, ox, oy, mx, my) -> {
            int x = ox + contentX();
            int w = contentWidth();
            int top = oy + y + 2;
            Ui.roundRect(g, x - 1, top - 1, w + 2, 46, Ui.RADIUS + 1, Theme.border());
            Ui.roundRect(g, x, top + Ui.HEADER_H, w, 22, Ui.RADIUS, Ui.PANEL, false, true);
            Ui.header(g, x, top, w, 0f, 1f, false);
            Ui.text(g, this.font, "Vorschau", x + 8, top + 7, 0xFFFFFFFF, true);
            int accent = Theme.accent();
            Ui.roundGradientH(g, x, top + Ui.HEADER_H, w, 22, Ui.RADIUS, Ui.withA(accent, 0x4A),
                    Ui.withA(accent, 0x0A), false, true);
            Ui.roundRect(g, x + 2, top + Ui.HEADER_H + 4, 2, 14, 1, accent);
            Ui.text(g, this.font, "Eingeschaltetes Modul", x + 10, top + Ui.HEADER_H + 7, Ui.TEXT_ON, true);
            Ui.switchPill(g, x + w - 24, top + Ui.HEADER_H + 7, 1f);
        });

        sectionClosed("Eigene Farben");
        hex("Kopffarbe", () -> Theme.headHex, v -> Theme.headHex = v);
        hex("Akzentfarbe", () -> Theme.accentHex, v -> Theme.accentHex = v);
        note("Hex-Code wie 9A74E6. Leer lassen = Farbe vom Farbschema.");

        section("Größe");
        choice("Menü-Größe", Theme.MENU_SCALE_NAMES, () -> Theme.menuScale,
                v -> Theme.menuScale = v, 0);
        choice("Spaltenbreite", Theme.WIDTH_NAMES, () -> Theme.colWidth, v -> Theme.colWidth = v, 1);
        note("Auto: so groß wie möglich, aber alle Spalten passen nebeneinander. "
                + "Spalten werden von selbst breiter, wenn ein Name mehr Platz braucht.");

        section("Logo");
        toggle("Logo anzeigen", () -> Theme.showLogo, v -> Theme.showLogo = v, true);
        choice("Logo-Größe", Theme.SIZE_NAMES, () -> Theme.logoSize, v -> Theme.logoSize = v, 0);

        section("Effekte");
        toggle("Animationen", () -> Theme.animations, v -> Theme.animations = v, true);
        note("Aus = alles erscheint sofort, ohne Übergänge.");

        wideButton("Alles auf Standard zurücksetzen", false, () -> {
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
