package de.test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Allgemeiner Auswahl-Bildschirm fuer jedes Modul: links alle Items bzw. alle Bloecke des Spiels
 * (mit Suche und Vorschau), rechts die aktuelle Auswahl. Die Auswahl selbst liegt in einer {@link Selection}.
 */
public class PickerScreen extends Screen {
    /** Was zur Auswahl steht: jedes Item oder jeder Block aus den Spiel-Registern. */
    public enum Kind { ITEM, BLOCK, ENTITY }

    /** Speicherort der Auswahl eines Moduls (Mehrfach- oder Einzelauswahl). */
    public interface Selection {
        boolean contains(Object value);

        void add(Object value);

        void remove(Object value);

        List<Object> values();
    }

    /** Mehrfachauswahl in einem Set, z.B. die Drop-Items. */
    @SuppressWarnings("unchecked")
    public static <T> Selection ofSet(Set<T> set) {
        return new Selection() {
            @Override
            public boolean contains(Object value) {
                return set.contains(value);
            }

            @Override
            public void add(Object value) {
                set.add((T) value);
            }

            @Override
            public void remove(Object value) {
                set.remove(value);
            }

            @Override
            public List<Object> values() {
                return new ArrayList<Object>(set);
            }
        };
    }

    /** Ein Eintrag der Liste; iconItem ist das Item fuer die Vorschau (bei Bloecken ohne Item: Luft). */
    private record Option(Object value, String name, String id, Item iconItem) {
    }

    private static final int ROW = 22;
    private static final int PAD = 10;
    private static final int LANE = 7;   // Platz fuer den Scrollbalken je Liste
    private static final int MID = 13;   // Abstand zwischen den beiden Listen (mit Trennlinie)

    private static final Map<Kind, List<Option>> OPTIONS = new EnumMap<>(Kind.class);
    private static final Map<Kind, Map<Object, Option>> LOOKUP = new EnumMap<>(Kind.class);
    /** Breite des laengsten Namens je Art (einmal gemessen), damit die Listen breit genug werden. */
    private static final Map<Kind, Integer> NAME_W = new EnumMap<>(Kind.class);
    private static final Map<Item, ItemStack> ICONS = new HashMap<>();
    private static final Set<Item> BAD_ICONS = new HashSet<>();

    private final Screen parent;
    private final Kind kind;
    private final Selection selection;
    private final BooleanSupplier active;
    private final Consumer<Boolean> setActive;
    private final List<Option> ordered = new ArrayList<>();
    private final List<Option> filtered = new ArrayList<>();
    private final List<Button> addBtns = new ArrayList<>();
    private final List<Button> delBtns = new ArrayList<>();
    private final Map<AbstractWidget, Integer> baseY = new LinkedHashMap<>();
    private final long openedAt = System.currentTimeMillis();
    private boolean closing;
    private boolean closed;
    private long closeAt;
    private String search = "";
    private int scrollL = 0;
    private int scrollR = 0;
    private int rows;
    private int px, py, pw, ph, lx, rx, leftW, rightW, listY, capY, footY;
    private EditBox searchBox;
    private Button activeBtn, backBtn;

    /**
     * @param active    optionaler "Aktiv"-Schalter des Moduls (null = keiner)
     * @param setActive zugehoeriger Setter
     */
    public PickerScreen(Screen parent, String title, Kind kind, Selection selection,
                        BooleanSupplier active, Consumer<Boolean> setActive) {
        super(Component.literal(title));
        this.parent = parent;
        this.kind = kind;
        this.selection = selection;
        this.active = active;
        this.setActive = setActive;
    }

    // ---------- Daten ----------

    private static String shortId(String full) {
        int i = full.indexOf(':');
        return (i >= 0 ? full.substring(i + 1) : full).toLowerCase(Locale.ROOT);
    }

