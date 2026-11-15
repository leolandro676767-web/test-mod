package de.test;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Test-Inventar zum Festlegen der Aufteilung: oben das Inventar, darunter die Hotbar und links daneben
 * die zweite Hand. Ein Klick auf einen Platz waehlt, was dort hin soll.
 */
public class InvLayoutScreen extends Form {
    private static final int S = 20;      // Kantenlaenge eines Platzes
    private static final int G = 2;       // Abstand zwischen den Plaetzen
    private static final int GRID_W = 9 * S + 8 * G;
    private static final int HAND_GAP = 26;
    private static final int BLOCK_W = S + HAND_GAP + GRID_W;
    private static final int MAIN_Y = 12;
    private static final int HOT_LABEL_Y = MAIN_Y + 3 * (S + G) + 6;
    private static final int HOT_Y = HOT_LABEL_Y + 12;
    private static final int INFO_Y = HOT_Y + S + 8;
    private static final int ROW_H = INFO_Y + 16;

    private int hover = -1;

    public InvLayoutScreen(Screen parent) {
        super(Component.literal("Inventar sortieren"), parent);
    }

    /** Name eines Platzes fuer Anzeige und Titel. */
    static String slotName(int slot) {
        if (slot == InvLayout.OFFHAND) return "Zweite Hand";
        if (slot < 9) return "Hotbar " + (slot + 1);
        return "Inventar " + (slot - 8);
    }

    @Override
    protected void build() {
        description("Lege fest, was in welchem Platz liegen soll. Mit der Taste für\n"
                + "„Inventar sortieren“ räumt er dein Inventar dann genau so ein.\n"
                + "Bei Werkzeugen nimmt er immer das beste (z.B. Diamant vor Eisen).");
        section("Aufteilung");
        customRow(ROW_H, y -> (g, ox, oy, mx, my) -> drawGrid(g, ox, oy + y, mx, my), this::addSlots);
        wideButton("Aus meinem Inventar übernehmen", false, () -> {
            InvLayout.fromInventory(this.minecraft.player);
            TestConfig.save();
            this.rebuildWidgets();
        }, true);
        wideButton("Vorschlag laden (Werkzeuge vorne)", false, () -> {
            InvLayout.suggestion();
            TestConfig.save();
            this.rebuildWidgets();
        }, true);
        wideButton("Alles leeren", false, () -> {
            java.util.Arrays.fill(InvLayout.RULES, null);
            TestConfig.save();
            this.rebuildWidgets();
        }, true);

        section("Sortieren");
        toggle("Gleiche Stapel vorher zusammenlegen", () -> InvLayout.mergeStacks, v -> InvLayout.mergeStacks = v, true);
        note(InvLayoutScreen::keyNote);
        wideButton("Jetzt sortieren", true, () -> {
            InvLayout.request(this.minecraft);
            onClose();
        }, true);
        footer(null, null);
    }

    private static String keyNote() {
        Integer k = Keybinds.get(Keybinds.SORT_NAME);
        if (k != null) {
            return "Taste: " + Keybinds.keyName(k) + ". Ändern im Hauptmenü mit „Tasten zuweisen“.";
        }
        return "Noch keine Taste: im Hauptmenü „Tasten zuweisen“ anklicken, dann „Inventar sortieren“ "
                + "und die gewünschte Taste drücken.";
    }

    // ---------- Raster ----------

    private int blockX() {
        return contentX() + Math.max(0, (contentWidth() - BLOCK_W) / 2);
    }

    /** Position {x, y} eines Platzes relativ zur Zeile. */
    private int[] pos(int slot) {
        int gx = blockX() + S + HAND_GAP;
        if (slot == InvLayout.OFFHAND) return new int[] {blockX(), HOT_Y};
        if (slot < 9) return new int[] {gx + slot * (S + G), HOT_Y};
        int i = slot - 9;
        return new int[] {gx + (i % 9) * (S + G), MAIN_Y + (i / 9) * (S + G)};
    }

    private void addSlots(int y) {
        for (int slot = 0; slot < 36; slot++) addSlot(slot, y);
        addSlot(InvLayout.OFFHAND, y);
    }

    private void addSlot(int slot, int y) {
        int[] p = pos(slot);
        hotspot(p[0], y + p[1], S, S, () -> this.minecraft.gui.setScreen(new InvSlotScreen(this, slot)));
    }

    private void drawGrid(GuiGraphicsExtractor g, int ox, int oy, int mx, int my) {
        int gx = ox + blockX() + S + HAND_GAP;
        Ui.text(g, this.font, "Inventar", gx, oy, Ui.MUTED, true);
        Ui.text(g, this.font, "Hotbar", gx, oy + HOT_LABEL_Y, Ui.MUTED, true);
        String hand = Lang.t("2. Hand");
        Ui.text(g, this.font, hand, ox + blockX() + (S - this.font.width(hand)) / 2, oy + HOT_LABEL_Y, Ui.MUTED, true);

        hover = -1;
        for (int slot = 0; slot < 36; slot++) drawSlot(g, ox, oy, mx, my, slot);
        drawSlot(g, ox, oy, mx, my, InvLayout.OFFHAND);

        String info;
        if (hover >= 0) {
            String rule = InvLayout.RULES[hover];
            info = Lang.t(slotName(hover)) + ": " + Lang.t(rule == null ? "egal (wird nicht umgeräumt)" : InvLayout.name(rule))
                    + Lang.t("   ·   Klicken zum Ändern");
        } else {
            info = "Klicke auf einen Platz, um festzulegen, was dort hin soll.";
        }
        Ui.fitText(g, this.font, info, ox + contentX(), oy + INFO_Y, contentWidth(), hover >= 0 ? Ui.TEXT : Ui.MUTED, true);
    }

    private void drawSlot(GuiGraphicsExtractor g, int ox, int oy, int mx, int my, int slot) {
        int[] p = pos(slot);
        int x = ox + p[0];
        int y = oy + p[1];
        boolean over = mx >= x && mx < x + S && my >= y && my < y + S;
        if (over) hover = slot;
        String rule = InvLayout.RULES[slot];
        float hv = Ui.ease("invslot" + slot, 0, over ? 1f : 0f, 0.35f);
        int bg = rule != null ? Ui.withA(Theme.accent(), 0x38) : 0x1CFFFFFF;
        Ui.roundRect(g, x, y, S, S, 3, bg);
        if (hv > 0.02f) Ui.roundRect(g, x, y, S, S, 3, Ui.withA(0xFFFFFF, Math.round(0x30 * hv)));
        if (slot < 9 && rule == null) {
            String n = Integer.toString(slot + 1);
            Ui.text(g, this.font, n, x + (S - this.font.width(n)) / 2, y + 6, 0x66FFFFFF, false);
        }
        if (rule == null) return;
        ItemStack icon = InvLayout.icon(rule);
        if (!icon.isEmpty()) {
            try {
                g.fakeItem(icon, x + 2, y + 2);
                return;
            } catch (RuntimeException e) {
                // ohne Symbol weiter unten
            }
        }
        String ch = InvLayout.name(rule).substring(0, 1);
        Ui.text(g, this.font, ch, x + (S - this.font.width(ch)) / 2, y + 6, Ui.TEXT, true);
    }
}
