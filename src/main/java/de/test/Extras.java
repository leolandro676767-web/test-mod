package de.test;

import java.util.ArrayDeque;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** Zoom, Minimap, Tasten-Anzeige, Haltbarkeits-Warnung, Boat Fly und Fadenkreuz. */
public final class Extras {
    private Extras() {
    }

    // =====================================================================================
    // Einstellungen (werden mit dem Praefix "ex." gespeichert)
    // =====================================================================================

    // --- Zoom ---
    public static boolean zoom = true;
    public static int zoomStrength = 4;        // 2..10-fach
    public static boolean zoomScroll = true;   // Mausrad aendert die Staerke
    public static boolean zoomSmooth = true;   // weich hinein- und herauszoomen
    public static boolean zoomSlowMouse = true;
    public static boolean zoomCinematic = false;

    // --- Minimap ---
    public static boolean minimap = false;
    public static int mapSize = 1;             // 0 klein, 1 mittel, 2 gross
    public static int mapZoom = 1;             // 0 nah (1 Block je Feld), 1 mittel (2), 2 weit (4)
    public static int mapCorner = 1;           // InfoHud.CORNERS
    public static boolean mapCoords = true;
    public static boolean mapWaypoints = true;
    public static boolean mapPlayers = false;
    public static boolean mapCaves = true;     // unter der Erde und im Nether: Hoehlen statt Oberflaeche

    // --- Tasten-Anzeige ---
    public static boolean keys = false;
    public static int keysCorner = 3;
    public static int keysSize = 1;            // 0 klein, 1 normal, 2 gross
    public static boolean keysMouse = true;
    public static boolean keysCps = true;
    public static boolean keysSpace = true;

    // --- Haltbarkeit ---
    public static boolean durability = true;
    public static int durPercent = 10;
    public static boolean durSound = true;
    public static boolean durScreen = true;

    // --- Boat Fly ---
    public static boolean boatFly = false;
    public static int boatSpeed = 20;          // Bloecke pro Sekunde
    public static int boatVertical = 8;
    public static boolean boatFollowLook = true;
    public static boolean boatAntiKick = true;

    // --- Fadenkreuz ---
    public static boolean crosshair = false;
    public static int chShape = 1;             // CH_SHAPES
    public static int chColor = 0;             // CH_COLORS
    public static int chSize = 5;              // Laenge der Striche
    public static int chThick = 1;
    public static int chGap = 2;
    public static boolean chOutline = true;
    public static boolean chTarget = true;     // auf Gegnern rot
    public static boolean chAttack = true;     // Abklingzeit unter dem Fadenkreuz
    public static boolean chThird = false;     // auch in der Ansicht von hinten/vorne

    static final String[] CH_SHAPES = {"Kreuz", "Kreuz mit Punkt", "Punkt", "Kreis", "T-Form"};
    static final String[] CH_COLORS = {"Weiß", "Grün", "Cyan", "Gelb", "Pink", "Rot", "Designfarbe"};
    private static final int[] CH_RGB = {0xFFFFFFFF, 0xFF5CFF7A, 0xFF4DE6FF, 0xFFFFE04D, 0xFFFF6BD5, 0xFFFF4D4D, 0};

    static final String[] SIZES = {"Klein", "Mittel", "Groß"};
    static final String[] MAP_ZOOMS = {"Nah", "Mittel", "Weit"};

    /** Taste fuer den Zoom (in den Minecraft-Steuerungen, Standard C). */
    static KeyMapping zoomKey;

