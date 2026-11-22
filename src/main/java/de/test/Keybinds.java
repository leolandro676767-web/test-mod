package de.test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.BooleanSupplier;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Eigene Tasten fuer jedes Modul, direkt im Menue zugewiesen. Gedrueckt wird nur, wenn kein
 * Menue/Chat offen ist; danach zeigt eine kleine Einblendung oben, was passiert ist.
 */
public final class Keybinds {
    private Keybinds() {
    }

    // Aktionen, die frueher in den Minecraft-Steuerungen standen (jetzt hier zuweisbar)
    static final String PANIC_NAME = "Alle Module aus / an";
    static final String STOP_NAME = "Stopp / Auto Walk";
    static final String SELECT_NAME = "Block w\u00e4hlen";
    static final String SORT_NAME = "Inventar sortieren";

    /** Modulname -> GLFW-Tastencode. */
    static final Map<String, Integer> BINDS = new LinkedHashMap<>();

    /** Was eine Taste ausloest (wird vom Hauptmenue gefuellt). */
    record Action(BooleanSupplier state, Runnable run) {
    }

    static final Map<String, Action> ACTIONS = new LinkedHashMap<>();

    /** Module, deren Taste "halten" bedeutet: nur an, solange die Taste gedrueckt ist. */
    static final Set<String> HOLD = new java.util.LinkedHashSet<>();
    /** Module, die eine Halten-Taste gerade eingeschaltet hat (beim Loslassen wieder aus). */
    private static final Set<String> heldOn = new HashSet<>();

    private static final Set<Integer> held = new HashSet<>();

    /** Module, die "Alle aus" abgeschaltet hat und beim naechsten Druck wieder einschaltet. */
    static final Set<String> PANIC = new java.util.LinkedHashSet<>();

    // Einblendung
    private static String toastText = null;
    private static boolean toastOn;
    private static boolean toastHasState;
    private static long toastAt;

