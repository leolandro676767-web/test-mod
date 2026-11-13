package de.test;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Gemeinsame visuelle Effekte fuer alle Menues. Alles Weiche (Lichtwolken, Schatten,
 * Leuchten, Abdunkelung der Ecken) kommt aus drei kleinen Texturen, die gestreckt und
 * eingefaerbt werden - dadurch sind die Uebergaenge wirklich stufenlos und es werden pro
 * Bild nur wenige Elemente gezeichnet. Alles reagiert auf Theme.animations.
 */
public final class Fx {
    private Fx() {
    }

    private static final Identifier GLOW = Identifier.fromNamespaceAndPath("test", "textures/gui/glow.png");
    private static final Identifier VIGNETTE = Identifier.fromNamespaceAndPath("test", "textures/gui/vignette.png");
    private static final Identifier SOFT = Identifier.fromNamespaceAndPath("test", "textures/gui/soft.png");
    private static final int GLOW_SIZE = 256;
    private static final int VIGNETTE_SIZE = 128;
    private static final int SOFT_SIZE = 128;   // Kantenlaenge der Textur
    private static final int SOFT_MARGIN = 32;  // so weit laeuft der weiche Rand nach aussen
    private static final int SOFT_SLICE = 56;   // Groesse der Eckstuecke (Rand + Rundung)

    private static final long START = System.currentTimeMillis();

    /** Sekunden seit Spielstart als fortlaufende Phase (fuer Wellen/Drift). */
    public static float time() {
        return (System.currentTimeMillis() - START) / 1000f;
    }

    // ---------- Texturen ----------