    static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("test", "extras"), Safe.hud("Extras", Extras::render));
    }

    static void tick(Minecraft mc) {
        Safe.run("Zoom", () -> zoomTick(mc));
        Safe.run("Minimap", () -> mapTick(mc));
        Safe.run("Haltbarkeit", () -> durabilityTick(mc));
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return; // F1
        float old = Ui.alpha;
        Ui.alpha = 1f;
        try {
            if (minimap) Safe.run("Minimap", () -> drawMap(g, mc));
            if (keys) Safe.run("Tasten-Anzeige", () -> drawKeys(g, mc));
            if (durability && durScreen) Safe.run("Haltbarkeit", () -> drawDurability(g, mc));
        } finally {
            Ui.alpha = old;
        }
    }

    // =====================================================================================
    // Zoom
    // =====================================================================================

    private static float zoomNow = 1f;      // aktuelle Vergroesserung (weich)
    private static float scrollMul = 1f;    // mit dem Mausrad dazu
    private static long zoomLast;
    private static boolean cineSet;
    private static boolean cineBefore;

    /** Gerade am Zoomen (Taste gehalten, kein Menue offen)? */
    static boolean zooming() {
        Minecraft mc = Minecraft.getInstance();
        return zoom && zoomKey != null && mc.player != null && mc.gui.screen() == null && zoomKey.isDown();
    }

    private static float zoomTarget() {
        return zooming() ? Math.max(1.5f, Math.min(50f, zoomStrength * scrollMul)) : 1f;
    }

    /** Vergroesserung fuer dieses Bild (vom CameraMixin): 1 = normal. */
    public static float zoomFactor() {
        long now = System.nanoTime();
        float dt = zoomLast == 0 ? 0f : Math.min(0.1f, (now - zoomLast) / 1.0e9f);
        zoomLast = now;
        float target = zoomTarget();
        if (!zoomSmooth) {
            zoomNow = target;
        } else {
            zoomNow += (target - zoomNow) * (1f - (float) Math.exp(-dt * 14f));
            if (Math.abs(target - zoomNow) < 0.002f) zoomNow = target;
        }
        return zoomNow;
    }

    /** Fuer die Maus (MouseHandlerMixin): beim Zoomen langsamer drehen. */
    public static double mouseScale() {
        if (!zoomSlowMouse || zoomNow <= 1.01f) return 1.0;
        return 1.0 / zoomNow;
    }

    /** Mausrad beim Zoomen: Staerke aendern (true = verbraucht, Hotbar bleibt). */
    public static boolean onScroll(double amount) {
        if (!zoomScroll || !zooming() || amount == 0) return false;
        scrollMul *= amount > 0 ? 1.2f : 1f / 1.2f;
        float total = zoomStrength * scrollMul;
        if (total > 50f) scrollMul = 50f / zoomStrength;
        if (total < 1.5f) scrollMul = 1.5f / zoomStrength;
        return true;
    }

    private static void zoomTick(Minecraft mc) {
        boolean z = zooming();
        if (!z) scrollMul = 1f;
        // Kino-Kamera nur waehrend des Zooms
        if (z && zoomCinematic && !cineSet) {
            cineBefore = mc.options.smoothCamera;
            mc.options.smoothCamera = true;
            cineSet = true;
        } else if ((!z || !zoomCinematic) && cineSet) {
            mc.options.smoothCamera = cineBefore;
            cineSet = false;
        }
    }

    // =====================================================================================
    // Minimap
    // =====================================================================================

    private static final int[] SIZE_PX = {64, 96, 128};
    private static final int[] PER_CELL = {1, 2, 4};

    // fertige Karte (wird angezeigt) und die, die gerade berechnet wird
    private static int[] shown = new int[0];
    private static int shownN;
    private static int shownX;
    private static int shownZ;
    private static int shownPer;
    private static boolean shownCave;
    private static int[] building = new int[0];
    private static int[] heights = new int[0];
    private static int buildRow = -1;
    private static int buildN;
    private static int buildX;
    private static int buildZ;
    private static int buildY;
    private static int buildPer;
    private static boolean buildCave;

    private static void mapTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (!minimap || p == null || level == null) {
            buildRow = -1;
            return;
        }
        int n = SIZE_PX[Math.max(0, Math.min(2, mapSize))] / 2;
        if (buildRow < 0) {
            // neuen Durchlauf beginnen: Mitte und Art festhalten
            buildN = n;
            buildPer = PER_CELL[Math.max(0, Math.min(2, mapZoom))];
            buildX = p.getBlockX();
            buildZ = p.getBlockZ();
            buildY = p.getBlockY();
            buildCave = mapCaves && (level.dimensionType().hasCeiling() || underground(level, p));
            if (building.length != n * n) {
                building = new int[n * n];
                heights = new int[n * n];
            }
            buildRow = 0;
        }
        // ein Viertel der Zeilen pro Tick: alle 4 Ticks eine neue Karte, ohne Ruckler
        int rows = Math.max(1, (buildN + 3) / 4);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int k = 0; k < rows && buildRow < buildN; k++, buildRow++) {
            int r = buildRow;
            for (int c = 0; c < buildN; c++) {
                int wx = buildX + (c - buildN / 2) * buildPer;
                int wz = buildZ + (r - buildN / 2) * buildPer;
                int i = r * buildN + c;
                if (!level.hasChunk(wx >> 4, wz >> 4)) {
                    building[i] = 0xFF101014;
                    heights[i] = Integer.MIN_VALUE;
                    continue;
                }
                if (buildCave) {
                    caveCell(level, m, wx, wz, i);
                } else {
                    surfaceCell(level, m, wx, wz, i, r > 0 ? heights[i - buildN] : Integer.MIN_VALUE);
                }
            }
        }
        if (buildRow >= buildN) {
            int[] t = shown;
            shown = building;
            building = t.length == shown.length ? t : new int[shown.length];
            shownN = buildN;
            shownX = buildX;
            shownZ = buildZ;
            shownPer = buildPer;
            shownCave = buildCave;
            buildRow = -1;
        }
    }

    /** Unter der Erde: weit unter der Oberflaeche und kaum Himmelslicht (unter Baeumen gibt es noch welches). */
    private static boolean underground(ClientLevel level, LocalPlayer p) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, p.getBlockX(), p.getBlockZ());
        return top > p.getBlockY() + 6 && level.getBrightness(LightLayer.SKY, p.blockPosition().above()) <= 3;
    }

    private static void surfaceCell(ClientLevel level, BlockPos.MutableBlockPos m, int wx, int wz, int i, int north) {
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
        int minY = level.getMinY();
        BlockState st = null;
        MapColor col = MapColor.NONE;
        for (int k = 0; k < 8 && y >= minY; k++, y--) {
            m.set(wx, y, wz);
            st = level.getBlockState(m);
            col = st.getMapColor(level, m);
            if (col != MapColor.NONE) break;
        }
        if (col == MapColor.NONE || st == null) {
            building[i] = 0xFF101014;
            heights[i] = Integer.MIN_VALUE;
            return;
        }
        heights[i] = y;
        MapColor.Brightness b;
        if (!st.getFluidState().isEmpty()) {
            // Wasser: je tiefer, desto dunkler
            int depth = 0;
            int yy = y;
            while (depth < 10 && yy > minY) {
                m.set(wx, --yy, wz);
                if (level.getBlockState(m).getFluidState().isEmpty()) break;
                depth++;
            }
            b = depth >= 6 ? MapColor.Brightness.LOW : depth >= 2 ? MapColor.Brightness.NORMAL : MapColor.Brightness.HIGH;
        } else if (north == Integer.MIN_VALUE || north == y) {
            b = MapColor.Brightness.NORMAL;
        } else {
            b = y > north ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
        }
        building[i] = 0xFF000000 | col.calculateARGBColor(b);
    }

    private static void caveCell(ClientLevel level, BlockPos.MutableBlockPos m, int wx, int wz, int i) {
        heights[i] = Integer.MIN_VALUE;
        // von Kopfhoehe nach unten: der erste feste Block mit Luft darueber ist der Boden
        boolean airAbove = false;
        for (int y = buildY + 2; y >= buildY - 16; y--) {
            m.set(wx, y, wz);
            BlockState st = level.getBlockState(m);
            boolean open = st.getCollisionShape(level, m).isEmpty();
            if (!open && airAbove) {
                MapColor col = st.getMapColor(level, m);
                if (col == MapColor.NONE) col = MapColor.STONE;
                int d = buildY - y;
                MapColor.Brightness b = d <= 1 ? MapColor.Brightness.HIGH : d <= 4 ? MapColor.Brightness.NORMAL
                        : d <= 9 ? MapColor.Brightness.LOW : MapColor.Brightness.LOWEST;
                building[i] = 0xFF000000 | col.calculateARGBColor(b);
                return;
            }
            if (open && !st.getFluidState().isEmpty() && airAbove) {
                building[i] = 0xFF000000 | st.getMapColor(level, m).calculateARGBColor(MapColor.Brightness.NORMAL);
                return;
            }
            airAbove = open;
        }
        building[i] = 0xFF18161C; // Wand / kein Boden in Reichweite
    }

    private static void drawMap(GuiGraphicsExtractor g, Minecraft mc) {
        int n = shownN;
        if (n == 0 || shown.length < n * n) return;
        LocalPlayer p = mc.player;
        int cell = 2;
        int size = n * cell;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        boolean right = mapCorner == 1 || mapCorner == 3;
        boolean bottom = mapCorner >= 2;
        int textH = mapCoords ? 11 : 0;
        int x0 = right ? sw - size - 4 : 4;
        int y0 = bottom ? sh - size - 4 - textH : 4 + baseTop(mc, mapCorner);
        // Rahmen
        Ui.fill(g, x0 - 2, y0 - 2, x0 + size + 2, y0 + size + 2, 0xC0000000);
        Ui.fill(g, x0 - 1, y0 - 1, x0 + size + 1, y0 + size + 1, Ui.lerp(Theme.accent(), 0xFF000000, 0.45f));
        // Felder: gleiche Farben in einer Zeile zusammen zeichnen
        for (int r = 0; r < n; r++) {
            int c0 = 0;
            while (c0 < n) {
                int col = shown[r * n + c0];
                int c1 = c0 + 1;
                while (c1 < n && shown[r * n + c1] == col) c1++;
                Ui.fill(g, x0 + c0 * cell, y0 + r * cell, x0 + c1 * cell, y0 + r * cell + cell, col);
                c0 = c1;
            }
        }
        int cx = x0 + (n / 2) * cell + 1;
        int cy = y0 + (n / 2) * cell + 1;
        // Wegpunkte
        if (mapWaypoints && RenderMods.waypointList != null && !RenderMods.waypointList.isEmpty()) {
            for (String part : RenderMods.waypointList.split(";")) {
                String[] f = part.split(",");
                if (f.length < 4) continue;
                try {
                    int wx = Integer.parseInt(f[1].trim());
                    int wz = Integer.parseInt(f[3].trim());
                    double dx = (wx - shownX) / (double) shownPer * cell;
                    double dz = (wz - shownZ) / (double) shownPer * cell;
                    int px = (int) Math.round(Math.max(-size / 2.0 + 2, Math.min(size / 2.0 - 2, dx)));
                    int pz = (int) Math.round(Math.max(-size / 2.0 + 2, Math.min(size / 2.0 - 2, dz)));
                    Ui.fill(g, cx + px - 2, cy + pz - 2, cx + px + 2, cy + pz + 2, 0xFF000000);
                    Ui.fill(g, cx + px - 1, cy + pz - 1, cx + px + 1, cy + pz + 1, 0xFFFFD24D);
                } catch (NumberFormatException ignored) {
                    // kaputter Eintrag
                }
            }
        }
        // andere Spieler
        if (mapPlayers) {
            for (Player o : mc.level.players()) {
                if (o == p) continue;
                double dx = (o.getX() - shownX) / shownPer * cell;
                double dz = (o.getZ() - shownZ) / shownPer * cell;
                if (Math.abs(dx) > size / 2.0 - 2 || Math.abs(dz) > size / 2.0 - 2) continue;
                int px = (int) Math.round(dx);
                int pz = (int) Math.round(dz);
                int color = Friends.espColor(o, 0xFFFF4D4D);
                Ui.fill(g, cx + px - 2, cy + pz - 2, cx + px + 2, cy + pz + 2, 0xFF000000);
                Ui.fill(g, cx + px - 1, cy + pz - 1, cx + px + 1, cy + pz + 1, color);
            }
        }
        // Spieler: Punkt mit Pfeil in Blickrichtung (Norden ist oben)
        double yaw = Math.toRadians(p.getYRot());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        for (int k = 1; k <= 6; k++) {
            int ax = cx + (int) Math.round(fx * k);
            int az = cy + (int) Math.round(fz * k);
            Ui.fill(g, ax - 1, az - 1, ax + 1, az + 1, 0xFFFFFFFF);
        }
        for (int s = -1; s <= 1; s += 2) {
            double bx = fx * Math.cos(2.5) - fz * Math.sin(2.5) * s;
            double bz = fz * Math.cos(2.5) + fx * Math.sin(2.5) * s;
            for (int k = 1; k <= 3; k++) {
                int ax = cx + (int) Math.round(fx * 6 + bx * k);
                int az = cy + (int) Math.round(fz * 6 + bz * k);
                Ui.fill(g, ax - 1, az - 1, ax + 1, az + 1, 0xFFFFFFFF);
            }
        }
        Ui.fill(g, cx - 2, cy - 2, cx + 2, cy + 2, 0xFF000000);
        Ui.fill(g, cx - 1, cy - 1, cx + 1, cy + 1, Theme.accent());
        // Norden
        Ui.text(g, mc.font, "N", x0 + size / 2 - mc.font.width("N") / 2, y0 + 2, 0xFFFFFFFF, true);
        if (shownCave) Ui.text(g, mc.font, "Höhle", x0 + 3, y0 + size - 10, 0xB0FFFFFF, true);
        if (mapCoords) {
            String s = p.getBlockX() + "  " + p.getBlockY() + "  " + p.getBlockZ();
            int tw = mc.font.width(s);
            Ui.text(g, mc.font, s, x0 + size / 2 - tw / 2, y0 + size + 4, 0xFFFFFFFF, true);
        }
    }

    // ---------- Platz in den Ecken (Minimap, Tasten-Anzeige, Info-Anzeige stapeln sich) ----------

    /** Oben rechts: unter den Trank-Symbolen anfangen. */
    static int baseTop(Minecraft mc, int corner) {
        return corner == 1 && mc.player != null ? effectRows(mc.player) * 26 : 0;
    }

    private static int mapBlockH() {
        return shownN * 2 + 4 + (mapCoords ? 11 : 0) + 4;
    }

    private static int keysBlockH() {
        float scale = new float[] {0.75f, 1f, 1.3f}[Math.max(0, Math.min(2, keysSize))];
        int h = 2 * 20 + 2 + (keysMouse ? 22 : 0) + (keysSpace ? 12 : 0);
        return Math.round(h * scale) + 4;
    }

    /** So weit muss die Info-Anzeige in ihrer Ecke ausweichen (0 = gar nicht). */
    static int infoOffset(Minecraft mc, int corner) {
        int h = 0;
        if (minimap && shownN > 0 && mapCorner == corner) h += mapBlockH();
        if (keys && keysCorner == corner) h += keysBlockH();
        if (h > 0 && corner < 2) h += baseTop(mc, corner);
        return h;
    }

    /** Wie viele Reihen Trank-Symbole oben rechts stehen (gute und schlechte getrennt). */
    private static int effectRows(LocalPlayer p) {
        boolean good = false;
        boolean bad = false;
        for (var e : p.getActiveEffects()) {
            if (!e.isVisible()) continue;
            if (e.getEffect().value().isBeneficial()) good = true;
            else bad = true;
        }
        return (good ? 1 : 0) + (bad ? 1 : 0);
    }

    // =====================================================================================
    // Tasten-Anzeige
    // =====================================================================================

    private static final ArrayDeque<Long> leftClicks = new ArrayDeque<>();
    private static final ArrayDeque<Long> rightClicks = new ArrayDeque<>();
    private static boolean leftWas;
    private static boolean rightWas;
    private static final float[] glow = new float[7];
    private static long keysLast;

    private static int cps(ArrayDeque<Long> q, long now) {
        while (!q.isEmpty() && now - q.peekFirst() > 1000L) q.pollFirst();
        return q.size();
    }

    private static void drawKeys(GuiGraphicsExtractor g, Minecraft mc) {
        var o = mc.options;
        long now = System.currentTimeMillis();
        boolean left = o.keyAttack.isDown();
        boolean right = o.keyUse.isDown();
        if (left && !leftWas) leftClicks.addLast(now);
        if (right && !rightWas) rightClicks.addLast(now);
        leftWas = left;
        rightWas = right;
        int lc = cps(leftClicks, now);
        int rc = cps(rightClicks, now);
        float dt = keysLast == 0 ? 0f : Math.min(0.2f, (now - keysLast) / 1000f);
        keysLast = now;

        boolean[] down = {o.keyUp.isDown(), o.keyLeft.isDown(), o.keyDown.isDown(), o.keyRight.isDown(), left, right,
            o.keyJump.isDown()};
        for (int i = 0; i < down.length; i++) {
            float target = down[i] ? 1f : 0f;
            glow[i] += (target - glow[i]) * Math.min(1f, dt * 18f);
        }
        float scale = new float[] {0.75f, 1f, 1.3f}[Math.max(0, Math.min(2, keysSize))];
        int u = 20;
        int gap = 2;
        int w = 3 * u + 2 * gap;
        int h = 2 * u + gap + (keysMouse ? u + gap : 0) + (keysSpace ? 10 + gap : 0);
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        boolean rightSide = keysCorner == 1 || keysCorner == 3;
        boolean bottom = keysCorner >= 2;
        int below = minimap && shownN > 0 && mapCorner == keysCorner ? mapBlockH() : 0; // Minimap in derselben Ecke
        float px = rightSide ? sw - 4 - w * scale : 4;
        float py = bottom ? sh - 4 - h * scale - below : 4 + baseTop(mc, keysCorner) + below;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(px, py);
        pose.scale(scale, scale);
        int y = 0;
        key(g, mc, label(o.keyUp), u + gap, y, u, u, glow[0], null);
        y += u + gap;
        key(g, mc, label(o.keyLeft), 0, y, u, u, glow[1], null);
        key(g, mc, label(o.keyDown), u + gap, y, u, u, glow[2], null);
        key(g, mc, label(o.keyRight), 2 * (u + gap), y, u, u, glow[3], null);
        y += u + gap;
        if (keysMouse) {
            int half = (w - gap) / 2;
            key(g, mc, "LMB", 0, y, half, u, glow[4], keysCps ? lc + " CPS" : null);
            key(g, mc, "RMB", half + gap, y, w - half - gap, u, glow[5], keysCps ? rc + " CPS" : null);
            y += u + gap;
        }
        if (keysSpace) {
            key(g, mc, null, 0, y, w, 10, glow[6], null);
            int bw = w / 3;
            Ui.fill(g, w / 2 - bw / 2, y + 4, w / 2 + bw / 2, y + 6, Ui.lerp(0xFFFFFFFF, 0xFF000000, glow[6] * 0.7f));
        }
        pose.popMatrix();
    }

    private static String label(KeyMapping k) {
        String s = k.getTranslatedKeyMessage().getString();
        return s.length() > 3 ? s.substring(0, 3) : s;
    }

    private static void key(GuiGraphicsExtractor g, Minecraft mc, String text, int x, int y, int w, int h, float on,
            String sub) {
        int bg = Ui.lerp(0x90000000, (Theme.accent() & 0x00FFFFFF) | 0xE0000000, on);
        Ui.fill(g, x, y, x + w, y + h, bg);
        Ui.fill(g, x, y + h - 1, x + w, y + h, Ui.lerp(0x40FFFFFF, 0xC0FFFFFF, on));
        if (text == null) return;
        int tc = Ui.lerp(0xFFFFFFFF, 0xFF101010, on * 0.85f);
        if (sub == null) {
            // Tastennamen nicht uebersetzen (g.text direkt)
            g.text(mc.font, text, x + w / 2 - mc.font.width(text) / 2, y + h / 2 - 4, tc, false);
        } else {
            var pose = g.pose();
            g.text(mc.font, text, x + w / 2 - mc.font.width(text) / 2, y + 2, tc, false);
            pose.pushMatrix();
            pose.translate(x + w / 2f, y + 12f);
            pose.scale(0.75f, 0.75f);
            g.text(mc.font, sub, -mc.font.width(sub) / 2, 0, Ui.lerp(0xFFD0D0D0, 0xFF202020, on * 0.85f), false);
            pose.popMatrix();
        }
    }

    // =====================================================================================
    // Haltbarkeits-Warnung
    // =====================================================================================

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
        EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int[] warnedLevel = new int[SLOTS.length];   // 0 = nichts, 1 = gewarnt, 2 = fast kaputt
    private static final String[] warnedItem = new String[SLOTS.length];
    private static int durTimer;
    private static String bigText;
    private static long bigAt;

    private static void durabilityTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (!durability || p == null) return;
        if (--durTimer > 0) return;
        durTimer = 5;
        for (int s = 0; s < SLOTS.length; s++) {
            ItemStack st = p.getItemBySlot(SLOTS[s]);
            if (st.isEmpty() || !st.isDamageableItem() || st.getMaxDamage() <= 0) {
                warnedLevel[s] = 0;
                warnedItem[s] = null;
                continue;
            }
            String id = st.getItem().toString() + "|" + st.getMaxDamage();
            if (!id.equals(warnedItem[s])) {
                warnedItem[s] = id;
                warnedLevel[s] = 0;
            }
            int max = st.getMaxDamage();
            int left = max - st.getDamageValue();
            int pct = (int) Math.floor(left * 100.0 / max);
            boolean critical = left <= Math.max(3, max / 100);
            int lvl = critical ? 2 : pct <= durPercent ? 1 : 0;
            if (lvl == 0) {
                warnedLevel[s] = 0; // repariert oder noch gut
                continue;
            }
            if (lvl <= warnedLevel[s]) continue;
            warnedLevel[s] = lvl;
            warn(mc, p, st, left, pct, critical);
        }
    }

    private static void warn(Minecraft mc, LocalPlayer p, ItemStack st, int left, int pct, boolean critical) {
        String name = st.getHoverName().getString();
        String msg = critical
                ? name + Lang.t(" geht gleich kaputt: noch ") + left + Lang.t(" Haltbarkeit!")
                : name + Lang.t(": noch ") + left + Lang.t(" Haltbarkeit (") + pct + " %)";
        p.sendSystemMessage(Lang.literal((critical ? "§c" : "§e") + "1912: " + msg));
        if (durSound) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, critical ? 0.5f : 0.8f));
        }
        bigText = (critical ? "§c" : "§e") + msg;
        bigAt = System.currentTimeMillis();
    }

    private static void drawDurability(GuiGraphicsExtractor g, Minecraft mc) {
        if (bigText == null) return;
        long age = System.currentTimeMillis() - bigAt;
        if (age > 2500) {
            bigText = null;
            return;
        }
        float a = age < 150 ? age / 150f : age > 2000 ? 1f - (age - 2000) / 500f : 1f;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(sw / 2f, sh / 2f - 40);
        pose.scale(1.4f, 1.4f);
        int tw = mc.font.width(bigText);
        int alpha = Math.max(4, Math.round(255 * a));
        g.text(mc.font, bigText, -tw / 2, 0, (alpha << 24) | 0xFFFFFF, true);
        pose.popMatrix();
    }

    // =====================================================================================
    // Menues
    // =====================================================================================

    static Screen screen(String module, Screen parent) {
        return switch (module) {
            case "Zoom" -> new ModuleScreen(parent, module, f -> {
                f.description("Zoomt heran, solange du die Zoom-Taste hältst (Standard: C, änderbar in den "
                        + "Minecraft-Steuerungen unter „1912 Utilities“).");
                f.slider("Stärke (fach)", 2, 10, () -> zoomStrength, v -> zoomStrength = v, 4);
                f.toggle("Mit dem Mausrad stärker / schwächer", () -> zoomScroll, v -> zoomScroll = v, true);
                f.toggle("Weich zoomen", () -> zoomSmooth, v -> zoomSmooth = v, true);
                f.toggle("Maus beim Zoomen langsamer", () -> zoomSlowMouse, v -> zoomSlowMouse = v, true);
                f.toggle("Kamera beim Zoomen glätten", () -> zoomCinematic, v -> zoomCinematic = v, false);
                f.note("Das Mausrad wechselt beim Zoomen nicht den Hotbar-Platz. Beim Loslassen ist die Stärke "
                        + "wieder wie eingestellt.");
                f.footer(() -> zoom, v -> zoom = v);
            });
            case "Minimap" -> new ModuleScreen(parent, module, f -> {
                f.description("Kleine Karte in einer Ecke. Norden ist oben, der Pfeil zeigt deine Blickrichtung.");
                f.choice("Größe", SIZES, () -> mapSize, v -> mapSize = v, 1);
                f.choice("Ausschnitt", MAP_ZOOMS, () -> mapZoom, v -> mapZoom = v, 1);
                f.choice("Ecke", InfoHud.CORNERS, () -> mapCorner, v -> mapCorner = v, 1);
                f.toggle("Koordinaten darunter", () -> mapCoords, v -> mapCoords = v, true);
                f.toggle("Wegpunkte zeigen", () -> mapWaypoints, v -> mapWaypoints = v, true);
                f.toggle("Andere Spieler zeigen", () -> mapPlayers, v -> mapPlayers = v, false);
                f.toggle("Unter der Erde Höhlen zeigen", () -> mapCaves, v -> mapCaves = v, true);
                f.note("Unter der Erde und im Nether zeigt sie die Höhle auf deiner Höhe statt der Oberfläche. "
                        + "Oben rechts rutscht sie unter die Trank-Symbole.");
                f.footer(() -> minimap, v -> minimap = v);
            });
            case "Tasten-Anzeige" -> new ModuleScreen(parent, module, f -> {
                f.description("Zeigt, welche Tasten du gerade drückst, und deine Klicks pro Sekunde.");
                f.choice("Ecke", InfoHud.CORNERS, () -> keysCorner, v -> keysCorner = v, 3);
                f.choice("Größe", SIZES, () -> keysSize, v -> keysSize = v, 1);
                f.toggle("Maustasten", () -> keysMouse, v -> keysMouse = v, true);
                f.toggle("Klicks pro Sekunde (CPS)", () -> keysCps, v -> keysCps = v, true);
                f.toggle("Leertaste", () -> keysSpace, v -> keysSpace = v, true);
                f.footer(() -> keys, v -> keys = v);
            });
            case "Haltbarkeit" -> new ModuleScreen(parent, module, f -> {
                f.description("Warnt, bevor Werkzeug, Waffe oder Rüstung kaputtgeht (auch die Elytra).");
                f.slider("Warnen bei (% Haltbarkeit)", 1, 50, () -> durPercent, v -> durPercent = v, 10);
                f.toggle("Ton abspielen", () -> durSound, v -> durSound = v, true);
                f.toggle("Groß auf dem Bildschirm", () -> durScreen, v -> durScreen = v, true);
                f.note("Kurz vor dem Zerbrechen warnt er ein zweites Mal. Nach dem Reparieren ist alles wieder "
                        + "zurückgesetzt.");
                f.footer(() -> durability, v -> durability = v);
            });
            case "Boat Fly" -> new ModuleScreen(parent, module, f -> {
                f.description("Mit dem Boot fliegen: Springen = hoch, Sprinten (Strg) = runter. Das Boot fährt "
                        + "in deine Blickrichtung.");
                f.slider("Tempo (Blöcke/s)", 2, 60, () -> boatSpeed, v -> boatSpeed = v, 20);
                f.slider("Hoch/Runter (Blöcke/s)", 2, 30, () -> boatVertical, v -> boatVertical = v, 8);
                f.toggle("Boot dreht sich mit deinem Blick", () -> boatFollowLook, v -> boatFollowLook = v, true);
                f.toggle("Anti-Kick", () -> boatAntiKick, v -> boatAntiKick = v, true);
                f.note("Anti-Kick lässt das Boot alle zwei Sekunden kurz sinken - sonst werfen viele Server dich "
                        + "wegen Fliegens raus.");
                f.footer(() -> boatFly, v -> boatFly = v);
            });
            case "Fadenkreuz" -> new ModuleScreen(parent, module, f -> {
                f.description("Ein eigenes Fadenkreuz statt dem von Minecraft.");
                f.choice("Form", CH_SHAPES, () -> chShape, v -> chShape = v, 1);
                f.choice("Farbe", CH_COLORS, () -> chColor, v -> chColor = v, 0);
                f.slider("Länge", 1, 12, () -> chSize, v -> chSize = v, 5);
                f.slider("Dicke", 1, 4, () -> chThick, v -> chThick = v, 1);
                f.slider("Lücke in der Mitte", 0, 8, () -> chGap, v -> chGap = v, 2);
                f.toggle("Dunkler Rand", () -> chOutline, v -> chOutline = v, true);
                f.toggle("Rot, wenn du auf einen Gegner zielst", () -> chTarget, v -> chTarget = v, true);
                f.toggle("Abklingzeit darunter anzeigen", () -> chAttack, v -> chAttack = v, true);
                f.toggle("Auch in der Ansicht von außen (F5)", () -> chThird, v -> chThird = v, false);
                f.footer(() -> crosshair, v -> crosshair = v);
            });
            default -> null;
        };
    }

    // =====================================================================================
    // Boat Fly
    // =====================================================================================

    private static int boatTicks;

    /** Vom AbstractBoatMixin, direkt bevor das Boot sich bewegt (nur fuer das eigene Boot). */
    public static void boatTick(Entity boat) {
        if (!boatFly) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || boat.getControllingPassenger() != p) return;
        boolean canMove = mc.gui.screen() == null;
        boolean fwd = canMove && mc.options.keyUp.isDown();
        boolean back = canMove && mc.options.keyDown.isDown();
        boolean left = canMove && mc.options.keyLeft.isDown();
        boolean right = canMove && mc.options.keyRight.isDown();
        boolean up = canMove && mc.options.keyJump.isDown();
        boolean down = canMove && mc.options.keySprint.isDown();
        if (boatFollowLook) boat.setYRot(p.getYRot());
        double[] dir = Look.moveDir(p, fwd, back, left, right);
        double h = boatSpeed / 20.0;
        double v = boatVertical / 20.0;
        double vy = up ? v : down ? -v : 0;
        boatTicks++;
        if (boatAntiKick && boatTicks % 40 == 0 && !up) vy = -0.04;
        boat.setDeltaMovement(dir == null ? 0 : dir[0] * h, vy, dir == null ? 0 : dir[1] * h);
        boat.fallDistance = 0;
    }

    // =====================================================================================
    // Fadenkreuz
    // =====================================================================================

    /** Vom HudMixin statt des normalen Fadenkreuzes. false = aus, dann zeichnet Minecraft seins. */
    public static boolean drawCrosshair(GuiGraphicsExtractor g) {
        if (!crosshair) return false;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || p.isSpectator()) return true;
        if (!mc.options.getCameraType().isFirstPerson() && !chThird) return true;
        int col = chColor >= 0 && chColor < CH_RGB.length && CH_RGB[chColor] != 0 ? CH_RGB[chColor]
                : 0xFF000000 | Theme.accent();
        if (chTarget && mc.crosshairPickEntity instanceof LivingEntity le && le.isAlive()) {
            col = col == 0xFFFF4D4D ? 0xFFFFE04D : 0xFFFF4D4D; // rot (schon rot? dann gelb)
        }
        float old = Ui.alpha;
        Ui.alpha = 1f;
        try {
            int t = Math.max(1, chThick);
            int s = Math.max(1, chSize);
            int gap = Math.max(0, chGap);
            int ax = g.guiWidth() / 2 - (t - 1) / 2;
            int ay = g.guiHeight() / 2 - (t - 1) / 2;
            int bx = ax + t;
            int by = ay + t;
            switch (chShape) {
                case 2 -> { // Punkt
                    int d = Math.max(2, t + 1);
                    int x0 = g.guiWidth() / 2 - d / 2;
                    int y0 = g.guiHeight() / 2 - d / 2;
                    chRect(g, x0, y0, x0 + d, y0 + d, col);
                }
                case 3 -> { // Kreis
                    double r = gap + s / 2.0 + 1;
                    int steps = (int) Math.ceil(2 * Math.PI * r * 1.5);
                    int cx = g.guiWidth() / 2;
                    int cy = g.guiHeight() / 2;
                    java.util.Set<Long> done = new java.util.HashSet<>();
                    for (int i = 0; i < steps; i++) {
                        double a = 2 * Math.PI * i / steps;
                        int x = (int) Math.round(cx + Math.cos(a) * r - (t - 1) / 2.0);
                        int y = (int) Math.round(cy + Math.sin(a) * r - (t - 1) / 2.0);
                        if (done.add(((long) x << 32) ^ (y & 0xFFFFFFFFL))) chRect(g, x, y, x + t, y + t, col);
                    }
                    chRect(g, ax, ay, bx, by, col);
                }
                default -> { // Kreuz, Kreuz mit Punkt, T-Form
                    chRect(g, ax - gap - s, ay, ax - gap, by, col);
                    chRect(g, bx + gap, ay, bx + gap + s, by, col);
                    if (chShape != 4) chRect(g, ax, ay - gap - s, bx, ay - gap, col);
                    chRect(g, ax, by + gap, bx, by + gap + s, col);
                    if (chShape == 1 && gap > 0) chRect(g, ax, ay, bx, by, col);
                }
            }
            if (chAttack) {
                float strength = p.getAttackStrengthScale(0f);
                if (strength < 1f) {
                    int w = 16;
                    int x0 = g.guiWidth() / 2 - w / 2;
                    int y0 = g.guiHeight() / 2 + (chShape == 2 ? 4 : gap + s + 4);
                    Ui.fill(g, x0, y0, x0 + w, y0 + 2, 0x80000000);
                    Ui.fill(g, x0, y0, x0 + Math.round(w * strength), y0 + 2, col);
                }
            }
        } finally {
            Ui.alpha = old;
        }
        return true;
    }

    private static void chRect(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int col) {
        if (x2 <= x1 || y2 <= y1) return;
        if (chOutline) Ui.fill(g, x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xA0000000);
        Ui.fill(g, x1, y1, x2, y2, col);
    }
}