    public static void register() {
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("test", "keybind_toast"), Safe.hud("Anzeige", Keybinds::render));
    }

    // ---------- Speichern ----------

    /** Umbenannte Module: alter Name -> neuer Name (fuer alte Einstellungen und kopierte Texte). */
    static String current(String name) {
        return "Item Highlight".equals(name) ? "Item Esp" : name;
    }

    static void load(Properties p) {
        PANIC.clear();
        String saved = p.getProperty("panicSaved", "");
        for (String n : saved.split("\\|")) {
            if (!n.isBlank()) PANIC.add(current(n));
        }
        BINDS.clear();
        HOLD.clear();
        for (String n : p.getProperty("bindHold", "").split("\\|")) {
            if (!n.isBlank()) HOLD.add(current(n));
        }
        for (String k : p.stringPropertyNames()) {
            if (!k.startsWith("bind.")) continue;
            try {
                int code = Integer.parseInt(p.getProperty(k).trim());
                if (code > 0) BINDS.put(current(k.substring(5)), code);
            } catch (NumberFormatException e) {
                // kaputter Eintrag: ignorieren
            }
        }
        // Einmalig die alten Standardtasten uebernehmen (danach gilt, was man selbst einstellt)
        if (p.getProperty("bindDefaults") == null) {
            putDefault(PANIC_NAME, GLFW.GLFW_KEY_J);
            putDefault(STOP_NAME, GLFW.GLFW_KEY_G);
            putDefault(SELECT_NAME, GLFW.GLFW_KEY_K);
        }
    }

    private static void putDefault(String name, int code) {
        if (!BINDS.containsKey(name) && !BINDS.containsValue(code)) BINDS.put(name, code);
    }

    static void save(Properties p) {
        p.setProperty("panicSaved", String.join("|", PANIC));
        p.setProperty("bindHold", String.join("|", HOLD));
        p.setProperty("bindDefaults", "1");
        for (var e : BINDS.entrySet()) p.setProperty("bind." + e.getKey(), Integer.toString(e.getValue()));
    }

    // ---------- Tasten ----------

    static Integer get(String name) {
        return BINDS.get(name);
    }

    static void set(String name, int code) {
        // eine Taste gehoert immer nur einem Modul
        BINDS.values().removeIf(c -> c == code);
        BINDS.put(name, code);
        TestConfig.save();
    }

    static void clear(String name) {
        HOLD.remove(name);
        if (BINDS.remove(name) != null) TestConfig.save();
    }

    /** Alle gerade gedrueckten Tasten (Tastatur). */
    static Set<Integer> pressedNow() {
        Set<Integer> out = new HashSet<>();
        long win = GLFW.glfwGetCurrentContext();
        if (win == 0L) return out;
        for (int code = GLFW.GLFW_KEY_SPACE; code <= GLFW.GLFW_KEY_LAST; code++) {
            if (GLFW.glfwGetKey(win, code) == GLFW.GLFW_PRESS) out.add(code);
        }
        return out;
    }

    /** Im Tick: zugewiesene Tasten ausloesen (nur im Spiel, nicht in Menues oder im Chat). */
    static void tick(Minecraft mc) {
        if (BINDS.isEmpty()) return;
        long win = GLFW.glfwGetCurrentContext();
        if (win == 0L || mc.player == null) return;
        boolean inGame = mc.gui.screen() == null;
        for (var e : BINDS.entrySet()) {
            int code = e.getValue();
            boolean down = GLFW.glfwGetKey(win, code) == GLFW.GLFW_PRESS;
            if (!down) {
                held.remove(code);
                // Halten-Taste losgelassen: Modul wieder aus, wenn die Taste es eingeschaltet hat
                if (heldOn.remove(e.getKey())) {
                    Action a = ACTIONS.get(e.getKey());
                    if (a != null && a.state() != null && a.state().getAsBoolean()) a.run().run();
                }
                continue;
            }
            if (!held.add(code) || !inGame) continue; // nur beim Herunterdruecken, nur im Spiel
            if (ACTIONS.isEmpty()) TestScreen.registerActions();
            Action a = ACTIONS.get(e.getKey());
            if (a == null) continue;
            if (!Levels.visible(e.getKey())) continue; // im gewaehlten Modus nicht dabei (Menue: Modus)
            if (HOLD.contains(e.getKey()) && a.state() != null) {
                // halten: nur einschalten (und merken), ausschalten beim Loslassen
                if (!a.state().getAsBoolean()) {
                    a.run().run();
                    heldOn.add(e.getKey());
                }
                continue;
            }
            long before = toastAt;
            a.run().run();
            if (toastAt == before) toast(e.getKey(), a.state()); // eigene Meldung (z.B. "Alle aus") nicht ueberdecken
        }
    }

    /**
     * Taste "Alle Module aus/an" (Standard J): schaltet alle eingeschalteten Module aus und merkt sie
     * sich; beim naechsten Druck gehen genau diese wieder an.
     */
    static void panic() {
        if (ACTIONS.isEmpty()) TestScreen.registerActions();
        if (PANIC.isEmpty()) {
            for (var e : ACTIONS.entrySet()) {
                Action a = e.getValue();
                if (a.state() == null || !a.state().getAsBoolean()) continue;
                // der Schutz selbst und die Freundesliste bleiben an
                if (e.getKey().equals("Server-Schutz") || e.getKey().equals("Join-Schutz")
                        || e.getKey().equals("Freunde")) {
                    continue;
                }
                a.run().run();
                if (!a.state().getAsBoolean()) PANIC.add(e.getKey());
            }
            Integer key = BINDS.get(PANIC_NAME);
            message(PANIC.isEmpty() ? "Kein Modul war an" : "Alle Module aus (" + PANIC.size() + ")",
                    PANIC.isEmpty() ? "" : (key != null ? keyName(key) : "Nochmal") + ": wieder an");
        } else {
            int n = 0;
            for (String name : PANIC) {
                Action a = ACTIONS.get(name);
                if (a == null || a.state() == null || a.state().getAsBoolean()) continue;
                a.run().run();
                n++;
            }
            PANIC.clear();
            message(n + (n == 1 ? " Modul" : " Module") + " wieder an", "");
        }
        TestConfig.save();
    }

    private static String toastSub = "";

    static void message(String text, String sub) {
        toastText = Lang.t(text);
        toastSub = Lang.t(sub);
        toastHasState = false;
        toastAt = System.currentTimeMillis();
    }

    private static void toast(String name, BooleanSupplier state) {
        toastSub = "";
        toastText = Lang.t(name);
        toastHasState = state != null;
        toastOn = state != null && state.getAsBoolean();
        toastAt = System.currentTimeMillis();
    }

    /** Lesbarer Name einer Taste. */
    static String keyName(int code) {
        return Lang.t(keyNameDe(code));
    }

    private static String keyNameDe(int code) {
        String n = null;
        try {
            n = GLFW.glfwGetKeyName(code, 0);
        } catch (RuntimeException e) {
            // ohne Fenster kein Name
        }
        if (n != null && !n.isBlank()) return n.toUpperCase(java.util.Locale.ROOT);
        if (code >= GLFW.GLFW_KEY_F1 && code <= GLFW.GLFW_KEY_F25) return "F" + (code - GLFW.GLFW_KEY_F1 + 1);
        if (code >= GLFW.GLFW_KEY_KP_0 && code <= GLFW.GLFW_KEY_KP_9) return "Num " + (code - GLFW.GLFW_KEY_KP_0);
        return switch (code) {
            case GLFW.GLFW_KEY_SPACE -> "Leertaste";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_BACKSPACE -> "Rücktaste";
            case GLFW.GLFW_KEY_INSERT -> "Einfg";
            case GLFW.GLFW_KEY_DELETE -> "Entf";
            case GLFW.GLFW_KEY_RIGHT -> "Rechts";
            case GLFW.GLFW_KEY_LEFT -> "Links";
            case GLFW.GLFW_KEY_DOWN -> "Runter";
            case GLFW.GLFW_KEY_UP -> "Hoch";
            case GLFW.GLFW_KEY_PAGE_UP -> "Bild hoch";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "Bild runter";
            case GLFW.GLFW_KEY_HOME -> "Pos1";
            case GLFW.GLFW_KEY_END -> "Ende";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "Feststell";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "Shift links";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "Shift rechts";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "Strg links";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "Strg rechts";
            case GLFW.GLFW_KEY_LEFT_ALT -> "Alt";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "Alt Gr";
            case GLFW.GLFW_KEY_KP_ENTER -> "Num Enter";
            case GLFW.GLFW_KEY_KP_ADD -> "Num +";
            case GLFW.GLFW_KEY_KP_SUBTRACT -> "Num -";
            case GLFW.GLFW_KEY_KP_MULTIPLY -> "Num *";
            case GLFW.GLFW_KEY_KP_DIVIDE -> "Num /";
            case GLFW.GLFW_KEY_KP_DECIMAL -> "Num ,";
            case GLFW.GLFW_KEY_PAUSE -> "Pause";
            case GLFW.GLFW_KEY_PRINT_SCREEN -> "Druck";
            case GLFW.GLFW_KEY_SCROLL_LOCK -> "Rollen";
            case GLFW.GLFW_KEY_NUM_LOCK -> "Num";
            default -> "Taste " + code;
        };
    }

    // ---------- Einblendung ----------

    /** Liste der aktiven Module oben links (Einstellung unter "Design anpassen"). */
    private static void drawActiveList(GuiGraphicsExtractor g, Minecraft mc) {
        if (!Theme.hudList || mc.player == null || mc.gui.screen() instanceof TestScreen) return;
        if (ACTIONS.isEmpty()) TestScreen.registerActions();
        java.util.List<String> on = new java.util.ArrayList<>();
        for (var e : ACTIONS.entrySet()) {
            BooleanSupplier st = e.getValue().state();
            if (st != null && st.getAsBoolean()) on.add(Lang.t(e.getKey()));
        }
        if (on.isEmpty()) return;
        // laengste Namen zuerst: ergibt die typische Treppenform
        on.sort((a, b) -> mc.font.width(b) - mc.font.width(a));
        float scale = Theme.HUD_SCALES[Math.max(0, Math.min(Theme.HUD_SCALES.length - 1, Theme.hudListSize))];
        boolean right = Theme.hudCorner == 1 || Theme.hudCorner == 3;
        boolean bottom = Theme.hudCorner >= 2;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int maxRows = Math.max(1, (int) ((sh - 6) / (11 * scale)));
        int rows = Math.min(on.size(), maxRows);
        float old = Ui.alpha;
        Ui.alpha = 1f;
        var pose = g.pose();
        pose.pushMatrix();
        // in die gewaehlte Ecke; unten wird von unten nach oben gestapelt
        pose.translate(right ? sw - 3f : 3f, bottom ? sh - 3f - rows * 11 * scale : 3f);
        pose.scale(scale, scale);
        int accent = Theme.accent();
        int bar0 = Theme.headAt(0f);
        int bar1 = Theme.headAt(1f);
        for (int i = 0; i < rows; i++) {
            // unten: kuerzeste Namen nach unten, damit die Treppe zur Ecke hin schmal wird
            String name = on.get(bottom ? rows - 1 - i : i);
            int w = mc.font.width(name) + 9;
            int y = i * 11;
            int bar = Ui.lerp(bar0, bar1, rows <= 1 ? 0f : i / (float) (rows - 1));
            int tc = Ui.lerp(0xFFFFFFFF, accent, 0.25f);
            if (right) {
                Ui.gradientH(g, -w - 10, y, 0, y + 11, 0x00000000, 0xA0000000);
                Ui.fill(g, -2, y, 0, y + 11, bar);
                Ui.text(g, mc.font, name, -5 - mc.font.width(name), y + 2, tc, true);
            } else {
                Ui.gradientH(g, 0, y, w + 10, y + 11, 0xA0000000, 0x00000000);
                Ui.fill(g, 0, y, 2, y + 11, bar);
                Ui.text(g, mc.font, name, 5, y + 2, tc, true);
            }
        }
        pose.popMatrix();
        Ui.alpha = old;
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        drawActiveList(g, Minecraft.getInstance());
        Safe.run("Debris Miner", () -> DebrisMiner.renderHud(g, Minecraft.getInstance()));
        Safe.run("Tunnel Miner", () -> TunnelMiner.renderHud(g, Minecraft.getInstance()));
        if (toastText == null) return;
        long age = System.currentTimeMillis() - toastAt;
        if (age > 1600) {
            toastText = null;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float in = Math.min(1f, age / 120f);
        float out = age > 1300 ? 1f - (age - 1300) / 300f : 1f;
        float a = Math.max(0f, Math.min(in, out));
        float old = Ui.alpha;
        Ui.alpha = a;
        String state = toastHasState ? Lang.t(toastOn ? "an" : "aus") : "";
        String sub = toastSub == null ? "" : toastSub;
        int nameW = mc.font.width(toastText);
        int stateW = state.isEmpty() ? 0 : mc.font.width(state) + 10 + 16 + 6;
        if (!sub.isEmpty()) stateW = mc.font.width(sub) + 12;
        int w = 12 + nameW + stateW + 12;
        int x = (mc.getWindow().getGuiScaledWidth() - w) / 2;
        int y = 8 + Math.round((1f - in) * -6f);
        Fx.shadow(g, x, y, w, 20);
        Ui.roundRect(g, x - 1, y - 1, w + 2, 22, 7, Theme.border());
        Ui.roundGradientH(g, x, y, w, 20, 6, Ui.lerp(Ui.PANEL, Theme.accent(), 0.18f), Ui.PANEL, true, true);
        Ui.text(g, mc.font, toastText, x + 12, y + 6, Ui.TEXT, true);
        if (!sub.isEmpty()) {
            Ui.text(g, mc.font, sub, x + 12 + nameW + 12, y + 6, Ui.MUTED, true);
        }
        if (!state.isEmpty()) {
            int sx = x + 12 + nameW + 10;
            Ui.switchPill(g, sx, y + 6, toastOn ? 1f : 0f);
            Ui.text(g, mc.font, state, sx + 22, y + 6, toastOn ? Theme.accent() : Ui.MUTED, true);
        }
        Ui.alpha = old;
    }
}
