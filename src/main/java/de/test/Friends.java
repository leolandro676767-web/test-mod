package de.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Freundesliste fuer den ganzen Client.
 * Freunde werden von allem verschont, was angreift oder Spieler verfolgt (Auto Clicker, Multitask, Auto Wasp,
 * City Esp) und in ESP, Linien, Chams, Nametags und Better Tab in der Freundesfarbe gezeigt.
 * Jede Wirkung laesst sich einzeln abschalten; der Hauptschalter schaltet alles auf einmal ab.
 */
public final class Friends {
    private Friends() {
    }

    // ---------- Einstellungen (werden ueber TestConfig gespeichert, Praefix "fr.") ----------

    public static boolean enabled = true;          // Hauptschalter
    public static String list = "";                // Namen, mit Komma getrennt
    public static int color = -1;                  // 0xRRGGBB, -1 = Standard

    // Anzeige
    public static boolean showEsp = true;          // ESP-Umriss, -Linie und -Name
    public static boolean showTracers = true;      // Tracers-Modul
    public static boolean showChams = true;
    public static boolean showNametags = true;
    public static boolean showTab = true;          // Better Tab: Farbe und oben einsortieren

    // Schutz
    public static boolean noAttack = true;         // Auto Clicker und Multitask greifen Freunde nicht an
    public static boolean blockHits = false;       // auch eigene Schlaege auf Freunde abfangen
    public static boolean noChase = true;          // Auto Wasp fliegt nicht zu Freunden
    public static boolean noCity = true;           // City Esp markiert Freunde nicht

    // Bedienung und Meldungen
    public static boolean middleClick = true;      // Mittelklick auf einen Spieler: Freund an/aus
    public static int middleClickRange = 64;
    public static boolean notifyOnline = true;     // Freund kommt online / geht offline
    public static boolean notifyNear = false;      // Freund kommt in Sichtweite

    public static final int DEFAULT_COLOR = 0xFF5CFFA8;

    // ---------- Liste ----------

    /** Kleinbuchstaben -> Schreibweise wie eingegeben. */
    private static final Map<String, String> names = new LinkedHashMap<>();
    private static String parsedFrom = null;

    private static void sync() {
        String now = list == null ? "" : list;
        if (now.equals(parsedFrom)) return;
        parsedFrom = now;
        names.clear();
        for (String s : now.split(",")) {
            String n = clean(s);
            if (!n.isEmpty()) names.putIfAbsent(key(n), n);
        }
    }

    private static void store() {
        list = String.join(",", names.values());
        parsedFrom = list;
        TestConfig.save();
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    /** Spielernamen haben keine Leerzeichen und keine Kommas; alles andere bleibt (auch Bedrock-Praefixe). */
    static String clean(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.strip().toCharArray()) {
            if (c > ' ' && c != ',' && c != ';') sb.append(c);
        }
        return sb.length() > 32 ? sb.substring(0, 32) : sb.toString();
    }

    public static List<String> all() {
        sync();
        return new ArrayList<>(names.values());
    }

    public static int count() {
        sync();
        return names.size();
    }

    /** Steht der Name auf der Liste? (Gross-/Kleinschreibung egal; unabhaengig vom Hauptschalter) */
    public static boolean listed(String name) {
        if (name == null || name.isEmpty()) return false;
        sync();
        return names.containsKey(key(name));
    }

    /** Ist das ein Freund, und ist die Freundesliste eingeschaltet? */
    public static boolean is(String name) {
        return enabled && listed(name);
    }

    public static boolean is(Entity e) {
        return e instanceof Player pl && is(pl.getGameProfile().name());
    }

    /** Fuegt hinzu; false, wenn der Name leer ist oder schon drinsteht. */
    public static boolean add(String name) {
        String n = clean(name);
        if (n.isEmpty()) return false;
        sync();
        if (names.containsKey(key(n))) return false;
        names.put(key(n), n);
        store();
        return true;
    }

    public static boolean remove(String name) {
        sync();
        if (names.remove(key(clean(name))) == null) return false;
        store();
        return true;
    }

    /** Schaltet um; true = jetzt Freund. */
    public static boolean toggle(String name) {
        if (listed(name)) {
            remove(name);
            return false;
        }
        return add(name);
    }

    // ---------- Fragen der anderen Module ----------

    public static int color() {
        return color >= 0 ? 0xFF000000 | color : DEFAULT_COLOR;
    }

    /** ESP: Freundesfarbe statt der normalen Farbe. */
    public static int espColor(Entity e, int normal) {
        return showEsp && is(e) ? color() : normal;
    }

    public static int tracerColor(Entity e, int normal) {
        return showTracers && is(e) ? color() : normal;
    }

    public static int chamsColor(Entity e, int normal) {
        return showChams && is(e) ? color() : normal;
    }

