package de.test;

import java.util.Set;
import net.minecraft.client.Minecraft;

/**
 * Modus des Menues: welche Module zu sehen sind.
 * <ul>
 * <li>Anfänger: nur die einfachsten Komfort-Module (Sprint, Fullbright, Wegpunkte, Chat ...).</li>
 * <li>Standard (voreingestellt): dazu alles, was nur die Anzeige oder den Komfort betrifft - nichts, was einem
 * einen unfairen Vorteil verschafft.</li>
 * <li>Experte: alle Module.</li>
 * </ul>
 * Was im gewaehlten Modus nicht dabei ist, wird ausgeschaltet und reagiert auch nicht auf seine Taste.
 */
final class Levels {
    private Levels() {
    }

    static final String[] NAMES = {"Anfänger", "Standard", "Experte"};
    static final int BEGINNER = 0;
    static final int STANDARD = 1;
    static final int EXPERT = 2;

    /** Gewaehlter Modus (wird gespeichert). */
    static int level = STANDARD;

    /** Fuer Anfaenger: wenige, einfache Komfort-Module. */
    private static final Set<String> SIMPLE = Set.of(
            "Sprint", "Sneak", "Auto Jump", "Auto Respawn",
            "Fullbright", "Info-Anzeige", "Waypoints", "Marker", "Better Tooltips", "Blur", "Time Changer",
            "Name Protect", "Better Chat", "Auto Reconnect", "Discord Presence", "Freunde", "Server-Schutz",
            "Zoom", "Haltbarkeit", "Tasten-Anzeige", "Fadenkreuz", "Todespunkt", "Kompass-Leiste", "Chunk-Grenzen",
            Keybinds.PANIC_NAME);

    /** Zusaetzlich im Standardmodus: Anzeige und Komfort, kein unfairer Vorteil. */
    private static final Set<String> NORMAL = Set.of(
            "Auto Walk",
            "Anti Afk", "Auto Eat", "Auto Replenish", "Auto Tool", "Chest Swap", "No Interact", "Portals",
            "Better Tab", "Block Selection", "Boss Stack", "Breadcrumbs", "Entity Owner", "Free Look", "Hand View",
            "Item Physics", "Light Overlay", "No Render", "Trail", "Minimap",
            "AutoDrop", "Elytra-Tausch", "Schadenszahlen", "Auto Sign", "Auto Smelt",
            "Inventory Tweaks", "Notifier", "Sound Blocker");

    /** Ab welchem Modus ein Modul zu sehen ist. */
    static int of(String name) {
        if (SIMPLE.contains(name)) return BEGINNER;
        if (NORMAL.contains(name)) return STANDARD;
        return EXPERT;
    }

    static boolean visible(String name) {
        return of(name) <= level;
    }

    /** Anzahl der Module, die im Modus zu sehen sind (fuer die Beschreibung). */
    static int count(int lvl) {
        if (Keybinds.ACTIONS.isEmpty()) TestScreen.registerActions();
        int n = 0;
        for (String name : Keybinds.ACTIONS.keySet()) {
            if (!name.equals(Keybinds.PANIC_NAME) && of(name) <= lvl) n++;
        }
        return n;
    }

    /** Modus wechseln: was dann nicht mehr dabei ist, wird ausgeschaltet. */
    static void set(int lvl) {
        lvl = Math.max(BEGINNER, Math.min(EXPERT, lvl));
        if (lvl == level) return;
        level = lvl;
        int off = switchOffHidden();
        TestConfig.save();
        if (off > 0) {
            Keybinds.message(Lang.t("Modus ") + Lang.t(NAMES[level]) + ": " + off
                    + (off == 1 ? Lang.t(" Modul ausgeschaltet") : Lang.t(" Module ausgeschaltet")), "");
        }
    }

    private static boolean checked;

    /** Einmal nach dem Betreten einer Welt: Module, die im Modus nicht dabei sind, ausschalten. */
    static void tick(Minecraft mc) {
        if (mc.player == null) {
            checked = false;
            return;
        }
        if (checked) return;
        checked = true;
        int off = switchOffHidden();
        if (off > 0) {
            TestConfig.save();
            mc.player.sendSystemMessage(Lang.literal("§e1912: " + Lang.t("Modus ") + Lang.t(NAMES[level]) + ": "
                    + off + (off == 1 ? Lang.t(" Modul ausgeschaltet") : Lang.t(" Module ausgeschaltet"))
                    + Lang.t(" (alle Module: Menü, Modus, Experte).")));
        }
    }

    /** Schaltet alle eingeschalteten Module aus, die im Modus nicht zu sehen sind. */
    private static int switchOffHidden() {
        if (level >= EXPERT) return 0;
        if (Keybinds.ACTIONS.isEmpty()) TestScreen.registerActions();
        int n = 0;
        for (var e : Keybinds.ACTIONS.entrySet()) {
            if (visible(e.getKey())) continue;
            Keybinds.Action a = e.getValue();
            if (a.state() == null || !a.state().getAsBoolean()) continue;
            try {
                a.run().run();
            } catch (RuntimeException | LinkageError ex) {
                Safe.report("Modus", ex);
            }
            if (!a.state().getAsBoolean()) n++;
        }
        // laufende Bots (Bloecke suchen, Bereich, Koordinaten) haben keinen An/Aus-Schalter
        Minecraft mc = Minecraft.getInstance();
        if (TestClient.areaActive) {
            TestClient.stopArea(mc, null);
            n++;
        }
        return n;
    }
}
