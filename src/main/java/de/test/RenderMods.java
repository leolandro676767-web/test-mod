package de.test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Render-Module (Kategorie "Render"). ESP, Tracer, Nametags usw. werden ueber {@link Render3D}
 * selbst aufs HUD projiziert. Module, die tief in die Grafik eingreifen (Chams, Wall Hack,
 * Freecam, Blur, Item Physics, Hand View) setzen nur statische Schalter, die Mixins lesen -
 * ohne Mixin-Treffer in 26.2 bleiben sie wirkungslos.
 */
public final class RenderMods {
    private RenderMods() {
    }

    // ---------- Schalter + Einstellungen ----------

    public static boolean blockEsp = false;
    public static int blockEspRadius = 32;
    public static final Set<Block> blockEspBlocks = new LinkedHashSet<>();

    public static boolean storageEsp = false;
    public static int storageEspRadius = 48;

    public static boolean holeEsp = false;
    public static int holeEspRadius = 16;

    public static boolean voidEsp = false;
    public static int voidEspRadius = 10;

    public static boolean cityEsp = false;
    public static int cityEspRange = 6;

    public static boolean tunnelEsp = false;
    public static int tunnelEspRadius = 16;

    public static boolean tracers = false;
    public static boolean tracersPlayers = true;
    public static boolean tracersMobs = false;
    public static boolean tracersItems = false;
    public static int tracersRange = 96;

    public static boolean nametags = false;
    public static boolean nametagsHealth = true;
    public static boolean nametagsItems = false;    // Items in Haenden/Ruestung (nur Zahlen hier vereinfacht)
    public static int nametagsRange = 64;

    public static boolean entityOwner = false;
    public static boolean ownerAnimals = true;
    public static boolean ownerProjectiles = true;
    public static int ownerRange = 32;

    public static boolean itemHighlight = false;
    public static int itemHighlightRange = 48;

    public static boolean marker = false;
    public static String markerList = "";           // "name,x,y,z;..."

    public static boolean waypoints = false;
    public static String waypointList = "";
    public static boolean waypointsBeam = true;

    public static boolean logoutSpots = false;
    public static boolean logoutBox = true;
    public static boolean logoutDistance = true;

    public static boolean trail = false;
    public static int trailLength = 80;

    public static boolean breadcrumbs = false;
    public static int breadcrumbsLength = 200;

    public static boolean trajectories = false;
    public static int trajSteps = 80;
    public static boolean trajLanding = true;

    public static boolean lightOverlay = false;
    public static int lightOverlayRadius = 12;

    public static boolean breakIndicators = false;
    public static boolean breakPercent = true;
    public static boolean breakOthers = true;

    // Reine Schalter (von Tick oder Mixin gelesen)
    public static boolean fullbright = false;
    public static boolean timeChanger = false;
    public static int timeChangerValue = 6000;      // 0=Morgen, 6000=Mittag, 18000=Mitternacht
    public static boolean cameraTweaks = false;
    public static int cameraDistance = 4;
    public static boolean cameraNoClip = true;
    public static boolean freeLook = false;
    public static boolean freecam = false;
    public static int freecamSpeed = 10;
    private static boolean fullbrightApplied = false;
    public static int fullbrightMode = 0;           // 0 = Nachtsicht, 1 = Gamma
    public static final String[] FULLBRIGHT_MODES = {"Nachtsicht", "Gamma"};
    public static int fullbrightGamma = 1000;       // Prozent
    private static double savedGamma = -1;
    private static java.lang.reflect.Field gammaValueField;
    private static boolean gammaLookupDone = false;

    public static boolean tipShulker = true;
    public static int tipShulkerLines = 5;
    public static boolean tipDurability = true;
    public static boolean tipFood = true;

    public static boolean noRender = false;
    public static boolean noFire = true;            // Feuer-Overlay (eigene Sicht)
    public static boolean noWaterOverlay = true;
    public static boolean noNausea = true;
    public static boolean noPumpkin = true;
    public static boolean noFog = false;
    public static boolean noHurtCam = false;

    public static boolean blockSelection = false;
    public static int blockSelMode = 0;             // 0=Aus(normal), 1=Farbig, 2=Versteckt
    public static final String[] BLOCK_SEL_MODES = {"Normal", "Farbig", "Versteckt"};

    public static boolean handView = false;
    public static boolean chams = false;
    public static boolean wallHack = false;
    public static boolean itemPhysics = false;
    public static boolean blur = false;
    public static int blurStrength = 100;       // Abdunkelung (0..200)
    public static boolean bossStack = false;
    public static boolean betterTab = false;
    public static boolean betterTooltips = false;
    public static boolean popChams = false;
    public static int popDuration = 3;          // Sekunden
    public static boolean popChat = true;
    public static boolean popSelf = false;

    private static final int C_BLOCK = 0xFF5BFF7A;
    private static final int C_STORAGE = 0xFFFFC24D;
    private static final int C_HOLE = 0xFF4DD2FF;
    private static final int C_VOID = 0xFFFF4D4D;
    private static final int C_CITY = 0xFFFF6EC7;
    private static final int C_PLAYER = 0xFFFF4D4D;
    private static final int C_MOB = 0xFFFFB13B;
    private static final int C_ITEM = 0xFF4DD2FF;
    private static final int C_MARK = 0xFFB794F6;
    private static final int C_TRAIL = 0xFF8CF0B4;

    private static final int MAX_DRAW = 400;
    private static final int SCAN_INTERVAL = 10;