    /** Zeichnet einen Ausschnitt (u, v, sw, sh) einer Textur gestreckt in ein Rechteck; Kommazahlen erlaubt. */
    private static void part(GuiGraphicsExtractor g, Identifier id, float x, float y, float w, float h,
            int u, int v, int sw, int sh, int size, int color) {
        if ((color >>> 24) == 0 || w <= 0f || h <= 0f) return;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(w / sw, h / sh);
        g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, (float) u, (float) v, sw, sh, size, size, color);
        pose.popMatrix();
    }

    /** Weicher runder Lichtfleck um (cx, cy) mit Radius r; color enthaelt die Deckkraft der Mitte. */
    public static void glow(GuiGraphicsExtractor g, float cx, float cy, float r, int color) {
        part(g, GLOW, cx - r, cy - r, r * 2f, r * 2f, 0, 0, GLOW_SIZE, GLOW_SIZE, GLOW_SIZE, color);
    }

    /**
     * Weiches Leuchten bzw. weicher Schatten um ein Rechteck mit runden Ecken. Der Rand laeuft
     * spread Pixel nach aussen stufenlos aus. Die Mitte bleibt frei (dort liegt das Panel).
     */
    public static void soft(GuiGraphicsExtractor g, int x, int y, int w, int h, float spread, int color) {
        if ((color >>> 24) == 0) return;
        float s = Math.min(spread, Math.min(w, h) / 1.5f);
        if (s < 1f) return;
        float k = s / SOFT_MARGIN;        // GUI-Pixel je Textur-Pixel
        float c = SOFT_SLICE * k;         // Groesse eines Eckstuecks auf dem Bildschirm
        float x0 = x - s, y0 = y - s, x3 = x + w + s, y3 = y + h + s;
        float x1 = x0 + c, x2 = x3 - c, y1 = y0 + c, y2 = y3 - c;
        int sl = SOFT_SLICE;
        int far = SOFT_SIZE - SOFT_SLICE;
        int mid = SOFT_SIZE - 2 * SOFT_SLICE;
        // vier Ecken
        part(g, SOFT, x0, y0, c, c, 0, 0, sl, sl, SOFT_SIZE, color);
        part(g, SOFT, x2, y0, c, c, far, 0, sl, sl, SOFT_SIZE, color);
        part(g, SOFT, x0, y2, c, c, 0, far, sl, sl, SOFT_SIZE, color);
        part(g, SOFT, x2, y2, c, c, far, far, sl, sl, SOFT_SIZE, color);
        // vier Kanten (gestreckt)
        part(g, SOFT, x1, y0, x2 - x1, c, sl, 0, mid, sl, SOFT_SIZE, color);
        part(g, SOFT, x1, y2, x2 - x1, c, sl, far, mid, sl, SOFT_SIZE, color);
        part(g, SOFT, x0, y1, c, y2 - y1, 0, sl, sl, mid, SOFT_SIZE, color);
        part(g, SOFT, x2, y1, c, y2 - y1, far, sl, sl, mid, SOFT_SIZE, color);
    }

    // ---------- Hintergrund ----------

    /**
     * Ruhiger, animierter Hintergrund: ein stufenloser Farbverlauf, drei grosse weiche
     * Lichtwolken in den Theme-Farben, die langsam treiben, ein paar aufsteigende Lichtpunkte
     * und eine sanfte, runde Abdunkelung zu den Ecken hin.
     */
    public static void backdrop(GuiGraphicsExtractor g, int w, int h) {
        float t = Theme.animations ? time() : 0f;
        int accent = Theme.accent();
        int first = Theme.headAt(0f);
        int last = Theme.headAt(1f);

        g.fillGradient(0, 0, w, h, withAlpha(first, 0x1C), withAlpha(last, 0x36));

        float big = Math.max(w, h);
        glow(g, w * (0.26f + 0.08f * (float) Math.sin(t * 0.21)),
                h * (0.36f + 0.10f * (float) Math.cos(t * 0.17)),
                big * (0.42f + 0.03f * (float) Math.sin(t * 0.5)), withAlpha(first, 0x3C));
        glow(g, w * (0.76f + 0.07f * (float) Math.sin(t * 0.15 + 2)),
                h * (0.66f + 0.10f * (float) Math.cos(t * 0.19 + 1)),
                big * (0.40f + 0.03f * (float) Math.cos(t * 0.43 + 1)), withAlpha(last, 0x40));
        glow(g, w * (0.54f + 0.10f * (float) Math.cos(t * 0.12 + 4)),
                h * (0.10f + 0.06f * (float) Math.sin(t * 0.16 + 3)),
                big * (0.30f + 0.02f * (float) Math.sin(t * 0.37 + 2)), withAlpha(accent, 0x24));

        particles(g, w, h, t, accent);
        part(g, VIGNETTE, 0, 0, w, h, 0, 0, VIGNETTE_SIZE, VIGNETTE_SIZE, VIGNETTE_SIZE, withAlpha(0xFF000000, 0x78));
    }

    /** Wenige, langsam aufsteigende Lichtpunkte, die weich ein- und ausblenden. */
    private static void particles(GuiGraphicsExtractor g, int w, int h, float t, int accent) {
        int count = Math.max(10, Math.min(22, w * h / 20000));
        float span = h * 1.2f;
        for (int i = 0; i < count; i++) {
            float rx = hash(i * 3 + 1);
            float ry = hash(i * 3 + 2);
            float rs = hash(i * 3 + 3);
            float speed = 4f + rs * 7f;                        // Pixel pro Sekunde nach oben
            float p = ((t * speed + ry * span) % span) / span; // 0 = unten, 1 = oben
            float y = h + 6 - p * span;
            float x = rx * w + 6f * (float) Math.sin(t * (0.3f + rs * 0.4f) + i);
            float life = (float) Math.sin(Math.PI * Math.min(1f, Math.max(0f, p)));
            float twinkle = 0.7f + 0.3f * (float) Math.sin(t * (1.1f + rs) + i * 1.7f);
            float a = life * twinkle;
            if (a < 0.04f) continue;
            int col = i % 3 == 0 ? 0xFFFFFFFF : Ui.lerp(accent, 0xFFFFFFFF, 0.35f);
            float size = 1.2f + rs * 1.4f;
            glow(g, x, y, size * 4f, withAlpha(col, Math.round(0x22 * a)));
            glow(g, x, y, size, withAlpha(col, Math.round(0xC0 * a)));
        }
    }

    /** Pseudo-Zufall 0..1, immer gleich fuer dieselbe Zahl. */
    private static float hash(int n) {
        int x = n * 0x45d9f3b;
        x = ((x >>> 16) ^ x) * 0x45d9f3b;
        x = (x >>> 16) ^ x;
        return (x & 0xFFFF) / 65535f;
    }

    // ---------- Panel ----------

    /** Weicher Schlagschatten unter einem Panel. */
    public static void shadow(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        soft(g, x, y + 3, w, h, 14f, withAlpha(0xFF000000, 0x74));
    }

    /**
     * Dezentes Leuchten in der Akzentfarbe um ein Panel; beim Oeffnen kurz kraeftiger.
     * @param intro 0..1 Oeffnungs-Fortschritt (1 = fertig)
     */
    public static void panelGlow(GuiGraphicsExtractor g, int x, int y, int w, int h, float intro) {
        int a = Math.round(0x26 + 0x4A * (1f - intro));
        soft(g, x, y, w, h, 11f, withAlpha(Theme.accent(), a));
    }

    /** Kurzes, weiches Aufleuchten ueber dem Panel beim Oeffnen. intro 0..1 (1 = vorbei). */
    public static void openFlash(GuiGraphicsExtractor g, int x, int y, int w, int h, float intro) {
        if (!Theme.animations || intro >= 1f) return;
        int a = Math.round((1f - intro) * (1f - intro) * 0x30);
        if (a <= 0) return;
        Ui.roundRect(g, x, y, w, h, Ui.RADIUS, withAlpha(Theme.accent(), a));
    }

    // ---------- Helfer ----------

    /** Farbe mit neuem Alpha (0..255), multipliziert mit der globalen Ui.alpha. */
    public static int withAlpha(int color, int alpha) {
        int a = Math.round(Math.min(255, Math.max(0, alpha)) * Math.max(0f, Math.min(1f, Ui.alpha)));
        return (a << 24) | (color & 0xFFFFFF);
    }
}
