package de.test;

/** Vom Spieler einstellbares Aussehen des Menues (wird in der Config gespeichert). */
final class Theme {
    private Theme() {
    }

    static final String[] SCHEME_NAMES = {"Logo", "Flieder", "Blau", "Gr\u00fcn", "Orange", "Rot", "Grau"};
    static final String[] SIZE_NAMES = {"Auto", "Klein", "Mittel", "Gro\u00df"};
    static final String[] WIDTH_NAMES = {"Schmal", "Normal", "Breit"};
    private static final int[] WIDTH_PX = {88, 104, 120};

    /** Kopffarben der vier Spalten je Farbschema (Schema 0 nimmt die Logo-Farben). */
    private static final int[][] BASES = {
        {0, 0, 0, 0},
        {0xFF9A74E6, 0xFF8E63D9, 0xFF8257CF, 0xFF764BC5},
        {0xFF4F7DF0, 0xFF3E6AE0, 0xFF3359CC, 0xFF2A4AB8},
        {0xFF3DBE7A, 0xFF30AC6A, 0xFF269A5B, 0xFF1D884D},
        {0xFFF59A3C, 0xFFE8862A, 0xFFD97420, 0xFFC96418},
        {0xFFE05A5A, 0xFFD14646, 0xFFC03838, 0xFFAE2C2C},
        {0xFF8A8A98, 0xFF7E7E8C, 0xFF727280, 0xFF666674}
    };
    private static final int[] ACCENTS = {
        0xFFC3A6FF, 0xFFB794F6, 0xFF8FB3FF, 0xFF8CF0B4, 0xFFFFC38A, 0xFFFF9C9C, 0xFFD0D0E0
    };

    static int scheme = 0;
    static int headHex = -1;   // -1 = Farbschema benutzen, sonst 0xRRGGBB fuer alle Spalten
    static int accentHex = -1; // -1 = Farbschema benutzen
    static boolean showLogo = true;
    static boolean animations = true;
    static int logoSize = 0;   // 0 = automatisch, 1..3 = feste Pixelgroesse
    static int colWidth = 1;

    static void clamp() {
        scheme = Math.max(0, Math.min(SCHEME_NAMES.length - 1, scheme));
        logoSize = Math.max(0, Math.min(SIZE_NAMES.length - 1, logoSize));
        colWidth = Math.max(0, Math.min(WIDTH_NAMES.length - 1, colWidth));
        if (headHex > 0xFFFFFF || headHex < -1) headHex = -1;
        if (accentHex > 0xFFFFFF || accentHex < -1) accentHex = -1;
    }

    static void reset() {
        scheme = 0;
        headHex = -1;
        accentHex = -1;
        showLogo = true;
        animations = true;
        logoSize = 0;
        colWidth = 1;
    }

    static int columnWidth() {
        return WIDTH_PX[colWidth];
    }

    static int head(int col) {
        if (headHex >= 0) return 0xFF000000 | headHex;
        if (scheme == 0) return Logo.fillColor(col);
        return BASES[scheme][Math.max(0, Math.min(3, col))];
    }

    static int accent() {
        return accentHex >= 0 ? 0xFF000000 | accentHex : ACCENTS[scheme];
    }

    private static int accentAlpha(int alpha) {
        return (alpha << 24) | (accent() & 0xFFFFFF);
    }

    static int border() {
        return accentAlpha(0x80);
    }

    static int onBg() {
        return Ui.lerp(0xE0140F1F, accentAlpha(0xE0), 0.16f);
    }

    static int hoverBg() {
        return Ui.lerp(0xE0140F1F, accentAlpha(0xE0), 0.09f);
    }

    static int btnHover() {
        return Ui.lerp(Ui.BTN, accentAlpha(0xE0), 0.12f);
    }

    /** Kopfzeile der Untermenues: Farbe der zweiten Spalte. */
    static int headTop() {
        return Ui.lerp(head(1), 0xFFFFFFFF, 0.22f);
    }

    static int headBottom() {
        return Ui.lerp(head(1), 0xFF000000, 0.18f);
    }

    static int parseHex(String s) {
        if (s.length() != 6) return -1;
        try {
            return Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
