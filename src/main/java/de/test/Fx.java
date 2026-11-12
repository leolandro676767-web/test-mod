package de.test;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Gemeinsame visuelle Effekte fuer alle Menues: ein lebendiger, dezenter Hintergrund,
 * weiches Leuchten um Panels, ein kurzes Aufleuchten beim Öffnen und ein wandernder
 * Glanz in der Kopfzeile. Bewusst minimalistisch gehalten (nur Rechtecke, keine Texturen),
 * damit es auf jedem Rechner flüssig bleibt. Alles reagiert auf Theme.animations.
 */
public final class Fx {
    private Fx() {
    }

    private static final long START = System.currentTimeMillis();

    /** Sekunden seit Spielstart als fortlaufende Phase (fuer Wellen/Drift). */
    public static float time() {
        return (System.currentTimeMillis() - START) / 1000f;
    }

    // ---------- Hintergrund ----------

    /**
     * Zeichnet einen animierten Hintergrund: sanfter Vertikalverlauf, drei langsam driftende
     * Leucht-Flecken in den Theme-Farben und eine dunkle Vignette an den Raendern.
     */
    public static void backdrop(GuiGraphicsExtractor g, int w, int h) {
        float t = Theme.animations ? time() : 0f;
        int accent = Theme.accent();
        int top = Theme.head(0);
        int bottom = Theme.head(3);

        // Vertikalverlauf (sehr dezent ueber den vanilla-Abdunkelungen)
        int steps = 24;
        for (int i = 0; i < steps; i++) {
            int y1 = h * i / steps;
            int y2 = h * (i + 1) / steps;
            int c = Ui.lerp(withAlpha(top, 0x22), withAlpha(bottom, 0x3A), i / (float) (steps - 1));
            g.fill(0, y1, w, y2, c);
        }

        // Driftende Leucht-Flecken
        glow(g, (int) (w * (0.30 + 0.12 * Math.sin(t * 0.35))),
                (int) (h * (0.32 + 0.10 * Math.cos(t * 0.27))), Math.max(w, h) / 3, accent, 0x30);
        glow(g, (int) (w * (0.72 + 0.10 * Math.sin(t * 0.22 + 2))),
                (int) (h * (0.62 + 0.12 * Math.cos(t * 0.31 + 1))), Math.max(w, h) / 3, top, 0x2A);
        glow(g, (int) (w * (0.55 + 0.14 * Math.cos(t * 0.19 + 4))),
                (int) (h * (0.20 + 0.08 * Math.sin(t * 0.24 + 3))), Math.max(w, h) / 4, bottom, 0x24);

        vignette(g, w, h);
    }

    /** Weicher runder Lichtfleck (konzentrische, nach aussen schwaecher werdende Rechtecke). */
    public static void glow(GuiGraphicsExtractor g, int cx, int cy, int radius, int color, int maxAlpha) {
        int layers = 7;
        for (int i = layers; i >= 1; i--) {
            int r = radius * i / layers;
            int a = maxAlpha * (layers - i + 1) / (layers * layers / 2 + 1);
            if (a <= 0) continue;
            Ui.rounded(g, cx - r, cy - r, r * 2, r * 2, withAlpha(color, a));
        }
    }

    /** Dunkelt die vier Raender ab (Tunnelblick). */
    private static void vignette(GuiGraphicsExtractor g, int w, int h) {
        int band = Math.max(24, h / 6);
        int steps = 10;
        for (int i = 0; i < steps; i++) {
            int a = 0x12 * (steps - i) / steps;
            int c = withAlpha(0xFF000000, a);
            int y = band * i / steps;
            g.fill(0, y, w, y + band / steps + 1, c);
            g.fill(0, h - y - band / steps - 1, w, h - y, c);
        }
    }

    // ---------- Panel ----------

    /**
     * Weiches Leuchten rund um ein Panel; pulsiert langsam und flammt beim Öffnen kurz auf.
     * @param intro 0..1 Oeffnungs-Fortschritt (1 = fertig)
     */
    public static void panelGlow(GuiGraphicsExtractor g, int x, int y, int w, int h, float intro) {
        int accent = Theme.accent();
        float pulse = Theme.animations ? 0.5f + 0.5f * (float) Math.sin(time() * 1.6) : 0.6f;
        float open = 1f - intro; // am Anfang (intro klein) extra hell
        int layers = 6;
        for (int i = 1; i <= layers; i++) {
            int a = Math.round((14 + 26 * open) * (1f - i / (float) (layers + 1)) * (0.6f + 0.4f * pulse));
            if (a <= 0) continue;
            Ui.rounded(g, x - i, y - i, w + i * 2, h + i * 2, withAlpha(accent, a));
        }
    }

    /** Kurzes Aufleuchten ueber dem Panel beim Öffnen. intro 0..1 (1 = vorbei). */
    public static void openFlash(GuiGraphicsExtractor g, int x, int y, int w, int h, float intro) {
        if (!Theme.animations || intro >= 1f) return;
        int a = Math.round((1f - intro) * (1f - intro) * 0x40);
        if (a <= 0) return;
        Ui.fill(g, x, y, x + w, y + h, withAlpha(Theme.accent(), a));
    }

    /** Wandernder Glanz entlang einer waagerechten Linie (z.B. unter dem Titel). */
    public static void sweep(GuiGraphicsExtractor g, int x, int y, int w, int height, int color) {
        if (!Theme.animations) {
            g.fill(x, y, x + w, y + height, withAlpha(color, 0x30));
            return;
        }
        float p = (time() * 0.4f) % 1f;
        int cx = x + Math.round(p * w);
        int half = Math.max(12, w / 6);
        for (int i = -half; i <= half; i++) {
            int px = cx + i;
            if (px < x || px >= x + w) continue;
            int a = Math.round(0x50 * (1f - Math.abs(i) / (float) half));
            g.fill(px, y, px + 1, y + height, withAlpha(color, a));
        }
    }

    // ---------- Helfer ----------

    /** Farbe mit neuem Alpha (0..255), multipliziert mit der globalen Ui.alpha. */
    public static int withAlpha(int color, int alpha) {
        int a = Math.round(Math.min(255, Math.max(0, alpha)) * Math.max(0f, Math.min(1f, Ui.alpha)));
        return (a << 24) | (color & 0xFFFFFF);
    }
}
