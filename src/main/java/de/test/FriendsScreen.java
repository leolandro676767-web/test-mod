package de.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Einstellungen der Freundesliste: Freunde eintragen/entfernen und festlegen, was fuer Freunde gilt. */
public class FriendsScreen extends Form {
    /** Name im Eingabefeld (bleibt beim Neuaufbau des Menues erhalten). */
    private static String pending = "";

    public FriendsScreen(Screen parent) {
        super(Component.literal("Freunde"), parent);
    }

    @Override
    protected void build() {
        description("Freunde werden nicht angegriffen und überall in ihrer eigenen Farbe angezeigt. "
                + "Mittelklick auf einen Spieler fügt ihn hinzu oder entfernt ihn wieder.");

        section("Freundesliste");
        text("Spielername", () -> pending, v -> pending = v, "", 32, () -> "z.B. Notch");
        wideButton("Hinzufügen", true, () -> {
            if (Friends.add(pending)) pending = "";
            this.rebuildWidgets();
        }, true);
        Set<String> online = onlineNames();
        List<String> friends = Friends.all();
        for (String n : friends) {
            boolean on = online.contains(n.toLowerCase(Locale.ROOT));
            nameRow(n, on ? Friends.color() : Ui.MUTED, "Entfernen", () -> Friends.remove(n));
        }
        note(() -> Friends.count() == 0
                ? "Noch keine Freunde. Trag oben einen Namen ein, wähl unten einen Spieler aus der Nähe oder "
                        + "klick einen Spieler mit dem Mausrad an."
                : "Leuchtende Namen sind gerade online, graue nicht.");

        section("Spieler in der Nähe");
        List<Player> nearby = nearbyPlayers();
        for (Player pl : nearby) {
            String n = pl.getGameProfile().name();
            int dist = this.minecraft.player == null ? 0 : Math.round(pl.distanceTo(this.minecraft.player));
            nameRow(n + "  " + dist + "m", Ui.TEXT, "Hinzufügen", () -> Friends.add(n));
        }
        if (nearby.isEmpty()) note("Gerade ist kein anderer Spieler in der Nähe.");

        section("Anzeige");
        hex("Freundesfarbe", () -> Friends.color, v -> Friends.color = v);
        toggle("ESP in Freundesfarbe", () -> Friends.showEsp, v -> Friends.showEsp = v, true);
        toggle("Tracers in Freundesfarbe", () -> Friends.showTracers, v -> Friends.showTracers = v, true);
        toggle("Chams in Freundesfarbe", () -> Friends.showChams, v -> Friends.showChams = v, true);
        toggle("Nametags in Freundesfarbe", () -> Friends.showNametags, v -> Friends.showNametags = v, true);
        toggle("Better Tab: Freunde oben und farbig", () -> Friends.showTab, v -> Friends.showTab = v, true);
        note("Leere Freundesfarbe = Mintgrün.");

        section("Schutz");
        toggle("Nicht angreifen (Kill Aura, Auto Clicker, Multitask)", () -> Friends.noAttack, v -> Friends.noAttack = v, true);
        toggle("Auch eigene Schläge abfangen", () -> Friends.blockHits, v -> Friends.blockHits = v, false);
        toggle("Auto Wasp fliegt nicht zu Freunden", () -> Friends.noChase, v -> Friends.noChase = v, true);
        toggle("City Esp lässt Freunde aus", () -> Friends.noCity, v -> Friends.noCity = v, true);
        note("Mit „Auch eigene Schläge abfangen“ triffst du Freunde auch mit der Hand nicht mehr. "
                + "Message Aura und Debris Miner haben dazu eigene Einstellungen.");

        section("Bedienung");
        toggle("Mittelklick auf Spieler: hinzufügen oder entfernen", () -> Friends.middleClick,
                v -> Friends.middleClick = v, true);
        slider("Mittelklick-Reichweite (Chunks)", 1, 16, () -> Friends.middleClickChunks,
                v -> Friends.middleClickChunks = v, 6);
        toggle("Melden, wenn ein Freund online oder offline geht", () -> Friends.notifyOnline,
                v -> Friends.notifyOnline = v, true);
        toggle("Melden, wenn ein Freund in Sichtweite kommt", () -> Friends.notifyNear,
                v -> Friends.notifyNear = v, false);

        footer(() -> Friends.enabled, v -> Friends.enabled = v);
    }

    private Set<String> onlineNames() {
        Set<String> out = new HashSet<>();
        var conn = this.minecraft == null ? null : this.minecraft.getConnection();
        if (conn == null) return out;
        for (var info : conn.getOnlinePlayers()) out.add(info.getProfile().name().toLowerCase(Locale.ROOT));
        return out;
    }

