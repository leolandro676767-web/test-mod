package de.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Sprachen fuer das 1912-Menue. Geschrieben ist alles auf Deutsch; fuer andere Sprachen gibt es Tabellen
 * (assets/test/i18n): keys.txt (Nummer, deutscher Text) und je Sprache eine Datei (Nummer, Uebersetzung).
 * Uebersetzt wird beim Anzeigen (Ui.text, Form, Chat-Meldungen). Eintraege mit "~" sind Teilstuecke von
 * zusammengesetzten Texten (z.B. "Größe: " + Zahl) und werden innerhalb eines Textes ersetzt.
 */
public final class Lang {
    private Lang() {
    }

    /** Auswahl im Menue; 0 = wie Minecraft. Sprachnamen stehen in ihrer eigenen Sprache. */
    static final String[] NAMES = {"Automatisch (wie Minecraft)", "Deutsch", "English", "Español", "Français"};
    private static final String[] CODES = {null, "de", "en", "es", "fr"};

    /** Gewaehlte Sprache (Index in NAMES), wird gespeichert. */
    public static int language = 0;

    /**
     * Geladene Tabelle einer Sprache. Wird als Ganzes ausgetauscht, damit Chat-Meldungen aus anderen
     * Threads nie eine halb geladene Tabelle sehen.
     */
    private record Table(String code, Map<String, String> exact, List<String[]> parts, Map<String, String> cache) {
    }

    private static volatile Table table;
    private static final int CACHE_MAX = 4096;

    /** Aktueller Sprachcode ("de", "en", ...). */
    static String code() {
        if (language > 0 && language < CODES.length) return CODES[language];
        String mc = null;
        try {
            Minecraft m = Minecraft.getInstance();
            if (m != null && m.options != null) mc = m.options.languageCode;
        } catch (RuntimeException e) {
            mc = null;
        }
        if (mc == null || mc.length() < 2) return "de";
        String p = mc.substring(0, 2).toLowerCase(java.util.Locale.ROOT);
        for (int i = 1; i < CODES.length; i++) {
            if (CODES[i].equals(p)) return p;
        }
        return "en"; // andere Sprachen: Englisch
    }

    /** Uebersetzt einen (deutschen) Text in die gewaehlte Sprache; Unbekanntes bleibt, wie es ist. */
    public static String t(String s) {
        if (s == null || s.isEmpty()) return s;
        String c = code();
        if (c.equals("de")) return s;
        Table tb = table;
        if (tb == null || !c.equals(tb.code())) tb = load(c);
        String r = tb.cache().get(s);
        if (r != null) return r;
        r = tb.exact().get(s);
        if (r == null) {
            r = s;
            for (String[] p : tb.parts()) {
                if (r.contains(p[0])) r = r.replace(p[0], p[1]);
            }
        }
        // Texte mit wechselnden Zahlen fuellen den Zwischenspeicher - dann einfach neu anfangen
        if (tb.cache().size() > CACHE_MAX) tb.cache().clear();
        tb.cache().put(s, r);
        return r;
    }

    /** Mehrere Texte auf einmal (z.B. die Moeglichkeiten einer Auswahl). */
    static String[] all(String[] in) {
        String[] out = new String[in.length];
        for (int i = 0; i < in.length; i++) out[i] = t(in[i]);
        return out;
    }

    /**
     * Fuer Chat-Meldungen: Component.literal mit uebersetztem Text. Meldungen, die mit "1912: " beginnen,
     * bekommen den farbigen Absender "1912 Client" (siehe Brand).
     */
    public static MutableComponent literal(String s) {
        String text = t(s);
        MutableComponent branded = Brand.chat(text);
        return branded != null ? branded : Component.literal(text);
    }

    // ---------- Tabellen laden ----------

    private static synchronized Table load(String c) {
        Table tb = table;
        if (tb != null && c.equals(tb.code())) return tb; // ein anderer Thread war schneller
        Map<String, String> exact = new HashMap<>();
        List<String[]> parts = new ArrayList<>();
        Map<String, String> keys = read("keys");
        Map<String, String> tr = read(c);
        for (var e : keys.entrySet()) {
            String target = tr.get(e.getKey());
            if (target == null || target.isEmpty()) continue;
            String de = e.getValue();
            if (de.startsWith("~")) {
                parts.add(new String[] {de.substring(1), target});
            } else {
                exact.put(de, target);
            }
        }
        parts.sort((a, b) -> b[0].length() - a[0].length()); // laengste zuerst
        tb = new Table(c, exact, parts, new ConcurrentHashMap<>());
        table = tb;
        return tb;
    }

    /** Liest "Nummer TAB Text" (\n im Text = Zeilenumbruch). */
    private static Map<String, String> read(String name) {
        Map<String, String> out = new HashMap<>();
        try (InputStream in = Lang.class.getResourceAsStream("/assets/test/i18n/" + name + ".txt")) {
            if (in == null) return out;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = r.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab <= 0 || line.startsWith("#")) continue;
                out.put(line.substring(0, tab).trim(), line.substring(tab + 1).replace("\\n", "\n"));
            }
        } catch (IOException | RuntimeException e) {
            // ohne Tabelle bleibt alles auf Deutsch
        }
        return out;
    }
}
