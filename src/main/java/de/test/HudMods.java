package de.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** Spieler-Radar, Schadenszahlen, Kompass-Leiste und Chunk-Grenzen. */
public final class HudMods {
    private HudMods() {
    }

    // =====================================================================================
    // Einstellungen (werden mit dem Praefix "hm." gespeichert)
    // =====================================================================================

    // --- Spieler-Radar ---
    public static boolean radar = false;
    public static int radarCorner = 1;         // InfoHud.CORNERS
    public static int radarMax = 8;
    public static int radarRange = 0;          // Bloecke, 0 = alle, die geladen sind
    public static boolean radarHealth = true;
    public static boolean radarArrow = true;

    // --- Schadenszahlen ---
    public static boolean damage = false;
    public static boolean dmgOnlyOwn = true;
    public static boolean dmgHearts = true;
    public static int dmgSize = 1;             // Extras.SIZES

    // --- Kompass-Leiste ---
    public static boolean compass = false;
    public static int compassWidth = 220;
    public static int compassY = 4;
    public static boolean compassWaypoints = true;
    public static boolean compassDegrees = true;

    // --- Chunk-Grenzen ---
    public static boolean chunks = false;
    public static boolean chunksNeighbors = true;
    public static int chunksHeight = 16;       // Bloecke ueber und unter dir

