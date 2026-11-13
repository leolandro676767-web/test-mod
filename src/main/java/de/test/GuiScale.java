package de.test;

import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Eigene GUI-Groesse nur fuer die 1912-Menues. Beim Oeffnen eines Menues wird die GUI-Skalierung
 * des Fensters verkleinert (damit alle Spalten und Texte ganz hineinpassen), beim Schliessen
 * wird die Einstellung des Spiels wiederhergestellt. Die Spiel-Optionen selbst werden nie
 * veraendert. Klappt der Zugriff nicht, bleibt einfach alles wie im Spiel.
 */
final class GuiScale {
    private GuiScale() {
    }

    /** So viel Platz (in GUI-Pixeln) braucht das Hauptmenue, damit alles nebeneinander passt ("Auto"). */
    private static int needW = 800;
    private static int needH = 330;

    /** Das Hauptmenue meldet, wie breit alle Spalten zusammen sind (ohne dass Text gekuerzt wird). */
    static void need(int w, int h) {
        needW = Math.max(480, Math.min(1400, w));
        needH = Math.max(240, Math.min(600, h));
    }

    private static Method getter;
    private static Method setter;
    private static boolean looked;
    private static int original = -1; // Groesse des Spiels vor dem Menue
    private static int applied = -1;  // von uns gesetzte Groesse (-1 = nichts geaendert)

    private static boolean ready(Object win) {
        if (!looked) {
            looked = true;
            try {
                for (Method m : win.getClass().getMethods()) {
                    if (m.getName().equals("getGuiScale") && m.getParameterCount() == 0) getter = m;
                    if (m.getName().equals("setGuiScale") && m.getParameterCount() == 1) setter = m;
                }
            } catch (RuntimeException e) {
                getter = null;
                setter = null;
            }
        }
        return getter != null && setter != null;
    }

    private static int read(Object win) throws Exception {
        return (int) Math.round(((Number) getter.invoke(win)).doubleValue());
    }

    private static void write(Object win, int scale) throws Exception {
        Class<?> p = setter.getParameterTypes()[0];
        if (p == int.class || p == Integer.class) {
            setter.invoke(win, scale);
        } else if (p == double.class || p == Double.class) {
            setter.invoke(win, (double) scale);
        } else if (p == float.class || p == Float.class) {
            setter.invoke(win, (float) scale);
        }
    }

    /** Ziel-Groesse je nach Einstellung "Menü-Größe". */
    private static int target(int orig, int fbW, int fbH) {
        switch (Theme.menuScale) {
            case 1:
                return orig;
            case 2:
                return Math.max(1, orig - 1);
            case 3:
                return Math.max(1, orig - 2);
            default:
                // Auto: groesste Stufe (hoechstens die des Spiels), bei der alles hineinpasst
                int min = Math.min(orig, 2);
                int s = orig;
                while (s > min && (fbW / s < needW || fbH / s < needH)) s--;
                return s;
        }
    }

    /** Im init() jedes 1912-Menues aufrufen (danach width/height des Screens neu setzen). */
    static void apply() {
        Minecraft mc = Minecraft.getInstance();
        Object win = mc.getWindow();
        if (!ready(win)) return;
        try {
            int cur = read(win);
            // erstes Menue, oder das Spiel hat die Groesse inzwischen selbst neu gesetzt
            if (applied < 0 || cur != applied) original = cur;
            if (original <= 0) return;
            int fbW = mc.getWindow().getGuiScaledWidth() * cur;
            int fbH = mc.getWindow().getGuiScaledHeight() * cur;
            int want = target(original, fbW, fbH);
            if (want != cur) write(win, want);
            applied = want;
        } catch (Exception | LinkageError e) {
            getter = null;
            setter = null;
        }
    }

    /** Beim Schliessen eines 1912-Menues: Groesse des Spiels zurueck. */
    static void restore() {
        if (applied < 0) return;
        Minecraft mc = Minecraft.getInstance();
        Object win = mc.getWindow();
        try {
            if (ready(win) && original > 0 && read(win) == applied && applied != original) write(win, original);
        } catch (Exception | LinkageError e) {
            // nichts zu tun
        }
        applied = -1;
    }

    static boolean isMenu(Screen s) {
        return s instanceof TestScreen || s instanceof Form || s instanceof PickerScreen;
    }

    /**
     * Sicherheitsnetz im Tick: ist kein 1912-Menue mehr offen, die Groesse aber noch verkleinert,
     * wird sie zurueckgesetzt und ein evtl. offener fremder Screen neu angeordnet.
     */
    static void check(Minecraft mc) {
        if (applied < 0) return;
        Screen s = mc.gui.screen();
        if (isMenu(s)) return;
        restore();
        if (s == null) return;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        try {
            for (Method m : s.getClass().getMethods()) {
                if (!m.getName().equals("resize")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2 && p[0] == int.class && p[1] == int.class) {
                    m.invoke(s, w, h);
                    return;
                }
                if (p.length == 3 && p[1] == int.class && p[2] == int.class && p[0].isInstance(mc)) {
                    m.invoke(s, mc, w, h);
                    return;
                }
            }
        } catch (Exception | LinkageError e) {
            // nicht schlimm: der Screen ordnet sich beim naechsten Oeffnen richtig an
        }
    }
}