    /** Alle Eintraege einer Art, einmal aufgebaut und nach Name sortiert. */
    private static List<Option> options(Kind kind) {
        List<Option> cached = OPTIONS.get(kind);
        if (cached != null) return cached;
        List<Option> list = new ArrayList<>();
        if (kind == Kind.ITEM) {
            for (Item it : BuiltInRegistries.ITEM) {
                if (it == Items.AIR) continue;
                list.add(new Option(it, Component.translatable(it.getDescriptionId()).getString(),
                        shortId(BuiltInRegistries.ITEM.getKey(it).toString()), it));
            }
        } else if (kind == Kind.ENTITY) {
            for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
                Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(t);
                if (key.getPath().equals("player")) continue;
                // Vorschau ueber das passende Spawn-Ei, falls es eins gibt
                Identifier egg = Identifier.fromNamespaceAndPath(key.getNamespace(), key.getPath() + "_spawn_egg");
                Item icon = BuiltInRegistries.ITEM.containsKey(egg) ? BuiltInRegistries.ITEM.getValue(egg) : Items.AIR;
                list.add(new Option(t, t.getDescription().getString(), shortId(key.toString()), icon));
            }
        } else {
            for (Block b : BuiltInRegistries.BLOCK) {
                if (b == Blocks.AIR || b == Blocks.CAVE_AIR || b == Blocks.VOID_AIR) continue;
                list.add(new Option(b, b.getName().getString(),
                        shortId(BuiltInRegistries.BLOCK.getKey(b).toString()), b.asItem()));
            }
        }
        list.sort(Comparator.<Option, String>comparing(Option::name, String.CASE_INSENSITIVE_ORDER));
        Map<Object, Option> lookup = new HashMap<>();
        for (Option o : list) lookup.put(o.value(), o);
        OPTIONS.put(kind, list);
        LOOKUP.put(kind, lookup);
        return list;
    }

    /** Eintraege, deren Item im Inventar liegt, zuerst; danach der Rest. */
    private void buildOrdered() {
        Set<Item> inv = new LinkedHashSet<>();
        if (this.minecraft != null && this.minecraft.player != null) {
            var inventory = this.minecraft.player.getInventory();
            for (int i = 0; i < 41; i++) {
                ItemStack st = inventory.getItem(i);
                if (!st.isEmpty()) inv.add(st.getItem());
            }
        }
        List<Option> first = new ArrayList<>();
        List<Option> rest = new ArrayList<>();
        for (Option o : options(kind)) {
            (inv.contains(o.iconItem()) ? first : rest).add(o);
        }
        ordered.clear();
        ordered.addAll(first);
        ordered.addAll(rest);
    }

    private void refilter() {
        String q = search.toLowerCase(Locale.ROOT).trim();
        String qId = q.replace(' ', '_');
        filtered.clear();
        for (Option o : ordered) {
            if (q.isEmpty() || o.name().toLowerCase(Locale.ROOT).contains(q) || o.id().contains(qId)) {
                filtered.add(o);
            }
        }
        clampScroll();
    }

    private List<Option> selected() {
        Map<Object, Option> lookup = LOOKUP.get(kind);
        List<Option> out = new ArrayList<>();
        for (Object v : selection.values()) {
            Option o = lookup.get(v);
            if (o != null) out.add(o);
        }
        out.sort(Comparator.<Option, String>comparing(Option::name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private void clampScroll() {
        scrollL = Math.max(0, Math.min(scrollL, Math.max(0, filtered.size() - rows)));
        scrollR = Math.max(0, Math.min(scrollR, Math.max(0, selection.values().size() - rows)));
    }

    private Option leftOption(int r) {
        int idx = scrollL + r;
        return idx >= 0 && idx < filtered.size() ? filtered.get(idx) : null;
    }

    private Option rightOption(int r) {
        List<Option> sel = selected();
        int idx = scrollR + r;
        return idx >= 0 && idx < sel.size() ? sel.get(idx) : null;
    }

    /** Breite des laengsten Namens: danach richtet sich die Breite der Listen (nichts wird gekuerzt). */
    private int longestName() {
        Integer cached = NAME_W.get(kind);
        if (cached != null) return cached;
        int w = 0;
        for (Option o : options(kind)) w = Math.max(w, this.font.width(o.name()));
        NAME_W.put(kind, w);
        return w;
    }

    // ---------- Vorschau ----------

    private static ItemStack icon(Item it) {
        return ICONS.computeIfAbsent(it, Item::getDefaultInstance);
    }

    /** Zeichnet die Vorschau; ohne Item (z.B. Portal-Block) oder bei einem Fehler kommt ein Ersatzkaestchen mit Anfangsbuchstabe. */
    private void drawIcon(GuiGraphicsExtractor g, Option o, int x, int y) {
        Item it = o.iconItem();
        if (it != Items.AIR && !BAD_ICONS.contains(it)) {
            try {
                g.fakeItem(icon(it), x, y);
                return;
            } catch (RuntimeException e) {
                BAD_ICONS.add(it);
            }
        }
        String ch = o.name().isEmpty() ? "?" : o.name().substring(0, 1).toUpperCase(Locale.ROOT);
        Ui.fill(g, x, y, x + 16, y + 16, 0xFF3A3450);
        Ui.text(g, this.font, ch, x + (16 - this.font.width(ch)) / 2, y + 4, Ui.MUTED, true);
    }

    // ---------- Aufbau ----------

    @Override
    protected void init() {
        GuiScale.apply();
        this.width = this.minecraft.getWindow().getGuiScaledWidth();
        this.height = this.minecraft.getWindow().getGuiScaledHeight();
        options(kind);
        ICONS.clear();
        BAD_ICONS.clear();
        buildOrdered();
        addBtns.clear();
        delBtns.clear();

        rows = Math.max(4, Math.min(12, (this.height - 150) / ROW));
        // Jede Liste: Vorschau, Name (in voller Laenge), Knopf, Scrollbalken
        int colNeed = 4 + 16 + 6 + longestName() + 8 + 20 + 2 + LANE;
        int want = Math.max(360, 2 * colNeed + MID + 2 * PAD);
        pw = Math.min(want, this.width - 12);
        int inner = pw - 2 * PAD;
        leftW = (inner - MID) / 2;
        rightW = inner - MID - leftW;
        int searchRel = Ui.HEADER_H + 8;
        int capRel = searchRel + 20 + 7;
        int listRel = capRel + 12;
        int footRel = listRel + rows * ROW + 8;
        ph = footRel + 7 + 20 + 8;
        px = (this.width - pw) / 2;
        py = Math.max(4, (this.height - ph) / 2);
        lx = px + PAD;
        rx = lx + leftW + MID;
        capY = py + capRel;
        listY = py + listRel;
        footY = py + footRel + 7;

        if (searchBox == null) {
            searchBox = new EditBox(this.font, 0, 0, inner, 20, Component.literal("Suchen"));
            searchBox.setHint(Component.literal(kind == Kind.ITEM ? "Alle Items durchsuchen"
                    : kind == Kind.ENTITY ? "Alle Mobs durchsuchen" : "Alle Bl\u00f6cke durchsuchen"));
            searchBox.setMaxLength(40);
            searchBox.setValue(search);
            searchBox.setResponder(s -> {
                search = s;
                scrollL = 0;
                refilter();
            });
        }
        searchBox.setWidth(inner);
        searchBox.setX(lx);
        searchBox.setY(py + searchRel);
        addWidget(searchBox); // gezeichnet wird es von uns selbst (ueber dem Panel)
        refilter();

        for (int i = 0; i < rows; i++) {
            final int r = i;
            int y = listY + i * ROW + 1;
            addBtns.add(addWidget(Button.builder(Component.empty(), b -> {
                Option o = leftOption(r);
                if (o != null) {
                    selection.add(o.value());
                    TestConfig.save();
                }
            }).bounds(lx + leftW - LANE - 22, y, 20, 20).build()));
            delBtns.add(addWidget(Button.builder(Component.empty(), b -> {
                Option o = rightOption(r);
                if (o != null) {
                    selection.remove(o.value());
                    TestConfig.save();
                    clampScroll();
                }
            }).bounds(rx + rightW - LANE - 22, y, 20, 20).build()));
        }

        // Klick in den Scrollbalken: obere Haelfte blaettert zurueck, untere weiter
        int half = rows * ROW / 2;
        addWidget(Button.builder(Component.empty(), b -> {
            scrollL -= rows - 1;
            clampScroll();
        }).bounds(lx + leftW - LANE, listY, LANE, half).build());
        addWidget(Button.builder(Component.empty(), b -> {
            scrollL += rows - 1;
            clampScroll();
        }).bounds(lx + leftW - LANE, listY + half, LANE, rows * ROW - half).build());
        addWidget(Button.builder(Component.empty(), b -> {
            scrollR -= rows - 1;
            clampScroll();
        }).bounds(rx + rightW - LANE, listY, LANE, half).build());
        addWidget(Button.builder(Component.empty(), b -> {
            scrollR += rows - 1;
            clampScroll();
        }).bounds(rx + rightW - LANE, listY + half, LANE, rows * ROW - half).build());

        activeBtn = null;
        if (active != null) {
            int activeW = 16 + 6 + this.font.width("Aktiv") + 4;
            activeBtn = addWidget(Button.builder(Component.empty(), b -> {
                setActive.accept(!active.getAsBoolean());
                TestConfig.save();
            }).bounds(lx, footY, activeW, 20).build());
        }
        int backW = Math.max(72, Ui.buttonWidth(this.font, CommonComponents.GUI_BACK.getString()));
        backBtn = addWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(px + pw - PAD - backW, footY, backW, 20).build());

        // Ausgangspositionen merken, damit die Einblend-Animation alles mitverschieben kann
        baseY.clear();
        for (var c : this.children()) {
            if (c instanceof AbstractWidget w) baseY.put(w, w.getY());
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int d = scrollY > 0 ? -3 : 3;
        if (mouseX < rx - MID / 2) {
            scrollL += d;
        } else {
            scrollR += d;
        }
        clampScroll();
        return true;
    }

    // ---------- Zeichnen ----------

    /** Scrollbalken einer Liste (nur wenn sie mehr Eintraege hat, als sichtbar sind). */
    private void listBar(GuiGraphicsExtractor g, Object key, int x, int top, int total, int scroll, int mx, int my) {
        if (total <= rows) return;
        int trackY = top + 2;
        int trackH = rows * ROW - 6;
        int thumbH = Math.max(14, trackH * rows / total);
        int thumbY = trackY + Math.round((trackH - thumbH) * (scroll / (float) (total - rows)));
        boolean over = mx >= x - 2 && mx < x + LANE && my >= top && my < top + rows * ROW;
        Ui.scrollbar(g, x + 2, trackY, trackH, thumbY, thumbH, Ui.ease(key, 0, over ? 1f : 0f, 0.3f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Ui.beginFrame();
        long nowMs = System.currentTimeMillis();
        float closeProgress = 0f;
        float t;
        if (closing) {
            closeProgress = Theme.animations ? Math.min(1f, (nowMs - closeAt) / 150f) : 1f;
            t = 1f - closeProgress;
        } else {
            t = Theme.animations ? Math.min(1f, (nowMs - openedAt) / 180f) : 1f;
        }
        float ease = 1f - (1f - t) * (1f - t);
        int d = Math.round((1f - ease) * 10f);
        float ga = Math.max(0.06f, ease);
        for (var e : baseY.entrySet()) {
            e.getKey().setY(e.getValue() + d);
        }
        searchBox.setAlpha(ga);
        // Minecraft zeichnet hier nichts selbst: das Suchfeld kommt unten, ueber dem Panel
        super.extractRenderState(g, mouseX, mouseY, delta);

        Ui.alpha = 1f;
        Fx.backdrop(g, this.width, this.height);

        boolean icons = ga >= 0.85f;
        Ui.alpha = ga;
        Fx.panelGlow(g, px, py + d, pw, ph, ease);
        Ui.panel(g, this.font, px, py + d, pw, ph, this.title.getString(), 0);
        Fx.openFlash(g, px, py + d, pw, ph, ease);

        searchBox.extractRenderState(g, mouseX, mouseY, delta);
        Ui.field(g, searchBox);

        // Ueberschriften der beiden Listen
        List<Option> sel = selected();
        String all = kind == Kind.ITEM ? "Items" : kind == Kind.ENTITY ? "Mobs" : "Bl\u00f6cke";
        Ui.text(g, this.font, filtered.size() + " " + all, lx + 2, capY + d, Ui.MUTED, true);
        Ui.text(g, this.font, "Ausgew\u00e4hlt: " + sel.size(), rx + 2, capY + d, Ui.MUTED, true);

        // feine Trennlinie zwischen den Listen
        int midX = lx + leftW + MID / 2;
        Ui.fill(g, midX, listY + d, midX + 1, listY + d + rows * ROW, Ui.HAIR);

        int nameMaxL = leftW - LANE - 22 - 8 - 26;
        int nameMaxR = rightW - LANE - 22 - 8 - 26;
        for (int r = 0; r < rows; r++) {
            int y = listY + d + r * ROW;

            Option a = leftOption(r);
            Button ab = addBtns.get(r);
            boolean chosen = a != null && selection.contains(a.value());
            ab.active = a != null && !chosen;
            boolean overL = a != null && mouseX >= lx && mouseX < lx + leftW - LANE && mouseY >= y && mouseY < y + ROW;
            float hl = Ui.ease(ab, 2, overL ? 1f : 0f, 0.3f);
            if (hl > 0.01f) {
                Ui.roundRect(g, lx, y, leftW - LANE, ROW - 1, 3, Ui.withA(0xFFFFFF, Math.round(0x12 * hl)));
            }
            if (a != null) {
                if (icons) drawIcon(g, a, lx + 4, y + 3);
                Ui.fitText(g, this.font, a.name(), lx + 26, y + 7, nameMaxL,
                        chosen ? Theme.accent() : Ui.TEXT, true);
                Ui.plusMinus(g, ab, ab.getX(), ab.getY(), true, ab.active, mouseX, mouseY);
            }

            int idx = scrollR + r;
            Option s = idx < sel.size() ? sel.get(idx) : null;
            Button db = delBtns.get(r);
            db.active = s != null;
            boolean overR = s != null && mouseX >= rx && mouseX < rx + rightW - LANE && mouseY >= y && mouseY < y + ROW;
            float hr = Ui.ease(db, 2, overR ? 1f : 0f, 0.3f);
            if (hr > 0.01f) {
                Ui.roundRect(g, rx, y, rightW - LANE, ROW - 1, 3, Ui.withA(0xFFFFFF, Math.round(0x12 * hr)));
            }
            if (s != null) {
                if (icons) drawIcon(g, s, rx + 4, y + 3);
                Ui.fitText(g, this.font, s.name(), rx + 26, y + 7, nameMaxR, Ui.TEXT, true);
                Ui.plusMinus(g, db, db.getX(), db.getY(), false, true, mouseX, mouseY);
            }
        }
        if (sel.isEmpty()) {
            Ui.fitText(g, this.font, "Noch nichts ausgew\u00e4hlt", rx + 4, listY + d + 7, rightW - LANE - 8,
                    Ui.withA(Ui.MUTED, 0xB0), true);
        }

        // Scrollbalken statt Blaetter-Knoepfen
        listBar(g, "pickL", lx + leftW - LANE, listY + d, filtered.size(), scrollL, mouseX, mouseY);
        listBar(g, "pickR", rx + rightW - LANE, listY + d, sel.size(), scrollR, mouseX, mouseY);

        // Fusszeile
        Ui.fill(g, px + PAD, footY + d - 7, px + pw - PAD, footY + d - 6, Ui.HAIR);
        if (activeBtn != null) {
            boolean on = active.getAsBoolean();
            boolean over = mouseX >= activeBtn.getX() && mouseX < activeBtn.getX() + activeBtn.getWidth()
                    && mouseY >= activeBtn.getY() && mouseY < activeBtn.getY() + 20;
            float hv = Ui.ease(activeBtn, 0, over ? 1f : 0f, 0.3f);
            float st = Ui.ease(activeBtn, 1, on ? 1f : 0f, 0.35f);
            Ui.switchPill(g, activeBtn.getX(), activeBtn.getY() + 6, st);
            Ui.text(g, this.font, "Aktiv", activeBtn.getX() + 22, activeBtn.getY() + 6,
                    Ui.lerp(Ui.lerp(Ui.MUTED, Ui.TEXT_ON, st), 0xFFFFFFFF, hv * 0.4f), true);
        }
        Ui.button(g, this.font, backBtn, mouseX, mouseY, Ui.STYLE_NORMAL);
        Ui.alpha = 1f;

        if (closing && closeProgress >= 1f) finishClose();
    }

    private void finishClose() {
        if (closed) return;
        closed = true;
        this.minecraft.gui.setScreen(parent);
    }

    public void tick() {
        if (closing && System.currentTimeMillis() - closeAt >= 150) finishClose();
    }

    public void removed() {
        TestConfig.save();
        GuiScale.restore();
    }

    @Override
    public void onClose() {
        TestConfig.save();
        if (!Theme.animations) {
            finishClose();
            return;
        }
        if (closing) return;
        closing = true;
        closeAt = System.currentTimeMillis();
    }
}