    public static int nametagColor(Entity e, int normal) {
        return showNametags && is(e) ? color() : normal;
    }

    /** Auto Clicker, Multitask: diesen Spieler nicht angreifen. */
    public static boolean protect(Entity e) {
        return noAttack && is(e);
    }

    /** Fuer alle Schlaege (auch die eigenen, ueber den Mixin): abfangen, wenn eingeschaltet. */
    public static boolean blockAttack(Entity e) {
        return blockHits && is(e);
    }

    /** Auto Wasp: nicht zu diesem Spieler fliegen. */
    public static boolean noChase(Entity e) {
        return noChase && is(e);
    }

    /** City Esp: diesen Spieler auslassen. */
    public static boolean skipCity(Entity e) {
        return noCity && is(e);
    }

    // ---------- Mittelklick ----------

    private static boolean pickWasDown;

    /**
     * Jeden Tick vor den Tasten: Mittelklick auf einen Spieler schaltet ihn als Freund um.
     * Die Taste wird dabei nicht verbraucht (Block-Auswahl mit Mittelklick funktioniert weiter).
     * @return true, wenn dieser Klick einen Spieler getroffen hat
     */
    static boolean tickMiddleClick(Minecraft mc, LocalPlayer p) {
        boolean down = mc.options.keyPickItem.isDown();
        boolean pressed = down && !pickWasDown;
        pickWasDown = down;
        if (!pressed || !enabled || !middleClick || mc.gui.screen() != null) return false;
        Player target = lookedAt(mc, p, Math.max(4, middleClickRange));
        if (target == null) return false;
        String name = target.getGameProfile().name();
        boolean now = toggle(name);
        p.sendSystemMessage(Lang.literal(now ? "1912: Freund hinzugefügt: " + name : "1912: Freund entfernt: " + name));
        return true;
    }

    /** Der Spieler im Fadenkreuz (bis range Bloecke, nicht durch Waende). */
    static Player lookedAt(Minecraft mc, LocalPlayer self, double range) {
        Vec3 eye = self.getEyePosition();
        Vec3 end = eye.add(self.getLookAngle().scale(range));
        BlockHitResult wall = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, self));
        double max = wall.getType() == HitResult.Type.MISS ? range : eye.distanceTo(wall.getLocation());
        Player best = null;
        for (Player o : mc.level.players()) {
            if (o == self || !o.isAlive() || o.isSpectator()) continue;
            double f = Combat.rayHit(eye, end, o.getBoundingBox().inflate(0.3));
            if (f < 0) continue;
            double d = f * range;
            if (d < max) {
                max = d;
                best = o;
            }
        }
        return best;
    }

    // ---------- Meldungen ----------

    private static Object lastConnection;
    private static Map<String, String> online = new HashMap<>(); // alle Spieler in der Spielerliste
    private static Map<Integer, String> near = new HashMap<>();  // alle Spieler in Sichtweite (Entity-ID)
    private static int timer;

    /**
     * Alle halbe Sekunde: Freunde kommen online/offline oder in Sichtweite.
     * Verglichen werden alle Spieler, damit das Hinzufuegen eines Freundes keine "online"-Meldung ausloest.
     */
    static void tick(Minecraft mc) {
        var conn = mc.getConnection();
        if (conn == null || mc.player == null || mc.level == null) {
            lastConnection = null;
            online.clear();
            near.clear();
            return;
        }
        if (++timer < 10) return;
        timer = 0;

        Map<String, String> nowOnline = new HashMap<>();
        for (var info : conn.getOnlinePlayers()) {
            String n = info.getProfile().name();
            nowOnline.put(key(n), n);
        }
        Map<Integer, String> nowNear = new HashMap<>();
        for (Player o : mc.level.players()) {
            if (o != mc.player) nowNear.put(o.getId(), o.getGameProfile().name());
        }

        boolean fresh = conn != lastConnection; // gerade verbunden: niemanden als "gerade online" melden
        lastConnection = conn;
        if (!fresh && enabled && notifyOnline) {
            for (var e : nowOnline.entrySet()) {
                if (!online.containsKey(e.getKey()) && listed(e.getValue())) {
                    say(mc, "1912: Freund online: " + e.getValue());
                }
            }
            for (var e : online.entrySet()) {
                if (!nowOnline.containsKey(e.getKey()) && listed(e.getValue())) {
                    say(mc, "1912: Freund offline: " + e.getValue());
                }
            }
        }
        if (!fresh && enabled && notifyNear) {
            for (var e : nowNear.entrySet()) {
                if (!near.containsKey(e.getKey()) && listed(e.getValue())) {
                    say(mc, "1912: Freund in Sichtweite: " + e.getValue());
                }
            }
        }
        online = nowOnline;
        near = nowNear;
    }

    private static void say(Minecraft mc, String text) {
        mc.player.sendSystemMessage(Lang.literal(text));
    }
}