    // Gesammelte Positionen (im Tick gefuellt)
    private static final List<BlockPos> espBlocks = new ArrayList<>();
    private static final List<BlockPos> storages = new ArrayList<>();
    private static final List<BlockPos> holes = new ArrayList<>();
    private static final List<BlockPos> voids = new ArrayList<>();
    private static final List<BlockPos> lights = new ArrayList<>();
    private static final List<BlockPos> tunnels = new ArrayList<>();

    private record Pop(String name, AABB box, long time) {
    }

    private static final List<Pop> pops = new ArrayList<>();
    private static final Map<String, Integer> popCount = new HashMap<>();
    private static int scanCooldown = 0;

    private static final Deque<Vec3> trailPts = new ArrayDeque<>();
    private static final Map<UUID, Vec3> lastSeen = new HashMap<>();
    private static final Map<UUID, LogoutSpot> logouts = new HashMap<>();

    private record LogoutSpot(String name, Vec3 pos) {
    }

    public static void register() {
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("test", "render"), RenderMods::render);
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (betterTooltips) BetterTooltips.append(stack, lines);
        });
    }

    public static void rescan() {
        scanCooldown = 0;
    }

    // ---------- Tick: scannen, verfolgen, einfache Schalter ----------

    public static void tick(Minecraft mc) {
        Player self = mc.player;
        Level level = mc.level;
        if (self == null || level == null) {
            trailPts.clear();
            lastSeen.clear();
            logouts.clear();
            return;
        }

        boolean nightVision = fullbright && fullbrightMode == 0;
        boolean gamma = fullbright && fullbrightMode == 1;
        if (nightVision) {
            applyFullbright(self);
        } else if (fullbrightApplied) {
            self.removeEffect(MobEffects.NIGHT_VISION);
            fullbrightApplied = false;
        }
        if (gamma) setGamma(mc, fullbrightGamma / 100.0);
        else restoreGamma(mc);
        if (timeChanger) level.setDayTime(timeChangerValue);
        if (noRender && noNausea) self.removeEffect(MobEffects.NAUSEA);

        // Trail / Breadcrumbs
        if (trail || breadcrumbs) {
            Vec3 p = self.position();
            if (trailPts.isEmpty() || trailPts.peekLast().distanceToSqr(p) > 0.04) {
                trailPts.addLast(p);
                int max = Math.max(trail ? trailLength : 0, breadcrumbs ? breadcrumbsLength : 0);
                while (trailPts.size() > max) trailPts.pollFirst();
            }
        } else if (!trailPts.isEmpty()) {
            trailPts.clear();
        }

        // Logout Spots: zuletzt gesehene Spieler-Positionen, Verschwundene merken
        if (logoutSpots) trackLogouts(mc, self);

        // Block-Scans gebuendelt
        if (--scanCooldown <= 0) {
            scanCooldown = SCAN_INTERVAL;
            runScans(mc, self);
        }
    }

    /** Gamma ueber den gespeicherten Wert der Option setzen (die Option selbst erlaubt nur 0..100 %). */
    private static void setGamma(Minecraft mc, double value) {
        var opt = mc.options.gamma();
        if (!gammaLookupDone) {
            gammaLookupDone = true;
            try {
                gammaValueField = opt.getClass().getDeclaredField("value");
                gammaValueField.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                System.err.println("[1912] Gamma-Feld nicht gefunden: " + e);
            }
        }
        if (gammaValueField == null) return;
        try {
            if (savedGamma < 0) savedGamma = opt.get();
            gammaValueField.set(opt, value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // nicht setzbar
        }
    }

    private static void restoreGamma(Minecraft mc) {
        if (savedGamma < 0 || gammaValueField == null) return;
        try {
            gammaValueField.set(mc.options.gamma(), savedGamma);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // nicht setzbar
        }
        savedGamma = -1;
    }

    private static void applyFullbright(Player self) {
        MobEffectInstance eff = self.getEffect(MobEffects.NIGHT_VISION);
        if (eff == null || eff.getDuration() < 400) {
            self.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 100000, 0, false, false, false));
        }
        fullbrightApplied = true;
    }

    private static void trackLogouts(Minecraft mc, Player self) {
        Set<UUID> present = new LinkedHashSet<>();
        for (Player p : mc.level.players()) {
            if (p == self) continue;
            present.add(p.getUUID());
            lastSeen.put(p.getUUID(), p.position());
            logouts.remove(p.getUUID()); // wieder da
        }
        // Wer eben noch da war und jetzt fehlt, aber noch im Tab steht -> ausgeloggt
        var conn = mc.getConnection();
        for (var e : new ArrayList<>(lastSeen.entrySet())) {
            if (present.contains(e.getKey())) continue;
            boolean inTab = conn != null && conn.getPlayerInfo(e.getKey()) != null;
            if (!inTab && !logouts.containsKey(e.getKey())) {
                String name = conn != null && conn.getPlayerInfo(e.getKey()) != null
                        ? conn.getPlayerInfo(e.getKey()).getProfile().name() : "?";
                logouts.put(e.getKey(), new LogoutSpot(name, e.getValue()));
            }
            lastSeen.remove(e.getKey());
        }
    }

    private static void runScans(Minecraft mc, Player self) {
        espBlocks.clear();
        storages.clear();
        holes.clear();
        voids.clear();
        lights.clear();
        tunnels.clear();
        Level level = mc.level;
        BlockPos c = self.blockPosition();

        if (blockEsp && !blockEspBlocks.isEmpty()) {
            scanBlocks(level, c, blockEspRadius, pos -> blockEspBlocks.contains(level.getBlockState(pos).getBlock()), espBlocks);
        }
        if (storageEsp) {
            int r = storageEspRadius;
            for (int dx = -r; dx <= r; dx += 1) {
                for (int dz = -r; dz <= r; dz += 1) {
                    for (int dy = -8; dy <= 8; dy++) {
                        BlockPos pos = c.offset(dx, dy, dz);
                        if (level.getBlockEntity(pos) != null && isStorage(level.getBlockState(pos))) {
                            storages.add(pos);
                            if (storages.size() >= MAX_DRAW) break;
                        }
                    }
                }
            }
        }
        if (holeEsp) scanHoles(level, c, holeEspRadius);
        if (voidEsp && level.dimensionType().hasCeiling() == false) scanVoid(level, c);
        if (lightOverlay) scanLight(level, c, lightOverlayRadius);
        if (tunnelEsp) scanTunnels(level, c, tunnelEspRadius);
    }

    private interface PosTest {
        boolean ok(BlockPos p);
    }

    private static void scanBlocks(Level level, BlockPos c, int r, PosTest test, List<BlockPos> out) {
        int r2 = r * r;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (test.ok(m)) {
                        out.add(m.immutable());
                        if (out.size() >= MAX_DRAW) return;
                    }
                }
            }
        }
    }

    private static boolean isStorage(BlockState s) {
        Block b = s.getBlock();
        return b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL || b == Blocks.HOPPER
                || b == Blocks.ENDER_CHEST || b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER
                || b == Blocks.DISPENSER || b == Blocks.DROPPER || b == Blocks.BREWING_STAND
                || b.getName().getString().toLowerCase().contains("shulker");
    }

    private static boolean solid(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** Sicheres 1x1-Loch: Boden solide, Kopf- und Fusshoehe frei, ringsum solide. */
    private static void scanHoles(Level level, BlockPos c, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -r; dy <= 4; dy++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    if (solid(level, p) || solid(level, p.above()) || !solid(level, p.below())) continue;
                    if (solid(level, p.north()) && solid(level, p.south()) && solid(level, p.east()) && solid(level, p.west())) {
                        holes.add(p);
                        if (holes.size() >= MAX_DRAW) return;
                    }
                }
            }
        }
    }

    private static void scanVoid(Level level, BlockPos c) {
        int r = voidEspRadius;
        int minY = level.getMinY();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                boolean anySolid = false;
                for (int y = minY; y < minY + 6; y++) {
                    if (solid(level, new BlockPos(c.getX() + dx, y, c.getZ() + dz))) {
                        anySolid = true;
                        break;
                    }
                }
                if (!anySolid) {
                    voids.add(new BlockPos(c.getX() + dx, minY, c.getZ() + dz));
                    if (voids.size() >= MAX_DRAW) return;
                }
            }
        }
    }

    private static void scanLight(Level level, BlockPos c, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    if (!solid(level, p) || solid(level, p.above()) || solid(level, p.above(2))) continue;
                    if (level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p.above()) <= 0) {
                        lights.add(p.above());
                        if (lights.size() >= MAX_DRAW) return;
                    }
                }
            }
        }
    }

    /** 1x2-Gang: Fuss- und Kopfhoehe frei, Boden und Decke fest, links/rechts fest (aber nicht ringsum). */
    private static void scanTunnels(Level level, BlockPos c, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -8; dy <= 8; dy++) {
                    BlockPos p = c.offset(dx, dy, dz);
                    if (solid(level, p) || solid(level, p.above())) continue;
                    if (!solid(level, p.below()) || !solid(level, p.above(2))) continue;
                    boolean ew = solid(level, p.east()) && solid(level, p.west())
                            && solid(level, p.above().east()) && solid(level, p.above().west());
                    boolean ns = solid(level, p.north()) && solid(level, p.south())
                            && solid(level, p.above().north()) && solid(level, p.above().south());
                    if (ew == ns) continue; // keiner = offen, beide = Loch statt Gang
                    tunnels.add(p);
                    if (tunnels.size() >= MAX_DRAW) return;
                }
            }
        }
    }

    /** Vom LivingEntityMixin: jemand hat ein Totem verbraucht (Ereignis 35). */
    public static void onTotemPop(LivingEntity e) {
        if (!popChams) return;
        Minecraft mc = Minecraft.getInstance();
        if (e == mc.player && !popSelf) return;
        String name = e.getName().getString();
        pops.add(new Pop(name, e.getBoundingBox(), System.currentTimeMillis()));
        int n = popCount.merge(name, 1, Integer::sum);
        if (popChat && mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("\u00a7d1912: " + name + " hat ein Totem verbraucht (" + n + ")"));
        }
    }

    private static void drawPops(GuiGraphicsExtractor g) {
        if (pops.isEmpty()) return;
        long now = System.currentTimeMillis();
        long life = popDuration * 1000L;
        pops.removeIf(pp -> now - pp.time() > life);
        float old = Ui.alpha;
        for (Pop pp : pops) {
            float t = (now - pp.time()) / (float) life;
            Ui.alpha = 1f - t;
            // Geist steigt langsam auf und verblasst
            Render3D.box(g, pp.box().move(0, t * 1.5, 0), 0xFFFF6EF5, true);
        }
        Ui.alpha = old;
    }

    // ---------- Break Indicators (Reflection, Namen gelten seit 26.1 zur Laufzeit) ----------

    private static java.lang.reflect.Field fDestroyPos;
    private static java.lang.reflect.Field fDestroyProgress;
    private static java.lang.reflect.Field fIsDestroying;
    private static java.lang.reflect.Field fDestroying;
    private static boolean breakFieldsLoaded = false;

    private static java.lang.reflect.Field field(Class<?> c, String name) {
        try {
            java.lang.reflect.Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.err.println("[1912] Feld nicht gefunden: " + c.getSimpleName() + "." + name);
            return null;
        }
    }

    private static void drawBreaking(GuiGraphicsExtractor g, Minecraft mc) {
        if (!breakFieldsLoaded) {
            breakFieldsLoaded = true;
            Class<?> gm = net.minecraft.client.multiplayer.MultiPlayerGameMode.class;
            fDestroyPos = field(gm, "destroyBlockPos");
            fDestroyProgress = field(gm, "destroyProgress");
            fIsDestroying = field(gm, "isDestroying");
            fDestroying = field(net.minecraft.client.renderer.LevelRenderer.class, "destroyingBlocks");
        }
        // eigener Abbau
        try {
            if (mc.gameMode != null && fIsDestroying != null && fIsDestroying.getBoolean(mc.gameMode)) {
                BlockPos pos = (BlockPos) fDestroyPos.get(mc.gameMode);
                float prog = fDestroyProgress.getFloat(mc.gameMode);
                drawBreakBox(g, mc, pos, prog);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Feld nicht nutzbar
        }
        // Abbau anderer Spieler (Stufen 0..9)
        if (!breakOthers || fDestroying == null) return;
        try {
            Object map = fDestroying.get(mc.levelRenderer);
            if (map instanceof Map<?, ?> m) {
                for (Object v : m.values()) {
                    if (v instanceof net.minecraft.server.level.BlockDestructionProgress bdp) {
                        drawBreakBox(g, mc, bdp.getPos(), (bdp.getProgress() + 1) / 10f);
                    }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Feld nicht nutzbar
        }
    }

    private static void drawBreakBox(GuiGraphicsExtractor g, Minecraft mc, BlockPos pos, float prog) {
        prog = Math.max(0f, Math.min(1f, prog));
        int color = Ui.lerp(0xFFFF4040, 0xFF40FF60, prog);
        double shrink = 0.5 * (1 - prog);
        AABB bb = cube(pos).deflate(shrink);
        Render3D.box(g, bb, color, true);
        if (breakPercent) {
            Render3D.label(g, mc, Vec3.atCenterOf(pos), Math.round(prog * 100) + "%", color, -4);
        }
    }

    // ---------- Render (HUD) ----------

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        // Blur: einfacher Abdunkel-Overlay bei offenem Menue (kein echter Weichzeichner)
        if (blur && mc.gui.screen() != null) {
            int a = Math.max(0, Math.min(200, blurStrength));
            Ui.fill(g, 0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), a << 24);
        }

        if (!Render3D.begin(mc, delta.getGameTimeDeltaPartialTick(true))) return;
        float old = Ui.alpha;
        Ui.alpha = 1f;
        try {
            drawBlockLists(g);
            drawEntities(g, mc);
            drawMarkersAndWaypoints(g, mc);
            drawLogouts(g, mc);
            drawTrail(g);
            if (trajectories) drawTrajectory(g, mc);
            if (tunnelEsp) for (BlockPos p : tunnels) {
                Render3D.box(g, new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 2, p.getZ() + 1),
                        0xFFFFA64D, false);
            }
            if (popChams) drawPops(g);
            if (breakIndicators) drawBreaking(g, mc);
        } finally {
            Ui.alpha = old;
        }
    }

    private static void drawBlockLists(GuiGraphicsExtractor g) {
        if (blockEsp) for (BlockPos p : espBlocks) Render3D.box(g, cube(p), C_BLOCK, false);
        if (storageEsp) for (BlockPos p : storages) Render3D.box(g, cube(p), C_STORAGE, true);
        if (holeEsp) for (BlockPos p : holes) Render3D.box(g, new AABB(p), C_HOLE, false);
        if (voidEsp) for (BlockPos p : voids) Render3D.box(g, new AABB(p).inflate(0, 0.2, 0), C_VOID, true);
        if (lightOverlay) for (BlockPos p : lights) {
            Vec3 ctr = new Vec3(p.getX() + 0.5, p.getY() + 0.02, p.getZ() + 0.5);
            Render3D.label(g, Minecraft.getInstance(), ctr, "x", 0xFFFF3030, 0);
        }
    }

    private static AABB cube(BlockPos p) {
        return new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1);
    }

    private static void drawEntities(GuiGraphicsExtractor g, Minecraft mc) {
        boolean anyEnt = tracers || nametags || entityOwner || itemHighlight || cityEsp;
        if (!anyEnt) return;
        Player self = mc.player;
        double range = Math.max(Math.max(Math.max(tracersRange, nametagsRange), itemHighlightRange), ownerRange);
        AABB area = self.getBoundingBox().inflate(range);
        int drawn = 0;
        for (Entity e : mc.level.getEntities(self, area)) {
            if (drawn >= MAX_DRAW) break;
            if (!e.isAlive()) continue;
            AABB bb = Render3D.smoothBox(e);
            Vec3 center = bb.getCenter();
            double dist = self.getEyePosition().distanceTo(center);

            if (e instanceof Player pl) {
                if (cityEsp) drawCity(g, mc, pl);
                if (tracers && tracersPlayers && dist <= tracersRange) Render3D.tracer(g, center, tracer(C_PLAYER));
                if (nametags && dist <= nametagsRange) drawNametag(g, mc, pl, bb);
            } else if (e instanceof ItemEntity ie) {
                if (itemHighlight && dist <= itemHighlightRange) {
                    Render3D.box(g, bb.inflate(0.1), C_ITEM, false);
                    int n = ie.getItem().getCount();
                    Render3D.label(g, mc, bb.getCenter().add(0, 0.4, 0),
                            ie.getItem().getHoverName().getString() + (n > 1 ? " x" + n : ""), C_ITEM, -10);
                }
                if (tracers && tracersItems && dist <= tracersRange) Render3D.tracer(g, center, tracer(C_ITEM));
            } else if (e instanceof LivingEntity) {
                if (tracers && tracersMobs && dist <= tracersRange) Render3D.tracer(g, center, tracer(C_MOB));
                if (nametags && dist <= nametagsRange) drawNametag(g, mc, (LivingEntity) e, bb);
            }

            if (entityOwner && dist <= ownerRange) drawOwner(g, mc, e, bb);
            drawn++;
        }
    }

    private static int tracer(int color) {
        return (color & 0x00FFFFFF) | 0xB0000000;
    }

    private static void drawNametag(GuiGraphicsExtractor g, Minecraft mc, LivingEntity e, AABB bb) {
        String name = e.getName().getString();
        if (nametagsHealth) name += "  " + (int) Math.ceil(e.getHealth()) + "❤";
        Render3D.label(g, mc, new Vec3(bb.getCenter().x, bb.maxY, bb.getCenter().z), name, 0xFFFFFFFF, -12);
    }

    private static void drawOwner(GuiGraphicsExtractor g, Minecraft mc, Entity e, AABB bb) {
        String owner = null;
        if (ownerAnimals && e instanceof TamableAnimal ta && ta.getOwner() != null) {
            owner = ta.getOwner().getName().getString();
        } else if (ownerProjectiles && e instanceof Projectile pr && pr.getOwner() != null) {
            owner = pr.getOwner().getName().getString();
        }
        if (owner != null) {
            Render3D.label(g, mc, new Vec3(bb.getCenter().x, bb.maxY, bb.getCenter().z), owner, 0xFF8CF0B4, -2);
        }
    }

    /** City Esp: Bloecke rund um einen Gegner hervorheben (Surround-Bypass-Hilfe). */
    private static void drawCity(GuiGraphicsExtractor g, Minecraft mc, Player enemy) {
        if (enemy == mc.player) return;
        if (enemy.distanceTo(mc.player) > cityEspRange * 2) return;
        BlockPos base = enemy.blockPosition();
        for (BlockPos p : new BlockPos[] {base.below(), base.north(), base.south(), base.east(), base.west()}) {
            if (solid(mc.level, p) && mc.level.getBlockState(p).getBlock() != Blocks.BEDROCK) {
                Render3D.box(g, cube(p), C_CITY, false);
            }
        }
    }

    private static void drawMarkersAndWaypoints(GuiGraphicsExtractor g, Minecraft mc) {
        if (marker) drawPoints(g, mc, markerList, C_MARK, false);
        if (waypoints) drawPoints(g, mc, waypointList, 0xFFFFD24D, waypointsBeam);
    }

    private static void drawPoints(GuiGraphicsExtractor g, Minecraft mc, String list, int color, boolean beam) {
        if (list == null || list.isEmpty()) return;
        Vec3 eye = mc.player.getEyePosition();
        for (String part : list.split(";")) {
            String[] f = part.split(",");
            if (f.length < 4) continue;
            try {
                double x = Double.parseDouble(f[1]) + 0.5;
                double y = Double.parseDouble(f[2]);
                double z = Double.parseDouble(f[3]) + 0.5;
                Vec3 at = new Vec3(x, y + 0.5, z);
                if (beam) Render3D.worldLine(g, new Vec3(x, y, z), new Vec3(x, y + 3, z), color);
                Render3D.dot(g, at, 2, color);
                int dist = (int) eye.distanceTo(new Vec3(x, y, z));
                Render3D.label(g, mc, at, f[0] + "  " + dist + "m", color, -12);
            } catch (NumberFormatException ignored) {
                // kaputter Eintrag
            }
        }
    }

    private static void drawLogouts(GuiGraphicsExtractor g, Minecraft mc) {
        if (!logoutSpots) return;
        Vec3 eye = mc.player.getEyePosition();
        for (LogoutSpot s : logouts.values()) {
            if (logoutBox) {
                Render3D.box(g, new AABB(s.pos().x - 0.3, s.pos().y, s.pos().z - 0.3,
                        s.pos().x + 0.3, s.pos().y + 1.8, s.pos().z + 0.3), 0xFFFF6EC7, false);
            }
            String txt = s.name() + (logoutDistance ? " (Logout " + (int) eye.distanceTo(s.pos()) + "m)" : " (Logout)");
            Render3D.label(g, mc, s.pos().add(0, 1.9, 0), txt, 0xFFFF6EC7, -12);
        }
    }

    private static void drawTrail(GuiGraphicsExtractor g) {
        if (trailPts.size() < 2) return;
        Vec3 prev = null;
        int i = 0;
        for (Vec3 p : trailPts) {
            if (trail && prev != null) Render3D.worldLine(g, prev, p, C_TRAIL);
            if (breadcrumbs && (i++ % 4 == 0)) Render3D.dot(g, p, 1, 0xFFB794F6);
            prev = p;
        }
    }

    /** Flugbahn einer geworfenen Sache (grob: Schwerkraft wie bei Enderperlen/Schneebaellen). */
    private static void drawTrajectory(GuiGraphicsExtractor g, Minecraft mc) {
        ItemStack hand = mc.player.getMainHandItem();
        double speed;
        double gravity;
        if (hand.is(Items.BOW) || hand.is(Items.CROSSBOW) || hand.is(Items.TRIDENT)) {
            speed = 2.5;
            gravity = 0.05;
        } else if (hand.is(Items.ENDER_PEARL) || hand.is(Items.SNOWBALL) || hand.is(Items.EGG)
                || hand.is(Items.SPLASH_POTION) || hand.is(Items.LINGERING_POTION)) {
            speed = 1.5;
            gravity = 0.03;
        } else {
            return;
        }
        Vec3 pos = mc.player.getEyePosition();
        Vec3 look = mc.player.getLookAngle();
        Vec3 vel = look.scale(speed);
        for (int i = 0; i < trajSteps; i++) {
            Vec3 next = pos.add(vel);
            if (solid(mc.level, BlockPos.containing(next))) {
                if (trajLanding) Render3D.dot(g, next, 2, 0xFFFF3030);
                break;
            }
            if (i % 2 == 0) Render3D.dot(g, next, 1, 0xFF4DD2FF);
            pos = next;
            vel = vel.scale(0.99).subtract(0, gravity, 0);
        }
    }

    // ---------- Markierungen hinzufuegen (aus den Menues) ----------

    static void addPoint(boolean waypoint, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        BlockPos p = mc.player.blockPosition();
        String entry = name.replaceAll("[,;]", " ") + "," + p.getX() + "," + p.getY() + "," + p.getZ();
        if (waypoint) {
            waypointList = waypointList.isEmpty() ? entry : waypointList + ";" + entry;
        } else {
            markerList = markerList.isEmpty() ? entry : markerList + ";" + entry;
        }
        TestConfig.save();
    }

    static void clearPoints(boolean waypoint) {
        if (waypoint) waypointList = "";
        else markerList = "";
        TestConfig.save();
    }

    static void clearLogouts() {
        logouts.clear();
        lastSeen.clear();
    }

    static int logoutCount() {
        return logouts.size();
    }

    static int pointCount(String list) {
        return list == null || list.isEmpty() ? 0 : list.split(";").length;
    }

    // ---------- Einstellungs-Menues ----------

    public static Screen screen(String module, Screen parent) {
        return switch (module) {
            case "Block Esp" -> new ModuleScreen(parent, module, f -> {
                f.description("Hebt gewählte Blöcke durch Wände hervor.");
                f.section("Allgemein");
                f.slider("Radius", 8, 64, () -> blockEspRadius, v -> {
                    blockEspRadius = v;
                    rescan();
                }, 32);
                f.wideButton("Blöcke wählen (" + blockEspBlocks.size() + ")", false,
                        () -> Minecraft.getInstance().gui.setScreen(new PickerScreen(f, "Block Esp",
                                PickerScreen.Kind.BLOCK, PickerScreen.ofSet(blockEspBlocks), null, null)));
                f.footer(() -> blockEsp, v -> {
                    blockEsp = v;
                    rescan();
                });
            });
            case "Storage Esp" -> new ModuleScreen(parent, module, f -> {
                f.description("Hebt Kisten, Fässer, Öfen usw. hervor.");
                f.section("Allgemein");
                f.slider("Radius", 8, 64, () -> storageEspRadius, v -> storageEspRadius = v, 48);
                f.footer(() -> storageEsp, v -> storageEsp = v);
            });
            case "Hole Esp" -> new ModuleScreen(parent, module, f -> {
                f.description("Zeigt sichere 1x1-Löcher zum Hineinstellen.");
                f.section("Allgemein");
                f.slider("Radius", 4, 32, () -> holeEspRadius, v -> holeEspRadius = v, 16);
                f.footer(() -> holeEsp, v -> holeEsp = v);
            });
            case "Void Esp" -> new ModuleScreen(parent, module, f -> {
                f.description("Zeigt L\u00f6cher, durch die man in die Leere f\u00e4llt.");
                f.section("Allgemein");
                f.slider("Radius", 4, 24, () -> voidEspRadius, v -> voidEspRadius = v, 10);
                f.footer(() -> voidEsp, v -> voidEsp = v);
            });
            case "City Esp" -> new ModuleScreen(parent, module, f -> {
                f.description("Hebt Blöcke rund um Gegner hervor (PvP).");
                f.section("Allgemein");
                f.slider("Reichweite", 2, 12, () -> cityEspRange, v -> cityEspRange = v, 6);
                f.footer(() -> cityEsp, v -> cityEsp = v);
            });
            case "Tunnel Esp" -> new ModuleScreen(parent, module, f -> {
                f.description("Hebt gegrabene 1x2-G\u00e4nge hervor.");
                f.section("Allgemein");
                f.slider("Radius", 4, 32, () -> tunnelEspRadius, v -> tunnelEspRadius = v, 16);
                f.footer(() -> tunnelEsp, v -> tunnelEsp = v);
            });
            case "Tracers" -> new ModuleScreen(parent, module, f -> {
                f.description("Linien von der Bildschirmmitte zu Zielen.");
                f.section("Ziele");
                f.toggle("Spieler", () -> tracersPlayers, v -> tracersPlayers = v, true);
                f.toggle("Mobs", () -> tracersMobs, v -> tracersMobs = v, false);
                f.toggle("Items", () -> tracersItems, v -> tracersItems = v, false);
                f.slider("Reichweite", 16, 256, () -> tracersRange, v -> tracersRange = v, 96);
                f.footer(() -> tracers, v -> tracers = v);
            });
            case "Nametags" -> new ModuleScreen(parent, module, f -> {
                f.description("Namen über Spielern und Mobs, durch Wände.");
                f.section("Allgemein");
                f.toggle("Leben zeigen", () -> nametagsHealth, v -> nametagsHealth = v, true);
                f.slider("Reichweite", 16, 128, () -> nametagsRange, v -> nametagsRange = v, 64);
                f.footer(() -> nametags, v -> nametags = v);
            });
            case "Entity Owner" -> new ModuleScreen(parent, module, f -> {
                f.description("Zeigt den Besitzer von Tieren und Geschossen.");
                f.section("Allgemein");
                f.toggle("Zahme Tiere", () -> ownerAnimals, v -> ownerAnimals = v, true);
                f.toggle("Geschosse", () -> ownerProjectiles, v -> ownerProjectiles = v, true);
                f.slider("Reichweite", 8, 64, () -> ownerRange, v -> ownerRange = v, 32);
                f.footer(() -> entityOwner, v -> entityOwner = v);
            });
            case "Item Highlight" -> new ModuleScreen(parent, module, f -> {
                f.description("Rahmt am Boden liegende Items ein.");
                f.section("Allgemein");
                f.slider("Reichweite", 8, 96, () -> itemHighlightRange, v -> itemHighlightRange = v, 48);
                f.footer(() -> itemHighlight, v -> itemHighlight = v);
            });
            case "Marker" -> pointScreen(parent, module, false, () -> marker, v -> marker = v);
            case "Waypoints" -> new ModuleScreen(parent, module, f -> {
                f.description("Gespeicherte Orte im Spiel anzeigen.");
                f.section("Allgemein");
                f.toggle("Lichtstrahl", () -> waypointsBeam, v -> waypointsBeam = v, true);
                f.text("Neuer Name", () -> "", v -> {
                    if (!v.isBlank()) addPoint(true, v);
                }, "");
                f.note(() -> "Gespeichert: " + pointCount(waypointList));
                f.wideButton("Alle löschen", false, () -> clearPoints(true));
                f.footer(() -> waypoints, v -> waypoints = v);
            });
            case "Logout Spots" -> new ModuleScreen(parent, module, f -> {
                f.description("Markiert, wo Spieler sich ausgeloggt haben.");
                f.section("Allgemein");
                f.toggle("Kasten zeigen", () -> logoutBox, v -> logoutBox = v, true);
                f.toggle("Entfernung zeigen", () -> logoutDistance, v -> logoutDistance = v, true);
                f.note(() -> "Gemerkt: " + logoutCount());
                f.wideButton("Alle l\u00f6schen", false, RenderMods::clearLogouts);
                f.footer(() -> logoutSpots, v -> logoutSpots = v);
            });
            case "Trail" -> new ModuleScreen(parent, module, f -> {
                f.description("Zieht eine Linie hinter dir her.");
                f.section("Allgemein");
                f.slider("Länge", 20, 300, () -> trailLength, v -> trailLength = v, 80);
                f.footer(() -> trail, v -> trail = v);
            });
            case "Breadcrumbs" -> new ModuleScreen(parent, module, f -> {
                f.description("Punkte entlang deines Wegs.");
                f.section("Allgemein");
                f.slider("Länge", 40, 500, () -> breadcrumbsLength, v -> breadcrumbsLength = v, 200);
                f.footer(() -> breadcrumbs, v -> breadcrumbs = v);
            });
            case "Trajectories" -> new ModuleScreen(parent, module, f -> {
                f.description("Zeigt die Flugbahn von B\u00f6gen und Wurfobjekten.");
                f.section("Allgemein");
                f.slider("L\u00e4nge (Schritte)", 20, 200, () -> trajSteps, v -> trajSteps = v, 80);
                f.toggle("Aufprall markieren", () -> trajLanding, v -> trajLanding = v, true);
                f.footer(() -> trajectories, v -> trajectories = v);
            });
            case "Light Overlay" -> new ModuleScreen(parent, module, f -> {
                f.description("Markiert dunkle Blöcke, auf denen Mobs spawnen.");
                f.section("Allgemein");
                f.slider("Radius", 4, 24, () -> lightOverlayRadius, v -> lightOverlayRadius = v, 12);
                f.footer(() -> lightOverlay, v -> lightOverlay = v);
            });
            case "Break Indicators" -> new ModuleScreen(parent, module, f -> {
                f.description("Zeigt den Abbaufortschritt an Bl\u00f6cken\n(rot = gerade begonnen, gr\u00fcn = gleich weg).");
                f.section("Allgemein");
                f.toggle("Prozent anzeigen", () -> breakPercent, v -> breakPercent = v, true);
                f.toggle("Auch andere Spieler", () -> breakOthers, v -> breakOthers = v, true);
                f.footer(() -> breakIndicators, v -> breakIndicators = v);
            });
            case "Fullbright" -> new ModuleScreen(parent, module, f -> {
                f.description("Macht alles hell.");
                f.section("Allgemein");
                f.choice("Modus", FULLBRIGHT_MODES, () -> fullbrightMode, v -> fullbrightMode = v, 0);
                f.slider("Gamma (%) [nur Gamma-Modus]", 100, 1600, () -> fullbrightGamma, v -> fullbrightGamma = v, 1000);
                f.footer(() -> fullbright, v -> fullbright = v);
            });
            case "Time Changer" -> new ModuleScreen(parent, module, f -> {
                f.description("Ändert die Tageszeit (nur bei dir).\nServer setzt sie oft zurück.");
                f.section("Allgemein");
                f.slider("Zeit", 0, 23000, () -> timeChangerValue, v -> timeChangerValue = v, 6000);
                f.footer(() -> timeChanger, v -> timeChanger = v);
            });
            case "Camera Tweaks" -> new ModuleScreen(parent, module, f -> {
                f.description("Kamera in der 3rd-Person-Ansicht.");
                f.section("Allgemein");
                f.slider("Abstand", 2, 16, () -> cameraDistance, v -> cameraDistance = v, 4);
                f.toggle("Durch Blöcke", () -> cameraNoClip, v -> cameraNoClip = v, true);
                f.footer(() -> cameraTweaks, v -> cameraTweaks = v);
            });
            case "Free Look" -> new ModuleScreen(parent, module, f -> {
                f.description("Umsehen, ohne die Laufrichtung zu drehen.\n(Braucht Kamera-Mixin - evtl. wirkungslos.)");
                f.footer(() -> freeLook, v -> freeLook = v);
            });
            case "Freecam" -> new ModuleScreen(parent, module, f -> {
                f.description("Kamera frei bewegen, Körper bleibt stehen.\n(Schwer - evtl. wirkungslos in 26.2.)");
                f.section("Allgemein");
                f.slider("Tempo", 2, 40, () -> freecamSpeed, v -> freecamSpeed = v, 10);
                f.footer(() -> freecam, v -> freecam = v);
            });
            case "No Render" -> new ModuleScreen(parent, module, f -> {
                f.description("Blendet störende Effekte aus.");
                f.section("Aus");
                f.toggle("Feuer-Overlay", () -> noFire, v -> noFire = v, true);
                f.toggle("Wasser-Overlay", () -> noWaterOverlay, v -> noWaterOverlay = v, true);
                f.toggle("Übelkeit", () -> noNausea, v -> noNausea = v, true);
                f.toggle("Kürbis-Overlay", () -> noPumpkin, v -> noPumpkin = v, true);
                f.toggle("Nebel", () -> noFog, v -> noFog = v, false);
                f.toggle("Trefferwackeln", () -> noHurtCam, v -> noHurtCam = v, false);
                f.footer(() -> noRender, v -> noRender = v);
            });
            case "Block Selection" -> new ModuleScreen(parent, module, f -> {
                f.description("Ändert den Auswahlrahmen von Blöcken.");
                f.section("Allgemein");
                f.choice("Modus", BLOCK_SEL_MODES, () -> blockSelMode, v -> blockSelMode = v, 0);
                f.footer(() -> blockSelection, v -> blockSelection = v);
            });
            case "Hand View" -> flagScreen(parent, module, "Position der Hand anpassen.\n(Braucht Mixin - evtl. wirkungslos.)",
                    () -> handView, v -> handView = v);
            case "Chams" -> flagScreen(parent, module, "Wesen durch Wände sehen.\n(Braucht Render-Mixin - evtl. wirkungslos.)",
                    () -> chams, v -> chams = v);
            case "Wall Hack" -> flagScreen(parent, module, "Durch Wände sehen (wie Xray für alles).\n(Braucht Render-Mixin - evtl. wirkungslos.)",
                    () -> wallHack, v -> wallHack = v);
            case "Item Physics" -> flagScreen(parent, module, "Items liegen flach auf dem Boden.\n(Braucht Render-Mixin - evtl. wirkungslos.)",
                    () -> itemPhysics, v -> itemPhysics = v);
            case "Blur" -> new ModuleScreen(parent, module, f -> {
                f.description("Dunkler Hintergrund bei offenem Men\u00fc.\n(Vereinfacht, kein echter Weichzeichner.)");
                f.section("Allgemein");
                f.slider("St\u00e4rke", 0, 200, () -> blurStrength, v -> blurStrength = v, 100);
                f.footer(() -> blur, v -> blur = v);
            });
            case "Boss Stack" -> flagScreen(parent, module, "Mehrere Bossleisten kompakt stapeln.\n(Braucht HUD-Mixin - evtl. wirkungslos.)",
                    () -> bossStack, v -> bossStack = v);
            case "Better Tab" -> flagScreen(parent, module, "Tab-Liste mit Ping-Zahlen.\n(Braucht HUD-Mixin - evtl. wirkungslos.)",
                    () -> betterTab, v -> betterTab = v);
            case "Pop Chams" -> new ModuleScreen(parent, module, f -> {
                f.description("Zeigt einen verblassenden Geist, wenn\njemand ein Totem verbraucht.");
                f.section("Allgemein");
                f.slider("Dauer (s)", 1, 10, () -> popDuration, v -> popDuration = v, 3);
                f.toggle("Meldung im Chat", () -> popChat, v -> popChat = v, true);
                f.toggle("Auch eigene", () -> popSelf, v -> popSelf = v, false);
                f.footer(() -> popChams, v -> popChams = v);
            });
            case "Better Tooltips" -> new ModuleScreen(parent, module, f -> {
                f.description("Zus\u00e4tzliche Infos im Item-Tooltip.");
                f.section("Anzeigen");
                f.toggle("Shulker-Inhalt", () -> tipShulker, v -> tipShulker = v, true);
                f.slider("Shulker-Zeilen", 1, 27, () -> tipShulkerLines, v -> tipShulkerLines = v, 5);
                f.toggle("Haltbarkeit", () -> tipDurability, v -> tipDurability = v, true);
                f.toggle("N\u00e4hrwert", () -> tipFood, v -> tipFood = v, true);
                f.footer(() -> betterTooltips, v -> betterTooltips = v);
            });
            default -> null;
        };
    }

    private static Screen pointScreen(Screen parent, String module, boolean waypoint,
                                      java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set) {
        return new ModuleScreen(parent, module, f -> {
            f.description("Eigene Markierungen an deiner Position.");
            f.section("Allgemein");
            f.text("Neuer Name", () -> "", v -> {
                if (!v.isBlank()) addPoint(waypoint, v);
            }, "");
            f.note(() -> "Gesetzt: " + pointCount(markerList));
            f.wideButton("Alle löschen", false, () -> clearPoints(waypoint));
            f.footer(get, set);
        });
    }

    private static Screen flagScreen(Screen parent, String module, String desc,
                                     java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set) {
        return new ModuleScreen(parent, module, f -> {
            f.description(desc);
            f.footer(get, set);
        });
    }
}
