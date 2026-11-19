package de.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Info-Anzeige: kleines Feld mit Koordinaten (auch umgerechnet fuer Nether/Oberwelt), Richtung, FPS, Ping,
 * TPS des Servers, Geschwindigkeit, Uhrzeit, Ruestung und Totems. Jede Zeile einzeln abschaltbar.
 */
public final class InfoHud {
    private InfoHud() {
    }

    // ---------- Einstellungen (gespeichert ueber TestConfig, Praefix "ih.") ----------

    public static boolean enabled = false;
    public static int corner = 1;                  // 0 oben links, 1 oben rechts, 2 unten links, 3 unten rechts
    public static int size = 1;                    // Index in Theme.HUD_SCALES
    public static boolean coords = true;
    public static boolean otherDim = true;         // Nether-/Oberwelt-Koordinaten
    public static boolean direction = true;
    public static boolean fps = true;
    public static boolean ping = true;
    public static boolean tps = true;
    public static boolean speed = true;
    public static boolean time = false;
    public static boolean armor = true;
    public static boolean totems = true;

    static final String[] CORNERS = {"Oben links", "Oben rechts", "Unten links", "Unten rechts"};

    // ---------- Messwerte ----------

    private static int frames;
    private static long frameSecond;
    private static int fpsNow;
    private static double lastX = Double.NaN;
    private static double lastZ;
    private static double speedNow;
    private static int pingNow = -1;
    private static int pingTimer;
    // Zeitpunkte der letzten Zeit-Pakete (der Server schickt eins pro 20 Server-Ticks)
    private static final long[] timePackets = new long[8];
    private static int timeCount;
    private static volatile Class<?> timeClass;

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("test", "info_hud"),
                Safe.hud("Info-Anzeige", InfoHud::render));
    }

    /** Aus dem Netzwerk-Thread (ConnectionMixin): jedes ankommende Paket. */
    public static void onPacket(Object packet) {
        if (packet == null) return;
        Class<?> c = packet.getClass();
        if (timeClass == null) {
            if (!c.getSimpleName().equals("ClientboundSetTimePacket")) return;
            timeClass = c;
        } else if (c != timeClass) {
            return;
        }
        synchronized (timePackets) {
            timePackets[timeCount % timePackets.length] = System.nanoTime();
            timeCount++;
        }
    }

    /** Geschaetzte TPS des Servers; -1 = noch unbekannt. */
    static double serverTps() {
        synchronized (timePackets) {
            int n = Math.min(timeCount, timePackets.length);
            if (n < 2) return -1;
            long newest = timePackets[(timeCount - 1) % timePackets.length];
            long oldest = timePackets[(timeCount - n) % timePackets.length];
            if (System.nanoTime() - newest > 5_000_000_000L) return 0; // seit 5 s nichts: Server haengt
            double seconds = (newest - oldest) / 1.0e9;
            if (seconds <= 0) return -1;
            return Math.min(20.0, 20.0 * (n - 1) / seconds);
        }
    }

    /** Jeden Tick: Geschwindigkeit und Ping. */
    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) {
            lastX = Double.NaN;
            synchronized (timePackets) {
                timeCount = 0;
            }
            return;
        }
        if (!Double.isNaN(lastX)) {
            double dx = p.getX() - lastX;
            double dz = p.getZ() - lastZ;
            double now = Math.sqrt(dx * dx + dz * dz) * 20.0;
            speedNow = now > 100 ? 0 : speedNow * 0.7 + now * 0.3; // Teleport nicht mitzaehlen
        }
        lastX = p.getX();
        lastZ = p.getZ();
        if (--pingTimer <= 0) {
            pingTimer = 20;
            pingNow = -1;
            var conn = mc.getConnection();
            if (conn != null) {
                String me = p.getGameProfile().name();
                for (var info : conn.getOnlinePlayers()) {
                    if (info.getProfile().name().equals(me)) {
                        pingNow = info.getLatency();
                        break;
                    }
                }
            }
        }
    }

    // ---------- Zeichnen ----------

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        long sec = System.currentTimeMillis() / 1000;
        frames++;
        if (sec != frameSecond) {
            fpsNow = frames;
            frames = 0;
            frameSecond = sec;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!enabled || p == null || mc.level == null || mc.gui.screen() instanceof TestScreen) return;
        var bp = p.blockPosition();

        List<String[]> lines = new ArrayList<>();
        if (coords) {
            lines.add(new String[] {"XYZ", String.format(Locale.ROOT, "%d  %d  %d", bp.getX(), bp.getY(),
                    bp.getZ())});
        }
        if (otherDim) {
            if (mc.level.dimension() == Level.NETHER) {
                lines.add(new String[] {Lang.t("Oberwelt"), (bp.getX() * 8) + "  " + (bp.getZ() * 8)});
            } else if (mc.level.dimension() == Level.OVERWORLD) {
                lines.add(new String[] {Lang.t("Nether"), Math.floorDiv(bp.getX(), 8) + "  "
                        + Math.floorDiv(bp.getZ(), 8)});
            }
        }
        if (direction) lines.add(new String[] {Lang.t("Richtung"), facing(p.getYRot())});
        if (fps) lines.add(new String[] {"FPS", Integer.toString(fpsNow)});
        if (ping && pingNow >= 0) lines.add(new String[] {"Ping", pingNow + " ms"});
        if (tps) {
            double t = serverTps();
            if (t >= 0) lines.add(new String[] {"TPS", String.format(Locale.ROOT, "%.1f", t)});
        }
        if (speed) lines.add(new String[] {Lang.t("Tempo"), String.format(Locale.ROOT, "%.1f b/s", speedNow)});
        if (time) {
            String clock = clock(mc.level);
            if (clock != null) lines.add(new String[] {Lang.t("Uhrzeit"), clock});
        }
        if (armor) {
            String a = armorText(p);
            if (!a.isEmpty()) lines.add(new String[] {Lang.t("Rüstung"), a});
        }
        if (totems) lines.add(new String[] {"Totems", Integer.toString(Survival.totems(p))});
        if (lines.isEmpty()) return;

        var font = mc.font;
        int labelW = 0;
        int valueW = 0;
        for (String[] l : lines) {
            labelW = Math.max(labelW, font.width(l[0]));
            valueW = Math.max(valueW, font.width(l[1]));
        }
        int w = 8 + labelW + 8 + valueW + 8;
        int h = 6 + lines.size() * 11 + 3;
        float scale = Theme.HUD_SCALES[Math.max(0, Math.min(Theme.HUD_SCALES.length - 1, size))];
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        boolean right = corner == 1 || corner == 3;
        boolean bottom = corner >= 2;
        int off = Extras.infoOffset(mc, corner); // Minimap / Tasten-Anzeige in derselben Ecke
        float x = right ? sw - 3 - w * scale : 3;
        float y = bottom ? sh - 3 - h * scale - off : 3 + off;
        if (!right && !bottom && Theme.hudList && Theme.hudCorner == 0) y += 2; // neben der Modul-Liste

        float old = Ui.alpha;
        Ui.alpha = 1f;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(scale, scale);
        try {
            Ui.roundRect(g, 0, 0, w, h, 5, 0xB0101018);
            Ui.fill(g, 3, 0, w - 3, 1, 0xFF000000 | Theme.accent());
            int ly = 5;
            for (String[] l : lines) {
                // Werte enthalten Zahlen und Namen - nie durch die Uebersetzung schicken
                g.text(font, l[0], 8, ly, Ui.mul(Ui.MUTED), true);
                g.text(font, l[1], w - 8 - font.width(l[1]), ly, Ui.mul(0xFFFFFFFF), true);
                ly += 11;
            }
        } finally {
            pose.popMatrix();
            Ui.alpha = old;
        }
    }

    private static String facing(float yaw) {
        float y = ((yaw % 360) + 360) % 360;
        String name;
        if (y < 45 || y >= 315) {
            name = Lang.t("Süden") + " +Z";
        } else if (y < 135) {
            name = Lang.t("Westen") + " -X";
        } else if (y < 225) {
            name = Lang.t("Norden") + " -Z";
        } else {
            name = Lang.t("Osten") + " +X";
        }
        return name;
    }

    /** Ruestung: Haltbarkeit in Prozent je Teil (Kopf bis Fuesse). */
    private static String armorText(LocalPlayer p) {
        StringBuilder sb = new StringBuilder();
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot s : slots) {
            ItemStack st = p.getItemBySlot(s);
            if (st.isEmpty() || st.getMaxDamage() <= 0) continue;
            int pct = (st.getMaxDamage() - st.getDamageValue()) * 100 / st.getMaxDamage();
            String c = pct > 50 ? "§a" : pct > 20 ? "§e" : "§c";
            if (sb.length() > 0) sb.append("§f ");
            sb.append(c).append(pct).append('%');
        }
        return sb.toString();
    }

    private static java.lang.reflect.Method dayTime;
    private static boolean dayTimeLooked;

    /**
     * Uhrzeit im Spiel (06:00 = Sonnenaufgang). Ueber Reflection, weil sich die Zeit-Methoden in 26.x
     * geaendert haben (siehe Time Changer); gibt es keine, faellt die Zeile einfach weg.
     */
    private static String clock(Level level) {
        try {
            if (!dayTimeLooked) {
                dayTimeLooked = true;
                for (var m : level.getClass().getMethods()) {
                    if (m.getName().equals("getDayTime") && m.getParameterCount() == 0) {
                        dayTime = m;
                        break;
                    }
                }
            }
            if (dayTime == null) return null;
            long t = ((Number) dayTime.invoke(level)).longValue();
            long d = Math.floorMod(t + 6000, 24000);
            return String.format(Locale.ROOT, "%02d:%02d", d / 1000, d % 1000 * 60 / 1000);
        } catch (ReflectiveOperationException | RuntimeException e) {
            dayTime = null;
            return null;
        }
    }

    // ---------- Menue ----------

    static Screen screen(Screen parent) {
        return new Form(Component.literal("Info-Anzeige"), parent) {
            @Override
            protected void build() {
                description("Kleines Feld im Bild mit den wichtigsten Werten. Jede Zeile lässt sich einzeln abschalten.");
                section("Platz");
                choice("Ecke", CORNERS, () -> corner, v -> corner = v, 1);
                choice("Größe", Theme.HUD_SIZE_NAMES, () -> size, v -> size = v, 1);
                section("Zeilen");
                toggle("Koordinaten", () -> coords, v -> coords = v, true);
                toggle("Nether-/Oberwelt-Koordinaten", () -> otherDim, v -> otherDim = v, true);
                toggle("Richtung", () -> direction, v -> direction = v, true);
                toggle("FPS", () -> fps, v -> fps = v, true);
                toggle("Ping", () -> ping, v -> ping = v, true);
                toggle("TPS des Servers", () -> tps, v -> tps = v, true);
                toggle("Tempo", () -> speed, v -> speed = v, true);
                toggle("Uhrzeit im Spiel", () -> time, v -> time = v, false);
                toggle("Rüstung (Haltbarkeit)", () -> armor, v -> armor = v, true);
                toggle("Totems", () -> totems, v -> totems = v, true);
                note("TPS zeigt, wie schnell der Server läuft (20 = normal, weniger = Server laggt). "
                        + "Nether-Koordinaten: im Nether siehst du, wo du in der Oberwelt rauskommst, und umgekehrt.");
                footer(() -> enabled, v -> enabled = v);
            }
        };
    }
}
