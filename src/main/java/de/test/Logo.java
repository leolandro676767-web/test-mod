package de.test;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Das 1912-Logo (Pixel-Art), gezeichnet aus Rechtecken - keine Textur noetig. */
final class Logo {
    private Logo() {
    }

    static final int W = 55;
    static final int H = 17;

    /** '.' leer, '#' schwarzer Rand, a-d = Zifferfarben (1, 9, 1, 2), e-h = passende Schatten. */
    private static final String[] ROWS = {
        "...####.........########........###........########....",
        "...aaaa#........bbbbbbbb#.......ccc#.......dddddddd#...",
        ".##aaaa#.......#bbbbbbbb##....##ccc#.....##dddddddd##..",
        ".aaaaaa#e.....bbbb####bbbb#...ccccc#g....dddd####dddd#.",
        ".aaaaaa#e.....bbbb#ff#bbbb#...ccccc#g....dddd#hh#dddd#.",
        "..#aaaa#e.....bbbb#f..bbbb#f...#ccc#g....####hh..dddd#h",
        "...aaaa#e.....bbbb##.#bbbb#f....ccc#g......hh####dddd#h",
        "...aaaa#e......#bbbbbbbbbb#f....ccc#g.......#dddddd##hh",
        "...aaaa#e.......bbbbbbbbbb#f....ccc#g......##dddddd#hhh",
        "...aaaa#e.......######bbbb#f....ccc#g......dddd####hh..",
        "...aaaa#e.........fff#bbbb#f....ccc#g.....#dddd#h####..",
        "...aaaa#e..........#bbbb##ff....ccc#g....dddd##h#dddd#.",
        "..#aaaa##..........#bbbb#fff...#ccc##....dddd#hh#dddd#h",
        "aaaaaaaaaaaa#...bbbbbb##ff...cccccccccc#.dddddddddddd.h",
        "aaaaaaaaaaaa#e..bbbbbb#fff...cccccccccc#gdddddddddddd#h",
        ".###########ee..######ff......#########gg.###########hh",
        "..eeeeeeeeeeee...fffffff.......gggggggggg..hhhhhhhhhhhh"
    };

    private static final int[] PALETTE = {
        0xFFAC00A6,
        0xFF8B1AC6,
        0xFF7038E4,
        0xFF5253FE,
        0xFF380238,
        0xFF2E0443,
        0xFF260D4D,
        0xFF191853
    };

    /** Hauptfarbe der i-ten Ziffer (0..3), z.B. fuer Spaltenkoepfe. */
    static int fillColor(int i) {
        return PALETTE[Math.max(0, Math.min(3, i))];
    }

    private static int color(char c) {
        if (c == '#') return 0xFF000000;
        int i = c - 'a';
        return i >= 0 && i < PALETTE.length ? PALETTE[i] : 0;
    }

    /** Zeichnet das Logo. scale = Pixelgroesse, alpha = 0..1, dy = senkrechter Versatz. */
    static void draw(GuiGraphicsExtractor g, int x, int y, int scale, float alpha, int dy) {
        draw3d(g, x, y, scale, alpha, dy, 0f, 0f);
    }

    /**
     * 3D-Logo: die Schrift hat eine Tiefe aus mehreren dunkler werdenden Schichten. tiltX/tiltY
     * (-1..1, z.B. die Maus) bestimmen, zu welcher Seite die Tiefe zeigt - so wirkt es, als wuerde
     * das Logo sich zur Maus drehen. Dazu ein leichtes Schweben und ein Glanz, der darueber laeuft.
     */
    static void draw3d(GuiGraphicsExtractor g, int x, int y, int scale, float alpha, int dy, float tiltX, float tiltY) {
        float t = Theme.animations ? Fx.time() : 0f;
        float bob = (float) Math.sin(t * 1.4) * scale * 0.8f;
        int depth = 3 + scale * 2;                      // Anzahl Tiefenschichten
        // Tiefe zeigt von der Maus weg (das Logo "schaut" zur Maus)
        float sx = -tiltX * 0.55f * scale / 2f;
        float sy = 0.45f * scale / 2f + 0.25f * scale / 2f - tiltY * 0.35f * scale / 2f;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(0f, bob);
        // weicher Schatten unter dem Logo
        Fx.glow(g, x + W * scale / 2f + sx * depth, y + H * scale + dy + scale * 3,
                W * scale * 0.42f, Fx.withAlpha(0xFF000000, Math.round(0x50 * alpha)));
        // Tiefenschichten von hinten nach vorne
        for (int d = depth; d >= 1; d--) {
            float shade = 0.35f + 0.35f * (1f - d / (float) depth);
            pose.pushMatrix();
            pose.translate(sx * d, sy * d);
            layer(g, x, y, scale, alpha, dy, shade, 0f);
            pose.popMatrix();
        }
        // Vorderseite mit wanderndem Glanz
        float shine = Theme.animations ? (t * 0.35f) % 1.6f - 0.3f : -1f;
        layer(g, x, y, scale, alpha, dy, 1f, shine);
        pose.popMatrix();
    }

    /** Eine Schicht des Logos; shade < 1 = dunkler (Seitenflaeche), shine = Position des Glanzes (0..1). */
    private static void layer(GuiGraphicsExtractor g, int x, int y, int scale, float alpha, int dy, float shade, float shine) {
        for (int r = 0; r < ROWS.length; r++) {
            String row = ROWS[r];
            int c = 0;
            while (c < row.length()) {
                char ch = row.charAt(c);
                int start = c;
                while (c < row.length() && row.charAt(c) == ch) c++;
                if (ch == '.') continue;
                int col = color(ch);
                if (shade < 1f) col = Ui.lerp(col | 0xFF000000, 0xFF000000, 1f - shade);
                int a = Math.round(((col >>> 24) & 0xFF) * alpha);
                int x1 = x + start * scale;
                int x2 = x + c * scale;
                int y1 = y + r * scale + dy;
                int y2 = y + (r + 1) * scale + dy;
                g.fill(x1, y1, x2, y2, (a << 24) | (col & 0xFFFFFF));
                if (shine > -0.5f && shade >= 1f) {
                    // schraeger Glanzstreifen, der von links nach rechts ueber die Schrift laeuft
                    for (int px = start; px < c; px++) {
                        float pos = (px + 0.5f) / W - r / (float) H * 0.25f;
                        float f = 1f - Math.abs(pos - shine) / 0.12f;
                        if (f <= 0f) continue;
                        int sa = Math.round(0x70 * f * alpha);
                        g.fill(x + px * scale, y1, x + (px + 1) * scale, y2, (sa << 24) | 0xFFFFFF);
                    }
                }
            }
        }
    }
}