    /** Andere Spieler in Sichtweite, die noch keine Freunde sind (die naechsten zuerst, hoechstens 12). */
    private List<Player> nearbyPlayers() {
        List<Player> out = new ArrayList<>();
        var mc = this.minecraft;
        if (mc == null || mc.player == null || mc.level == null) return out;
        for (Player o : mc.level.players()) {
            if (o == mc.player || PlayerMods.isFake(o)) continue;
            if (Friends.listed(o.getGameProfile().name())) continue;
            out.add(o);
        }
        out.sort((a, b) -> Double.compare(a.distanceToSqr(mc.player), b.distanceToSqr(mc.player)));
        return out.size() > 12 ? new ArrayList<>(out.subList(0, 12)) : out;
    }
}

/** Das Menue selbst anpassen: Farben, Groesse, Logo, Animationen - mit Live-Vorschau. */
class DesignScreen extends Form {
    public DesignScreen(Screen parent) {
        super(Component.literal("Design anpassen"), parent);
    }

    @Override
    protected void build() {
        description("So sieht dein Menü aus. Änderungen siehst du sofort.");
        wideButton("Sprache / Language: " + Lang.t(Lang.NAMES[Lang.language]), false,
                () -> this.minecraft.gui.setScreen(new LanguageScreen(this)));

        section("Module");
        choice("Modus", Levels.NAMES, () -> Levels.level, Levels::set, Levels.STANDARD);
        note("Anfänger: nur die einfachsten Module. Standard: Anzeige und Komfort, nichts mit unfairem Vorteil. "
                + "Experte: alle Module. Was nicht dabei ist, wird ausgeschaltet.");

        section("Anzeige im Spiel");
        toggle("Aktive Module anzeigen", () -> Theme.hudList, v -> Theme.hudList = v, true);
        choice("Gr\u00f6\u00dfe der Liste", Theme.HUD_SIZE_NAMES, () -> Theme.hudListSize, v -> Theme.hudListSize = v, 1);
        choice("Position", Theme.HUD_CORNER_NAMES, () -> Theme.hudCorner, v -> Theme.hudCorner = v, 0);
        note("Zeigt alle Module, die gerade an sind, in der gew\u00e4hlten Ecke.");

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
        toggle("Weniger Effekte", () -> Theme.lowFx, v -> Theme.lowFx = v, false);
        note("Animationen aus = alles erscheint sofort. Weniger Effekte = ruhigerer Hintergrund "
                + "ohne Partikel, f\u00fcr schw\u00e4chere PCs.");

        section("Leistung");
        toggle("Leistungs-Modus", () -> RenderMods.perfMode, v -> RenderMods.perfMode = v, false);
        note("Für schwache PCs: Bei Wall Hack, Block Esp, Storage Esp, Hole Esp, Tunnel Esp, Void Esp, Base Finder "
                + "und den ESP-Blöcken werden zusammenhängende Blöcke zu einem großen Kasten. Es werden höchstens "
                + "100 Kästen gezeichnet, ohne Scan-Linie und Füllung, und die Suche läuft langsamer.");

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

/**
 * Auswahl fuer einen Platz der Inventar-Aufteilung: eine Art, ein haeufiges Item oder ein beliebiges Item.
 * (Minecrafts Item-Klasse wird hier voll ausgeschrieben, weil "Item" in Form schon ein eigener Name ist.)
 */
class InvSlotScreen extends Form {
    private final int slot;

    public InvSlotScreen(Screen parent, int slot) {
        super(Component.literal(InvLayoutScreen.slotName(slot)), parent);
        this.slot = slot;
    }

    @Override
    protected void build() {
        String cur = InvLayout.RULES[slot];
        description("Was soll hier liegen? Bei Werkzeugen nimmt er immer das beste,\n"
                + "bei Essen das nahrhafteste, sonst den größten Stapel.");
        section("Werkzeuge, Blöcke und Essen");
        for (String[] c : InvLayout.CATS) {
            wideButton(c[1], c[0].equals(cur), () -> choose(c[0]), true);
        }
        section("Gegenstände");
        for (String[] q : InvLayout.QUICK) {
            String rule = "item:" + q[0];
            wideButton(q[1], rule.equals(cur), () -> choose(rule), true);
        }
        section("Sonstiges");
        boolean other = cur != null && cur.startsWith("item:") && !isQuick(cur);
        wideButton(other ? "Anderes Item: " + InvLayout.name(cur) : "Anderes Item auswählen", other,
                this::pickItem, true);
        wideButton("Leer lassen (egal, was hier liegt)", cur == null, () -> choose(null), true);
        footer(null, null);
    }

    private static boolean isQuick(String rule) {
        for (String[] q : InvLayout.QUICK) {
            if (rule.equals("item:" + q[0])) return true;
        }
        return false;
    }

    private void choose(String rule) {
        InvLayout.RULES[slot] = rule;
        TestConfig.save();
        onClose();
    }

    /** Beliebiges Item aus der Liste aller Items (genau eins je Platz). */
    private void pickItem() {
        PickerScreen.Selection one = new PickerScreen.Selection() {
            @Override
            public boolean contains(Object value) {
                return value instanceof net.minecraft.world.item.Item it
                        && ("item:" + InvLayout.id(it)).equals(InvLayout.RULES[slot]);
            }

            @Override
            public void add(Object value) {
                if (value instanceof net.minecraft.world.item.Item it) {
                    InvLayout.RULES[slot] = "item:" + InvLayout.id(it);
                    TestConfig.save();
                }
            }

            @Override
            public void remove(Object value) {
                if (contains(value)) {
                    InvLayout.RULES[slot] = null;
                    TestConfig.save();
                }
            }

            @Override
            public List<Object> values() {
                List<Object> out = new ArrayList<>();
                net.minecraft.world.item.Item it = InvLayout.ruleItem(InvLayout.RULES[slot]);
                if (it != null) out.add(it);
                return out;
            }
        };
        this.minecraft.gui.setScreen(new PickerScreen(this.parent,
                InvLayoutScreen.slotName(slot) + ": Item wählen", PickerScreen.Kind.ITEM, one, null, null));
    }
}

/**
 * Test-Inventar zum Festlegen der Aufteilung: oben das Inventar, darunter die Hotbar und links daneben
 * die zweite Hand. Ein Klick auf einen Platz waehlt, was dort hin soll.
 */
class InvLayoutScreen extends Form {
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

/** Einstellungen fuer das ESP. */
class EspScreen extends Form {
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
        toggle("Zuschauer zeigen", () -> Esp.spectators, v -> Esp.spectators = v, true);
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
        toggle("Leistungs-Modus (für schwache PCs)", () -> RenderMods.perfMode, v -> RenderMods.perfMode = v, false);
        toggle("Linien", () -> Esp.tracers, v -> Esp.tracers = v, false);
        toggle("Name + Entfernung", () -> Esp.labels, v -> Esp.labels = v, true);

        section("Farben (leer = Standard)");
        hex("Spieler", () -> Esp.colPlayer, v -> Esp.colPlayer = v);
        hex("Mobs", () -> Esp.colMob, v -> Esp.colMob = v);
        hex("Items", () -> Esp.colItem, v -> Esp.colItem = v);
        hex("Bl\u00f6cke", () -> Esp.colBlock, v -> Esp.colBlock = v);

        section("Reichweite");
        slider("Reichweite (Chunks)", 1, 32, () -> Esp.rangeChunks, v -> Esp.rangeChunks = v, 6);
        slider("Block-Reichweite (Chunks)", 1, 12, () -> Esp.blockChunks, v -> {
            Esp.blockChunks = v;
            Esp.rescan();
        }, 6);

        wideButton("Mobs wählen (" + count(Esp.mobTypes.size(), Lang.t("alle")) + ")", false,
                () -> this.minecraft.gui.setScreen(new PickerScreen(this, "ESP: Mobs",
                        PickerScreen.Kind.ENTITY, PickerScreen.ofSet(Esp.mobTypes), null, null)));
        wideButton("Items wählen (" + count(Esp.itemTypes.size(), Lang.t("alle")) + ")", false,
                () -> this.minecraft.gui.setScreen(new PickerScreen(this, "ESP: Items",
                        PickerScreen.Kind.ITEM, PickerScreen.ofSet(Esp.itemTypes), null, null)));
        text("Items nie zeigen, wenn der Name enthält", () -> RenderMods.itemHideNames,
                v -> RenderMods.itemHideNames = v, "", 400, () -> "Bruchstein, Erde, Samen");
        wideButton("Blöcke wählen (" + count(Esp.blockTypes.size(), Lang.t("keine")) + ")", false,
                () -> this.minecraft.gui.setScreen(new PickerScreen(this, "ESP: Blöcke",
                        PickerScreen.Kind.BLOCK, PickerScreen.ofSet(Esp.blockTypes), null, null)));
        note("Leere Mob- oder Item-Auswahl = alle anzeigen.");
        note(Esp::blockStatus);

        footer(() -> Esp.enabled, v -> Esp.enabled = v);
    }

    @Override
    public void onClose() {
        Esp.rescan();
        super.onClose();
    }
}