    static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("test", "hud_mods"),
                Safe.hud("Anzeige", HudMods::render));
    }

    static void reset() {
        lastHealth.clear();
        numbers.clear();
        lastHits.clear();
    }

    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            reset();
            return;
        }
        Safe.run("Schadenszahlen", () -> damageTick(mc, p));
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) return;
        float old = Ui.alpha;
        Ui.alpha = 1f;
        try {
            boolean world = (chunks || damage) && Render3D.begin(mc, delta.getGameTimeDeltaPartialTick(true));
            if (chunks && world) Safe.run("Chunk-Grenzen", () -> drawChunks(g, mc));
            if (damage && world) Safe.run("Schadenszahlen", () -> drawNumbers(g, mc));
            if (compass) Safe.run("Kompass-Leiste", () -> drawCompass(g, mc));
            if (radar) Safe.run("Spieler-Radar", () -> drawRadar(g, mc));
        } finally {
            Ui.alpha = old;
        }
    }

    // =====================================================================================
    // Spieler-Radar
    // =====================================================================================

    private static void drawRadar(GuiGraphicsExtractor g, Minecraft mc) {
        LocalPlayer self = mc.player;
        List<Player> list = new ArrayList<>();
        for (Player pl : mc.level.players()) {
            if (pl == self || pl.isSpectator() || PlayerMods.isFake(pl)) continue;
            if (radarRange > 0 && self.distanceTo(pl) > radarRange) continue;
            list.add(pl);
        }
        list.sort((a, b) -> Double.compare(self.distanceToSqr(a), self.distanceToSqr(b)));
        int n = Math.min(list.size(), Math.max(1, radarMax));
        var font = mc.font;
        String title = Lang.t("Spieler in der Nähe") + " (" + list.size() + ")";
        int w = font.width(title) + 12;
        String[] left = new String[n];
        String[] right = new String[n];
        for (int i = 0; i < n; i++) {
            Player pl = list.get(i);
            left[i] = pl.getName().getString(); // Namen nie uebersetzen
            StringBuilder r = new StringBuilder();
            r.append(Math.round(self.distanceTo(pl))).append('m');
            if (radarHealth) {
                float hp = (pl.getHealth() + pl.getAbsorptionAmount()) / 2f;
                r.append("  ").append(String.format(Locale.ROOT, "%.1f", hp).replace(".0", "")).append(" ❤");
            }
            right[i] = r.toString();
            w = Math.max(w, 8 + (radarArrow ? 10 : 0) + font.width(left[i]) + 10 + font.width(right[i]) + 8);
        }
        int h = 16 + n * 11 + (list.isEmpty() ? 11 : 0) + 4;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        boolean rightSide = radarCorner == 1 || radarCorner == 3;
        boolean bottom = radarCorner >= 2;
        int off = Extras.infoOffset(mc, radarCorner);
        if (off == 0 && radarCorner == 1) off = Extras.baseTop(mc, 1); // unter die Trank-Symbole
        int x = rightSide ? sw - 4 - w : 4;
        int y = bottom ? sh - 4 - h - off : 4 + off;
        Ui.roundRect(g, x, y, w, h, 4, 0xB0101018);
        Ui.fill(g, x + 2, y, x + w - 2, y + 1, 0xFF000000 | Theme.accent());
        Ui.text(g, font, title, x + 6, y + 4, 0xFFFFFFFF, true);
        if (list.isEmpty()) {
            Ui.text(g, font, Lang.t("niemand"), x + 6, y + 16, 0xFF9AA0B0, true);
            return;
        }
        for (int i = 0; i < n; i++) {
            Player pl = list.get(i);
            int ly = y + 16 + i * 11;
            int tx = x + 6;
            if (radarArrow) {
                drawArrow(g, tx + 3, ly + 4, angleTo(self, pl), 0xFFDDE3F0);
                tx += 10;
            }
            int col = Friends.is(pl) ? Friends.color() : 0xFFFFFFFF;
            Ui.text(g, font, left[i], tx, ly, col, true);
            Ui.text(g, font, right[i], x + w - 6 - font.width(right[i]), ly, 0xFFFF8A9A, true);
        }
    }

    /** Richtung zum anderen Spieler, bezogen auf deine Blickrichtung (Grad, 0 = geradeaus). */
    private static float angleTo(LocalPlayer self, Entity other) {
        double dx = other.getX() - self.getX();
        double dz = other.getZ() - self.getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        return Mth.wrapDegrees(yaw - self.getYRot());
    }

    /** Kleiner Pfeil aus Punkten (3x3), der in die Richtung zeigt. */
    private static void drawArrow(GuiGraphicsExtractor g, int cx, int cy, float angle, int color) {
        double a = Math.toRadians(angle);
        double sx = Math.sin(a);
        double sy = -Math.cos(a);
        for (int i = -3; i <= 3; i++) {
            int px = (int) Math.round(cx + sx * i);
            int py = (int) Math.round(cy + sy * i);
            Ui.fill(g, px, py, px + 1, py + 1, color);
        }
        // Spitze
        double bx = cx + sx * 3;
        double by = cy + sy * 3;
        for (int k = -1; k <= 1; k += 2) {
            double ra = a + Math.PI + k * 0.6;
            for (int i = 1; i <= 2; i++) {
                int px = (int) Math.round(bx + Math.sin(ra) * i);
                int py = (int) Math.round(by - Math.cos(ra) * i);
                Ui.fill(g, px, py, px + 1, py + 1, color);
            }
        }
    }

    // =====================================================================================
    // Schadenszahlen
    // =====================================================================================

    private record Popup(double x, double y, double z, String text, int color, long born) {
    }

    private static final Map<Integer, Float> lastHealth = new HashMap<>();
    private static final Map<Integer, Long> lastHits = new HashMap<>();
    private static final List<Popup> numbers = new ArrayList<>();
    private static final java.util.Random RNG = new java.util.Random();
    private static int dmgNow;

    /** Vom MultiPlayerGameModeMixin bei jedem eigenen Schlag. */
    public static void noteAttack(Entity target) {
        if (target != null) lastHits.put(target.getId(), System.currentTimeMillis());
    }

    private static void damageTick(Minecraft mc, LocalPlayer p) {
        if (!damage) {
            if (!lastHealth.isEmpty()) reset();
            return;
        }
        long now = System.currentTimeMillis();
        Map<Integer, Float> seen = new HashMap<>();
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(32))) {
            if (!(e instanceof LivingEntity le)) continue;
            float hp = le.getHealth() + le.getAbsorptionAmount();
            seen.put(le.getId(), hp);
            Float before = lastHealth.get(le.getId());
            if (before == null || hp >= before - 0.01f) continue;
            if (dmgOnlyOwn) {
                Long hit = lastHits.get(le.getId());
                if (hit == null || now - hit > 1500) continue;
            }
            float d = before - hp;
            String text = dmgHearts
                    ? String.format(Locale.ROOT, "%.1f", d / 2f).replace(".0", "") + " ❤"
                    : String.format(Locale.ROOT, "%.1f", d).replace(".0", "");
            int color = !le.isAlive() || hp <= 0 ? 0xFFFFD84D : d >= 8 ? 0xFFFF4D4D : 0xFFFF8A8A;
            double jx = (RNG.nextDouble() - 0.5) * 0.6;
            double jz = (RNG.nextDouble() - 0.5) * 0.6;
            numbers.add(new Popup(le.getX() + jx, le.getY() + le.getBbHeight() + 0.2, le.getZ() + jz, "-" + text,
                    color, now));
        }
        lastHealth.clear();
        lastHealth.putAll(seen);
        if (++dmgNow % 100 == 0) lastHits.values().removeIf(t -> now - t > 5000);
        numbers.removeIf(nb -> now - nb.born() > 1200);
        while (numbers.size() > 40) numbers.remove(0);
    }

    private static void drawNumbers(GuiGraphicsExtractor g, Minecraft mc) {
        long now = System.currentTimeMillis();
        float scale = new float[] {0.75f, 1f, 1.4f}[Math.max(0, Math.min(2, dmgSize))];
        var pose = g.pose();
        for (Iterator<Popup> it = numbers.iterator(); it.hasNext(); ) {
            Popup nb = it.next();
            float age = (now - nb.born()) / 1200f;
            if (age >= 1f) continue;
            double[] s = Render3D.project(nb.x(), nb.y() + age * 0.8, nb.z());
            if (s[2] <= 0.05) continue; // hinter der Kamera
            float alpha = age < 0.7f ? 1f : 1f - (age - 0.7f) / 0.3f;
            Ui.alpha = alpha;
            int tw = mc.font.width(nb.text());
            pose.pushMatrix();
            pose.translate((float) s[0], (float) s[1]);
            pose.scale(scale, scale);
            Ui.text(g, mc.font, nb.text(), -tw / 2, -4, nb.color(), true);
            pose.popMatrix();
        }
        Ui.alpha = 1f;
    }

    // =====================================================================================
    // Kompass-Leiste
    // =====================================================================================

    /** Himmelsrichtungen je Sprache (einzelne Buchstaben laufen nicht durch die Uebersetzung). */
    private static String[] dirs() {
        return switch (Lang.code()) {
            case "en" -> new String[] {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
            case "es" -> new String[] {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
            case "fr" -> new String[] {"N", "NE", "E", "SE", "S", "SO", "O", "NO"};
            default -> new String[] {"N", "NO", "O", "SO", "S", "SW", "W", "NW"};
        };
    }

    private static void drawCompass(GuiGraphicsExtractor g, Minecraft mc) {
        LocalPlayer p = mc.player;
        var font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int w = Math.min(sw - 20, Math.max(100, compassWidth));
        int x0 = (sw - w) / 2;
        int y0 = Math.max(0, compassY);
        int h = 16;
        float fov = 180f; // so viel Grad zeigt die Leiste
        float bearing = Mth.wrapDegrees(p.getYRot() + 180f); // 0 = Norden, 90 = Osten
        if (bearing < 0) bearing += 360f;
        Ui.roundRect(g, x0, y0, w, h, 4, 0x90101018);
        int cx = x0 + w / 2;
        String[] dirs = dirs();
        // Striche alle 15 Grad, Buchstaben alle 45 Grad
        for (int a = 0; a < 360; a += 15) {
            float d = Mth.wrapDegrees(a - bearing);
            if (Math.abs(d) > fov / 2f) continue;
            int x = Math.round(cx + d * w / fov);
            if (x < x0 + 3 || x > x0 + w - 3) continue;
            float fade = 1f - Math.abs(d) / (fov / 2f) * 0.7f;
            Ui.alpha = fade;
            if (a % 45 == 0) {
                String lbl = dirs[a / 45];
                int c = a == 0 ? 0xFFFF5C5C : 0xFFFFFFFF;
                Ui.text(g, font, lbl, x - font.width(lbl) / 2, y0 + 4, c, true);
            } else {
                Ui.fill(g, x, y0 + 6, x + 1, y0 + 10, 0xFFB0B8C8);
            }
        }
        Ui.alpha = 1f;
        // Wegpunkte als kleine Rauten
        if (compassWaypoints && RenderMods.waypointList != null && !RenderMods.waypointList.isEmpty()) {
            for (String entry : RenderMods.waypointList.split(";")) {
                String[] f = entry.split(",");
                if (f.length < 4) continue;
                try {
                    double wx = Integer.parseInt(f[1].trim()) + 0.5;
                    double wz = Integer.parseInt(f[3].trim()) + 0.5;
                    double dx = wx - p.getX();
                    double dz = wz - p.getZ();
                    float wb = Mth.wrapDegrees((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0) + 180f);
                    float d = Mth.wrapDegrees(wb - bearing);
                    if (Math.abs(d) > fov / 2f) continue;
                    int x = Math.round(cx + d * w / fov);
                    int c = 0xFF000000 | Theme.accent();
                    Ui.fill(g, x - 1, y0 + h - 5, x + 2, y0 + h - 2, c);
                    Ui.fill(g, x, y0 + h - 6, x + 1, y0 + h - 1, c);
                    if (Math.abs(d) < 6f) { // fast genau vor dir: Name und Entfernung darunter
                        String t = f[0].trim() + "  " + Math.round(Math.sqrt(dx * dx + dz * dz)) + "m";
                        Ui.text(g, font, t, cx - font.width(t) / 2, y0 + h + 2, c, true);
                    }
                } catch (NumberFormatException ignored) {
                    // kaputter Eintrag
                }
            }
        }
        // Mitte
        Ui.fill(g, cx, y0 - 1, cx + 1, y0 + 3, 0xFFFFFFFF);
        if (compassDegrees) {
            String deg = Math.round(bearing) % 360 + "°";
            int ty = y0 + h + 2 + (compassWaypoints ? 10 : 0);
            Ui.text(g, font, deg, cx - font.width(deg) / 2, ty, 0xFFDDE3F0, true);
        }
    }

    // =====================================================================================
    // Chunk-Grenzen
    // =====================================================================================

    private static void drawChunks(GuiGraphicsExtractor g, Minecraft mc) {
        LocalPlayer p = mc.player;
        int cx = Mth.floor(p.getX()) >> 4;
        int cz = Mth.floor(p.getZ()) >> 4;
        double y = Math.floor(p.getY());
        int hh = Math.max(2, chunksHeight);
        int r = chunksNeighbors ? 1 : 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx == 0 && dz == 0) continue;
                double x0 = (cx + dx) << 4;
                double z0 = (cz + dz) << 4;
                Render3D.wire(g, new AABB(x0, y - hh, z0, x0 + 16, y + hh, z0 + 16), 0x904D9DFF);
            }
        }
        double x0 = cx << 4;
        double z0 = cz << 4;
        int c = 0xFFFFD84D;
        Render3D.wire(g, new AABB(x0, y - hh, z0, x0 + 16, y + hh, z0 + 16), c);
        // Linie am Boden (auf deiner Hoehe), damit man die Grenze beim Gehen sieht
        Render3D.worldLine(g, x0, y + 0.02, z0, x0 + 16, y + 0.02, z0, c);
        Render3D.worldLine(g, x0 + 16, y + 0.02, z0, x0 + 16, y + 0.02, z0 + 16, c);
        Render3D.worldLine(g, x0 + 16, y + 0.02, z0 + 16, x0, y + 0.02, z0 + 16, c);
        Render3D.worldLine(g, x0, y + 0.02, z0 + 16, x0, y + 0.02, z0, c);
    }

    // =====================================================================================
    // Einstellungen
    // =====================================================================================

    static Screen screen(String module, Screen parent) {
        return switch (module) {
            case "Spieler-Radar" -> new ModuleScreen(parent, module, f -> {
                f.description("Liste der Spieler in deiner Nähe - mit Entfernung, Leben und einem Pfeil in ihre "
                        + "Richtung.");
                f.choice("Ecke", InfoHud.CORNERS, () -> radarCorner, v -> radarCorner = v, 1);
                f.slider("Höchstens (Spieler)", 1, 20, () -> radarMax, v -> radarMax = v, 8);
                f.slider("Bis (Blöcke, 0 = alle)", 0, 500, () -> radarRange, v -> radarRange = v, 0);
                f.toggle("Leben zeigen", () -> radarHealth, v -> radarHealth = v, true);
                f.toggle("Pfeil in ihre Richtung", () -> radarArrow, v -> radarArrow = v, true);
                f.note("Man sieht nur Spieler, die dein Spiel geladen hat (meist bis etwa 100 Blöcke). Freunde "
                        + "stehen in ihrer Farbe da.");
                f.footer(() -> radar, v -> radar = v);
            });
            case "Schadenszahlen" -> new ModuleScreen(parent, module, f -> {
                f.description("Zeigt beim Treffen kurz an, wie viel Schaden du gemacht hast.");
                f.toggle("Nur eigene Treffer", () -> dmgOnlyOwn, v -> dmgOnlyOwn = v, true);
                f.toggle("In Herzen (sonst Lebenspunkte)", () -> dmgHearts, v -> dmgHearts = v, true);
                f.choice("Größe", Extras.SIZES, () -> dmgSize, v -> dmgSize = v, 1);
                f.note("Gelb: der Treffer war tödlich. Ohne „Nur eigene Treffer“ siehst du jeden Schaden in der "
                        + "Nähe, auch von Fallschaden oder anderen Spielern.");
                f.footer(() -> damage, v -> damage = v);
            });
            case "Kompass-Leiste" -> new ModuleScreen(parent, module, f -> {
                f.description("Ein Kompass oben am Bildschirm: Himmelsrichtungen, Gradzahl und deine Wegpunkte.");
                f.slider("Breite", 100, 400, () -> compassWidth, v -> compassWidth = v, 220);
                f.slider("Abstand von oben", 0, 80, () -> compassY, v -> compassY = v, 4);
                f.toggle("Wegpunkte zeigen", () -> compassWaypoints, v -> compassWaypoints = v, true);
                f.toggle("Gradzahl zeigen", () -> compassDegrees, v -> compassDegrees = v, true);
                f.note("Stehen oben Bossleisten, schieb den Kompass mit „Abstand von oben“ darunter.");
                f.footer(() -> compass, v -> compass = v);
            });
            case "Chunk-Grenzen" -> new ModuleScreen(parent, module, f -> {
                f.description("Zeigt die Grenzen des Chunks, in dem du stehst, als gelbe Linien.");
                f.toggle("Auch die Nachbar-Chunks (blau)", () -> chunksNeighbors, v -> chunksNeighbors = v, true);
                f.slider("Höhe über und unter dir", 2, 64, () -> chunksHeight, v -> chunksHeight = v, 16);
                f.footer(() -> chunks, v -> chunks = v);
            });
            default -> null;
        };
    }
}
