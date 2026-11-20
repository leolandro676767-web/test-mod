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
 * Render-Module (Kategorie "Render"): Schalter, Tick (Scans) und HUD-Zeichnung. ESP, Tracer,
 * Nametags, Chams, Wall Hack usw. werden ueber {@link Render3D} selbst aufs HUD projiziert.
 * Eigene Dateien: Better Tab ({@link BetterTab}), Freecam/Free Look
 * ({@link CameraMods}); Hand View und Item Physics laufen ueber Mixins, die die Schalter hier lesen.
 * Die Einstellungs-Fenster stehen in {@link RenderScreens}.
 */
public final class RenderMods {
    private RenderMods() {
    }

    // ---------- Schalter + Einstellungen ----------

    // Umriss-Stil fuer alle Block- und Wesen-Anzeigen (ESP, Block Esp, Abbau-Ziele, ...)
    public static int outlineStyle = 2;             // 0 = 2D, 1 = 3D-Rahmen, 2 = 3D + Scan-Linie
    public static int outlineScanSpeed = 4;
    public static boolean outlineWave = true;

    // Leistungs-Modus fuer schwache PCs: Bloecke zusammenfassen, weniger zeichnen, langsamer suchen
    public static boolean perfMode = false;

    // X-Ray: alles ausser den gewaehlten Bloecken unsichtbar (siehe Xray und die Xray-Mixins)
    public static boolean xray = false;
    public static boolean xrayBright = true;        // Nachtsicht, sonst sind die Erze im Dunkeln schwarz
    public static boolean xrayHideWater = true;
    public static final Set<Block> xrayBlocks = new LinkedHashSet<>();

    // Abbau-Ziele des Block-/Bereich-/Koordinaten-Werkzeugs anzeigen
    public static boolean miningHighlight = true;
    public static boolean miningShowArea = true;

    public static boolean blockEsp = false;
    public static int blockEspChunks = 6; // Chunks (1 Chunk = 16 Bloecke)
    public static final Set<Block> blockEspBlocks = new LinkedHashSet<>();

    public static boolean storageEsp = false;
    public static int storageEspChunks = 6; // Chunks (1 Chunk = 16 Bloecke)
    public static boolean storChests = true;    // Truhen und Faesser
    public static boolean storShulkers = true;
    public static boolean storEnder = true;
    public static boolean storFurnaces = true;  // Oefen, Schmelzoefen, Raeucheroefen
    public static boolean storHoppers = true;   // Trichter, Werfer, Spender
    public static boolean storBrewing = true;
    public static boolean storSpawners = false;
    public static boolean storGrayOpened = false; // schon geoeffnete grau

    public static boolean holeEsp = false;
    public static int holeEspChunks = 6; // Chunks (1 Chunk = 16 Bloecke)
    public static boolean holeOnlySafe = false; // nur Loecher aus Obsidian und Grundgestein

    public static boolean voidEsp = false;
    public static int voidEspChunks = 6; // Chunks (1 Chunk = 16 Bloecke)

    public static boolean cityEsp = false;
    public static int cityEspChunks = 6; // Chunks (1 Chunk = 16 Bloecke)

    public static boolean tunnelEsp = false;
    public static int tunnelEspChunks = 6; // Chunks (1 Chunk = 16 Bloecke)

    public static boolean tracers = false;
    public static boolean tracersPlayers = true;
    public static boolean tracersMobs = false;
    public static boolean tracersItems = false;
    public static int tracersChunks = 6; // Chunks (1 Chunk = 16 Bloecke)
    public static boolean tracersByDistance = false; // Farbe nach Entfernung (rot = nah)
    public static boolean tracersNoFriends = false;

    public static boolean nametags = false;
    public static boolean nametagsHealth = true;
    public static int nametagsChunks = 6; // Chunks (1 Chunk = 16 Bloecke)
    public static boolean nametagsArmor = true;     // Ruestung und Items in den Haenden
    public static boolean nametagsDistance = false;
    public static boolean nametagsPing = false;
    public static int nametagsSize = 100;           // Prozent

    public static boolean entityOwner = false;
    public static boolean ownerAnimals = true;
    public static boolean ownerProjectiles = true;
    public static int ownerChunks = 6; // Chunks (1 Chunk = 16 Bloecke)

    public static boolean itemHighlight = false;
    public static int itemHighlightChunks = 6; // Chunks (1 Chunk = 16 Bloecke)
    public static boolean itemValuable = false;     // nur wertvolle Items
    public static boolean itemNames = true;         // Name und Anzahl

    public static boolean marker = false;
    public static String markerList = "";           // "name,x,y,z;..."

    public static boolean waypoints = false;
    public static String waypointList = "";
    public static boolean waypointsBeam = true;
    public static boolean waypointsDistance = true;
    public static boolean waypointsDimOnly = true;  // nur Wegpunkte der aktuellen Dimension

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
    public static int lightOverlayChunks = 6; // Chunks (1 Chunk = 16 Bloecke)

    public static boolean breakIndicators = false;
    public static boolean breakPercent = true;
    public static boolean breakOthers = true;

    // Reine Schalter (von Tick oder Mixin gelesen)
    public static boolean fullbright = false;
    public static boolean timeChanger = false;
    public static int timeChangerValue = 6000;      // 0=Morgen, 6000=Mittag, 18000=Mitternacht
    public static boolean timeClearWeather = false; // Regen und Gewitter ausblenden
    public static boolean cameraTweaks = false;
    public static int cameraDistance = 4;
    public static boolean cameraNoClip = true;
    public static boolean freeLook = false;
    public static boolean freeLookThird = true;   // Free Look: automatisch 3rd Person, schraeg von der Seite
    public static int freeLookAngle = 50;         // Winkel zur Seite (0 = genau dahinter, 90 = seitlich)
    public static int freeLookPitch = 25;         // Neigung von oben
    public static boolean freeLookFollow = true;  // Kamera schwenkt mit der Laufrichtung mit
    public static boolean freecam = false;
    public static boolean freecamBlockClicks = true;
    public static boolean freecamWaitGround = true; // erst starten, wenn der Koerper am Boden steht
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
    public static int handX = 0;          // Hundertstel Bloecke
    public static int handY = 0;
    public static int handZ = 0;
    public static int handScale = 100;    // Prozent
    public static boolean chams = false;
    public static boolean chamsPlayers = true;
    public static boolean chamsMobs = true;
    public static boolean chamsHiddenOnly = false; // nur faerben, wenn das Wesen verdeckt ist
    public static int chamsChunks = 6; // Chunks (1 Chunk = 16 Bloecke)
    public static int chamsAlpha = 45;             // Deckkraft in Prozent
    public static boolean wallHack = false;
    public static int wallHackChunks = 6; // Chunks (1 Chunk = 16 Bloecke)
    public static boolean wallDiamond = true;
    public static boolean wallDebris = true;
    public static boolean wallGold = true;
    public static boolean wallIron = false;
    public static boolean wallEmerald = true;
    public static boolean wallRedstone = false;
    public static boolean wallLapis = false;
    public static boolean wallCoal = false;
    public static boolean wallCopper = false;
    public static boolean wallQuartz = false;
    public static boolean itemPhysics = false;
    public static boolean blur = false;
    public static int blurStrength = 100;       // Abdunkelung (0..200)
    public static boolean betterTab = false;
    public static boolean betterTooltips = false;
    public static boolean popChams = false;
    public static int popDuration = 3;          // Sekunden
    public static boolean popChat = true;
    public static boolean popSelf = false;

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
    private static final List<BlockPos> miningBlocks = new ArrayList<>();

    record Pop(String name, AABB box, long time) {
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
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("test", "render"), Safe.hud("Render", RenderMods::render));
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("test", "better_tab"), Safe.hud("Better Tab", BetterTab::render));
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (betterTooltips) BetterTooltips.append(stack, lines);
        });
    }

    /** Standard fuer X-Ray: alle Erze, Antiker Schrott und Spawner (nur wenn noch nichts gewaehlt ist). */
    static void xrayDefaults() {
        if (!xrayBlocks.isEmpty()) return;
        for (Block b : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath();
            if (path.endsWith("_ore")) xrayBlocks.add(b);
        }
        xrayBlocks.add(Blocks.ANCIENT_DEBRIS);
        xrayBlocks.add(Blocks.SPAWNER);
    }

    private static int xrayKey = 0;

    /** X-Ray an/aus oder andere Bloecke: Chunks neu bauen lassen. */
    private static void syncXray(Minecraft mc) {
        boolean on = xray && mc.level != null;
        int key = on ? ((xrayBlocks.hashCode() * 31 + xrayBlocks.size()) * 2 + (xrayHideWater ? 1 : 0)) | 1 : 0;
        if (key == xrayKey) return;
        xrayKey = key;
        Xray.set(on, xrayHideWater, xrayBlocks);
        if (mc.level != null) mc.levelExtractor.allChanged();
    }

    public static void rescan() {
        scanCooldown = 0;
        FAST_ESP.restart();
        FAST_ORES.restart();
    }

    // ---------- Tick: scannen, verfolgen, einfache Schalter ----------

    public static void tick(Minecraft mc) {
        Safe.run("X-Ray", () -> syncXray(mc));
        Player self = mc.player;
        Level level = mc.level;
        if (self == null || level == null) {
            trailPts.clear();
            lastSeen.clear();
            logouts.clear();
            // Welt verlassen: Suchen von vorn beginnen (sonst zeigen sie kurz Bloecke der alten Welt)
            FAST_ESP.clear(espBlocks);
            FAST_STORAGE.clear(storages);
            SCAN_HOLES.clear(holes);
            SCAN_LIGHT.clear(lights);
            SCAN_TUNNELS.clear(tunnels);
            FAST_ORES.clear(ores);
            return;
        }

        boolean nightVision = fullbright && fullbrightMode == 0 || xray && xrayBright;
        boolean gamma = fullbright && fullbrightMode == 1;
        if (nightVision) {
            applyFullbright(self);
        } else if (fullbrightApplied) {
            self.removeEffect(MobEffects.NIGHT_VISION);
            fullbrightApplied = false;
        }
        if (gamma) setGamma(mc, fullbrightGamma / 100.0);
        else restoreGamma(mc);
        if (timeChanger) setClientDayTime(level, timeChangerValue);
        if (timeChanger && timeClearWeather) {
            level.setRainLevel(0f);
            level.setThunderLevel(0f);
        }
        if (storageEsp && storGrayOpened) trackOpened(mc, level);
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

        // Block-Scans: grosse verteilt auf jeden Tick, kleine gebuendelt
        stepScans(mc, self);
        if (--scanCooldown <= 0) {
            scanCooldown = SCAN_INTERVAL;
            runScans(mc, self);
        }
    }

    // ---------- Time Changer (Reflection: in 26.2 gibt es Level.setDayTime nicht mehr) ----------

    private static final java.util.Map<Class<?>, java.lang.reflect.Method> TIME_SETTERS = new java.util.HashMap<>();
    private static boolean timeWarned = false;

    private static java.lang.reflect.Method findMethod(Class<?> c, String name, int params) {
        if (TIME_SETTERS.containsKey(c)) return TIME_SETTERS.get(c);
        java.lang.reflect.Method found = null;
        for (Class<?> k = c; k != null && found == null; k = k.getSuperclass()) {
            for (java.lang.reflect.Method m : k.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == params) {
                    m.setAccessible(true);
                    found = m;
                    break;
                }
            }
        }
        TIME_SETTERS.put(c, found);
        return found;
    }

    /** Setzt die Tageszeit nur bei dir: zuerst am Level, sonst an dessen LevelData. */
    private static void setClientDayTime(Level level, long time) {
        try {
            java.lang.reflect.Method set = findMethod(level.getClass(), "setDayTime", 1);
            if (set != null) {
                set.invoke(level, time);
                return;
            }
            java.lang.reflect.Method getData = findMethod(level.getClass(), "getLevelData", 0);
            if (getData != null) {
                Object data = getData.invoke(level);
                java.lang.reflect.Method setData = data == null ? null : findMethod(data.getClass(), "setDayTime", 1);
                if (setData != null) {
                    setData.invoke(data, time);
                    return;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // unten melden
        }
        if (!timeWarned) {
            timeWarned = true;
            System.err.println("[1912] Time Changer: keine Methode zum Setzen der Tageszeit gefunden");
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

    private static final List<BlockPos> ores = new ArrayList<>();

    /** Wall Hack: Farbe eines gesuchten Erzes, 0 = nicht gesucht. */
    private static int oreColor(Block b) {
        if (wallDiamond && (b == Blocks.DIAMOND_ORE || b == Blocks.DEEPSLATE_DIAMOND_ORE)) return 0xFF4DE8FF;
        if (wallDebris && b == Blocks.ANCIENT_DEBRIS) return 0xFFB07050;
        if (wallGold && (b == Blocks.GOLD_ORE || b == Blocks.DEEPSLATE_GOLD_ORE || b == Blocks.NETHER_GOLD_ORE)) {
            return 0xFFFFD84D;
        }
        if (wallIron && (b == Blocks.IRON_ORE || b == Blocks.DEEPSLATE_IRON_ORE)) return 0xFFE0B49A;
        if (wallEmerald && (b == Blocks.EMERALD_ORE || b == Blocks.DEEPSLATE_EMERALD_ORE)) return 0xFF3DFF7A;
        if (wallRedstone && (b == Blocks.REDSTONE_ORE || b == Blocks.DEEPSLATE_REDSTONE_ORE)) return 0xFFFF3B3B;
        if (wallLapis && (b == Blocks.LAPIS_ORE || b == Blocks.DEEPSLATE_LAPIS_ORE)) return 0xFF3B5BFF;
        if (wallCoal && (b == Blocks.COAL_ORE || b == Blocks.DEEPSLATE_COAL_ORE)) return 0xFF505050;
        if (wallCopper && (b == Blocks.COPPER_ORE || b == Blocks.DEEPSLATE_COPPER_ORE)) return 0xFFE07B4D;
        if (wallQuartz && b == Blocks.NETHER_QUARTZ_ORE) return 0xFFF2EEE6;
        return 0;
    }

    // Suchen nur nach dem Blocktyp: Chunk fuer Chunk, sofort bei neuen Chunks und Blockaenderungen (ChunkScan)
    private static final ChunkScan FAST_ESP = new ChunkScan(MAX_DRAW);
    private static final ChunkScan FAST_STORAGE = new ChunkScan(MAX_DRAW);
    private static final ChunkScan FAST_ORES = new ChunkScan(MAX_DRAW);
    // Suchen, die Nachbarbloecke anschauen: verteilt ueber mehrere Ticks (BlockScan)
    private static final BlockScan SCAN_HOLES = new BlockScan(MAX_DRAW);
    private static final BlockScan SCAN_LIGHT = new BlockScan(MAX_DRAW);
    private static final BlockScan SCAN_TUNNELS = new BlockScan(MAX_DRAW);
    private static final BlockScan SCAN_VOID = new BlockScan(MAX_DRAW);

    /** Fuer das Menue: Stand der Suche (und ob der Server Erze versteckt). */
    static String blockEspStatus() {
        return blockEsp && !blockEspBlocks.isEmpty() ? FAST_ESP.status(espBlocks) : Lang.t("Sucht, sobald es an ist.");
    }

    static String wallHackStatus() {
        return wallHack ? FAST_ORES.status(ores) : Lang.t("Sucht, sobald es an ist.");
    }

    /** Aendert sich, sobald sich die Auswahl aendert (dann sucht die Chunk-Suche alles neu). */
    private static Integer setKey(java.util.Set<?> set) {
        return set.hashCode() * 31 + set.size();
    }

    private static Integer oreKey() {
        boolean[] f = {wallDiamond, wallDebris, wallGold, wallIron, wallEmerald, wallRedstone, wallLapis, wallCoal,
            wallCopper, wallQuartz};
        int k = 0;
        for (int i = 0; i < f.length; i++) if (f[i]) k |= 1 << i;
        return k;
    }

    /** Jeden Tick: ein Stueck der grossen Suchen. */
    private static void stepScans(Minecraft mc, Player self) {
        Level level = mc.level;
        BlockPos c = self.blockPosition();
        var cl = mc.level;
        if (blockEsp && !blockEspBlocks.isEmpty()) {
            int r = blockEspChunks * 16;
            FAST_ESP.tick(cl, c, r, r, r, true, st -> blockEspBlocks.contains(st.getBlock()), setKey(blockEspBlocks),
                    espBlocks);
        } else {
            FAST_ESP.clear(espBlocks);
        }
        if (storageEsp) {
            FAST_STORAGE.tick(cl, c, storageEspChunks * 16, 8, 8, false, RenderMods::isStorage, storageKey(), storages);
        } else {
            FAST_STORAGE.clear(storages);
        }
        if (holeEsp) {
            SCAN_HOLES.stepBox(c, holeEspChunks * 16, Math.min(24, holeEspChunks * 16), 4, SCAN_INTERVAL, pos -> isHole(level, pos), holes);
        } else {
            SCAN_HOLES.clear(holes);
        }
        if (lightOverlay) {
            SCAN_LIGHT.stepBox(c, (lightOverlayChunks * 16), 3, 5, SCAN_INTERVAL, pos -> isDark(level, pos), lights);
        } else {
            SCAN_LIGHT.clear(lights);
        }
        if (tunnelEsp) {
            SCAN_TUNNELS.stepBox(c, (tunnelEspChunks * 16), 8, 8, SCAN_INTERVAL, pos -> isTunnel(level, pos), tunnels);
        } else {
            SCAN_TUNNELS.clear(tunnels);
        }
        if (voidEsp && !level.dimensionType().hasCeiling()) {
            // Loecher im Grundgestein: unterste 6 Schichten ohne festen Block (verteilt, auch bei grossem Radius)
            int minY = level.getMinY();
            SCAN_VOID.stepBox(new BlockPos(c.getX(), minY, c.getZ()), voidEspChunks * 16, 0, 0, SCAN_INTERVAL,
                    pos -> voidColumn(level, pos), voids);
        } else {
            SCAN_VOID.clear(voids);
        }
        if (wallHack) {
            int r = wallHackChunks * 16;
            FAST_ORES.tick(cl, c, r, r, r, true, st -> oreColor(st.getBlock()) != 0, oreKey(), ores);
        } else {
            FAST_ORES.clear(ores);
        }
    }

    /** Alle SCAN_INTERVAL Ticks: die kleinen Suchen. */
    private static void runScans(Minecraft mc, Player self) {
        miningBlocks.clear();
        Level level = mc.level;
        BlockPos c = self.blockPosition();
        if (miningHighlight && TestClient.blockMode && TestClient.selectedBlock != null) scanMining(level);
    }

    private static boolean isStorage(BlockState s) {
        Block b = s.getBlock();
        if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL) return storChests;
        if (b instanceof net.minecraft.world.level.block.ShulkerBoxBlock) return storShulkers; // alle 17 Farben
        if (b == Blocks.ENDER_CHEST) return storEnder;
        if (b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER) return storFurnaces;
        if (b == Blocks.HOPPER || b == Blocks.DISPENSER || b == Blocks.DROPPER) return storHoppers;
        if (b == Blocks.BREWING_STAND) return storBrewing;
        if (b == Blocks.SPAWNER) return storSpawners;
        return false;
    }

    /** Schluessel fuer die Suche: aendert sich die Auswahl, sucht sie neu. */
    private static Integer storageKey() {
        boolean[] on = {storChests, storShulkers, storEnder, storFurnaces, storHoppers, storBrewing, storSpawners};
        int k = 1;
        for (int i = 0; i < on.length; i++) {
            if (on[i]) k |= 2 << i;
        }
        return k;
    }

    // Storage Esp: schon geoeffnete Behaelter (nur fuer diese Sitzung)
    private static final Set<Long> openedStorage = new java.util.HashSet<>();
    private static Level openedLevel;
    private static boolean containerWasOpen;

    private static void trackOpened(Minecraft mc, Level level) {
        if (level != openedLevel) {
            openedStorage.clear();
            openedLevel = level;
        }
        var screen = mc.gui.screen();
        boolean open = screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>
                && !(screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)
                && !(screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen);
        if (open && !containerWasOpen && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bh
                && bh.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            BlockPos pos = bh.getBlockPos();
            BlockState st = level.getBlockState(pos);
            openedStorage.add(pos.asLong());
            // Doppeltruhe: die andere Haelfte gehoert dazu
            if (st.getBlock() instanceof net.minecraft.world.level.block.ChestBlock
                    && st.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)
                            != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
                openedStorage.add(pos.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(st)).asLong());
            }
        }
        containerWasOpen = open;
    }

    /** Hole Esp: 2 = alles Grundgestein, 1 = alles mindestens so fest wie Obsidian, 0 = sonst. */
    private static int holeGrade(Level level, BlockPos p) {
        float min = Float.MAX_VALUE;
        for (BlockPos n : new BlockPos[] {p.below(), p.north(), p.south(), p.east(), p.west()}) {
            min = Math.min(min, level.getBlockState(n).getBlock().getExplosionResistance());
        }
        return min >= 3_000_000f ? 2 : min >= 600f ? 1 : 0;
    }

    /** Item Highlight: lohnt es sich, das aufzuheben? */
    private static boolean valuable(ItemStack st) {
        if (st.isEnchanted()) return true;
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        if (path.contains("diamond") || path.contains("netherite") || path.endsWith("shulker_box")) return true;
        return switch (path) {
            case "ancient_debris", "totem_of_undying", "elytra", "golden_apple", "enchanted_golden_apple",
                    "nether_star", "beacon", "enchanted_book", "trident", "mace", "heavy_core", "dragon_egg",
                    "emerald", "emerald_block", "end_crystal", "experience_bottle" -> true;
            default -> false;
        };
    }

    private static boolean solid(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** Sicheres 1x1-Loch: Boden solide, Kopf- und Fusshoehe frei, ringsum solide. */
    private static boolean isHole(Level level, BlockPos p) {
        if (solid(level, p) || solid(level, p.above()) || !solid(level, p.below())) return false;
        return solid(level, p.north()) && solid(level, p.south()) && solid(level, p.east()) && solid(level, p.west());
    }

    /** Void Esp: in den untersten 6 Schichten dieser Saeule ist kein fester Block. */
    private static boolean voidColumn(Level level, BlockPos bottom) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = 0; y < 6; y++) {
            m.set(bottom.getX(), bottom.getY() + y, bottom.getZ());
            if (solid(level, m)) return false;
        }
        return true;
    }

    /** Light Overlay: auf diesem Luftblock koennte ein Monster spawnen (Boden fest, 2 Bloecke frei, kein Blocklicht). */
    private static boolean isDark(Level level, BlockPos a) {
        if (solid(level, a) || solid(level, a.above()) || !solid(level, a.below())) return false;
        return level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, a) <= 0;
    }

    /** 1x2-Gang: Fuss- und Kopfhoehe frei, Boden und Decke fest, links/rechts fest (aber nicht ringsum). */
    private static boolean isTunnel(Level level, BlockPos p) {
        if (solid(level, p) || solid(level, p.above())) return false;
        if (!solid(level, p.below()) || !solid(level, p.above(2))) return false;
        boolean ew = solid(level, p.east()) && solid(level, p.west())
                && solid(level, p.above().east()) && solid(level, p.above().west());
        boolean ns = solid(level, p.north()) && solid(level, p.south())
                && solid(level, p.above().north()) && solid(level, p.above().south());
        return ew != ns; // keiner = offen, beide = Loch statt Gang
    }

    /** Vom ClientPacketListenerMixin: jemand hat ein Totem verbraucht (Ereignis 35). */
    public static void onTotemPop(LivingEntity e) {
        Safe.run("Auto Totem", () -> Survival.onTotemPop(e)); // zuerst: sofort das naechste Totem nachlegen
        DebrisMiner.onTotemPop(e); // Auto-Log nach Totem
        TunnelMiner.onTotemPop(e);
        if (!popChams) return;
        Minecraft mc = Minecraft.getInstance();
        if (e == mc.player && !popSelf) return;
        String name = e.getName().getString();
        pops.add(new Pop(name, e.getBoundingBox(), System.currentTimeMillis()));
        int n = popCount.merge(name, 1, Integer::sum);
        if (popChat && mc.player != null) {
            mc.player.sendSystemMessage(Lang.literal("\u00a7d1912: " + name + " hat ein Totem verbraucht (" + n + ")"));
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
        Render3D.outline(g, bb, color, true);
        if (breakPercent) {
            Render3D.label(g, mc, Vec3.atCenterOf(pos), Math.round(prog * 100) + "%", color, -4);
        }
    }

    /** Alle Bloecke des gewaehlten Typs im Suchbereich des Block-Modus (fuer die Anzeige). */
    private static void scanMining(Level level) {
        int[] b = TestClient.miningBounds();
        if (b == null) return;
        Block want = TestClient.selectedBlock;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = b[0]; x <= b[3]; x++) {
            for (int y = b[1]; y <= b[4]; y++) {
                for (int z = b[2]; z <= b[5]; z++) {
                    m.set(x, y, z);
                    if (level.getBlockState(m).getBlock() == want) {
                        miningBlocks.add(m.immutable());
                        if (miningBlocks.size() >= MAX_DRAW) return;
                    }
                }
            }
        }
    }

    private static final int C_MINE = 0xFF39FF14;
    private static final int C_MINE_TARGET = 0xFFFFE14D;
    private static final int C_MINE_AREA = 0xFF4DD2FF;

    /** Ziele des Abbau-/Lauf-Werkzeugs: gesuchte Bloecke, aktuelles Ziel, Bereichsrahmen. */
    private static void drawMining(GuiGraphicsExtractor g) {
        if (!miningHighlight || !TestClient.areaActive) return;
        BlockPos target = TestClient.miningTarget();
        if (TestClient.blockMode) {
            for (BlockPos p : miningBlocks) {
                if (p.equals(target)) continue;
                if (Minecraft.getInstance().level.getBlockState(p).isAir()) continue; // schon abgebaut
                Render3D.outline(g, cube(p), C_MINE, false);
            }
        } else if (miningShowArea) {
            int[] b = TestClient.miningBounds();
            if (b != null) {
                Render3D.wire(g, new AABB(b[0], b[1], b[2], b[3] + 1, b[4] + 1, b[5] + 1), C_MINE_AREA);
            }
        }
        if (target != null) Render3D.outline(g, cube(target), C_MINE_TARGET, true);
    }

    // ---------- Render (HUD) ----------

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        try {
            renderWorldOverlays(g, delta, mc);
        } finally {
            // Freecam / Free Look: jeden Frame fluessig drehen. Erst nach dem Zeichnen, damit Linien und
            // Umrisse mit genau dem Blickwinkel gezeichnet werden, mit dem die Welt gerade gerendert wurde.
            CameraMods.frame(mc);
        }
    }

    private static void renderWorldOverlays(GuiGraphicsExtractor g, DeltaTracker delta, Minecraft mc) {
        BetterTab.frame(mc);

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
            if (chams) drawChams(g, mc);
            drawEntities(g, mc);
            drawMarkersAndWaypoints(g, mc);
            drawLogouts(g, mc);
            drawTrail(g);
            if (trajectories) drawTrajectory(g, mc);
            if (tunnelEsp && perfMode) {
                Render3D.blocks(g, tunnels, tunnels, p -> 0xFFFFA64D, 2);
            } else if (tunnelEsp) {
                for (BlockPos p : tunnels) {
                    Render3D.outline(g, new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 2,
                            p.getZ() + 1), 0xFFFFA64D, false);
                }
            }
            if (popChams) drawPops(g);
            if (breakIndicators) drawBreaking(g, mc);
            drawMining(g);
            if (TestClient.areaActive) Walker.render(g); // geplanter Weg (Koordinaten, Bereich, Bloecke suchen)
            if (DebrisMiner.isRunning()) DebrisMiner.render(g); // gefundenes Debris, naechster Tunnelabschnitt
            if (TunnelMiner.isRunning()) TunnelMiner.render(g); // naechster Tunnelabschnitt
        } finally {
            Ui.alpha = old;
        }
    }

    /** Chams: fuellt Spieler und Mobs farbig - auf dem HUD, deshalb auch durch Waende sichtbar. */
    private static void drawChams(GuiGraphicsExtractor g, Minecraft mc) {
        Player self = mc.player;
        int a = Math.max(5, Math.min(100, chamsAlpha)) * 255 / 100;
        int drawn = 0;
        for (Entity e : mc.level.getEntities(self, self.getBoundingBox().inflate((chamsChunks * 16)))) {
            if (drawn >= MAX_DRAW) break;
            if (!(e instanceof LivingEntity le) || !e.isAlive()) continue;
            boolean player = e instanceof Player;
            if (player ? !chamsPlayers : !chamsMobs) continue;
            AABB bb = Render3D.smoothBox(e);
            if (self.getEyePosition().distanceTo(bb.getCenter()) > (chamsChunks * 16)) continue;
            if (chamsHiddenOnly && self.hasLineOfSight(le)) continue;
            int c = (player ? Friends.chamsColor(e, Esp.playerColor()) : Esp.mobColor()) & 0xFFFFFF;
            // oben kraeftig, nach unten weich auslaufend
            if (Render3D.fillBox(g, bb, (a << 24) | c, ((a * 2 / 5) << 24) | c)) drawn++;
        }
    }

    private static void drawBlockLists(GuiGraphicsExtractor g) {
        if (perfMode) {
            drawBlockListsFast(g);
            return;
        }
        if (wallHack) {
            Level level = Minecraft.getInstance().level;
            for (BlockPos p : ores) {
                int c = oreColor(level.getBlockState(p).getBlock());
                if (c == 0) continue; // inzwischen abgebaut
                Render3D.fillBox(g, cube(p), (0x40 << 24) | (c & 0xFFFFFF), (0x18 << 24) | (c & 0xFFFFFF));
                Render3D.outline(g, cube(p), c, false);
            }
        }
        if (blockEsp) for (BlockPos p : espBlocks) Render3D.outline(g, cube(p), Esp.blockColor(), false);
        if (storageEsp) {
            Level level = Minecraft.getInstance().level;
            for (BlockPos p : storages) {
                int col = storGrayOpened && openedStorage.contains(p.asLong()) ? 0xFF808080
                        : level.getBlockState(p).is(Blocks.SPAWNER) ? 0xFFFF6E4D : C_STORAGE;
                Render3D.outline(g, cube(p), col, true);
            }
        }
        if (holeEsp) {
            Level level = Minecraft.getInstance().level;
            for (BlockPos p : holes) {
                int grade = holeGrade(level, p);
                if (holeOnlySafe && grade == 0) continue;
                int col = grade == 2 ? 0xFF4DFF88 : grade == 1 ? 0xFFFFD24D : C_HOLE;
                Render3D.outline(g, new AABB(p), col, false);
            }
        }
        if (voidEsp) for (BlockPos p : voids) Render3D.outline(g, new AABB(p).inflate(0, 0.2, 0), C_VOID, true);
        if (lightOverlay) for (BlockPos p : lights) {
            Vec3 ctr = new Vec3(p.getX() + 0.5, p.getY() + 0.02, p.getZ() + 0.5);
            Render3D.label(g, Minecraft.getInstance(), ctr, "x", 0xFFFF3030, 0);
        }
    }

    /** Leistungs-Modus: zusammenhaengende Bloecke als ein Kasten, ohne Fuellung; Light Overlay hoechstens 100. */
    private static void drawBlockListsFast(GuiGraphicsExtractor g) {
        Level level = Minecraft.getInstance().level;
        if (wallHack) Render3D.blocks(g, ores, ores, p -> oreColor(level.getBlockState(p).getBlock()), 1);
        if (blockEsp) Render3D.blocks(g, espBlocks, espBlocks, p -> Esp.blockColor(), 1);
        if (storageEsp) {
            Render3D.blocks(g, storages, storages, p -> storGrayOpened && openedStorage.contains(p.asLong()) ? 0xFF808080
                    : level.getBlockState(p).is(Blocks.SPAWNER) ? 0xFFFF6E4D : C_STORAGE, 1);
        }
        if (holeEsp) {
            Render3D.blocks(g, holes, holes, p -> {
                int grade = holeGrade(level, p);
                if (holeOnlySafe && grade == 0) return 0;
                return grade == 2 ? 0xFF4DFF88 : grade == 1 ? 0xFFFFD24D : C_HOLE;
            }, 1);
        }
        if (voidEsp) Render3D.blocks(g, voids, voids, p -> C_VOID, 1);
        if (lightOverlay) {
            int n = 0;
            for (BlockPos p : lights) {
                if (++n > Render3D.PERF_MAX) break;
                Vec3 ctr = new Vec3(p.getX() + 0.5, p.getY() + 0.02, p.getZ() + 0.5);
                Render3D.label(g, Minecraft.getInstance(), ctr, "x", 0xFFFF3030, 0);
            }
        }
    }

    private static AABB cube(BlockPos p) {
        return new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1);
    }

    private static void drawEntities(GuiGraphicsExtractor g, Minecraft mc) {
        boolean anyEnt = tracers || nametags || entityOwner || itemHighlight || cityEsp;
        if (!anyEnt) return;
        Player self = mc.player;
        double range = Math.max(Math.max(Math.max((tracersChunks * 16), (nametagsChunks * 16)), (itemHighlightChunks * 16)), (ownerChunks * 16));
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
                if (tracers && tracersPlayers && dist <= (tracersChunks * 16) && !(tracersNoFriends && Friends.is(pl))) {
                    Render3D.tracer(g, center, tracer(Friends.tracerColor(pl, tracerBase(C_PLAYER, dist))));
                }
                if (nametags && dist <= (nametagsChunks * 16)) drawNametag(g, mc, pl, bb);
            } else if (e instanceof ItemEntity ie) {
                if (itemHighlight && dist <= (itemHighlightChunks * 16) && (!itemValuable || valuable(ie.getItem()))) {
                    Render3D.outline(g, bb.inflate(0.1), C_ITEM, false);
                    int n = ie.getItem().getCount();
                    if (itemNames) {
                        Render3D.label(g, mc, bb.getCenter().add(0, 0.4, 0),
                                ie.getItem().getHoverName().getString() + (n > 1 ? " x" + n : ""), C_ITEM, -10);
                    }
                }
                if (tracers && tracersItems && dist <= (tracersChunks * 16)) {
                    Render3D.tracer(g, center, tracer(tracerBase(C_ITEM, dist)));
                }
            } else if (e instanceof LivingEntity) {
                if (tracers && tracersMobs && dist <= (tracersChunks * 16)) {
                    Render3D.tracer(g, center, tracer(tracerBase(C_MOB, dist)));
                }
                if (nametags && dist <= (nametagsChunks * 16)) drawNametag(g, mc, (LivingEntity) e, bb);
            }

            if (entityOwner && dist <= (ownerChunks * 16)) drawOwner(g, mc, e, bb);
            drawn++;
        }
    }

    private static int tracer(int color) {
        return (color & 0x00FFFFFF) | 0xB0000000;
    }

    /** Tracer-Farbe: normal oder nach Entfernung (rot = nah, gelb, gruen = weit weg). */
    private static int tracerBase(int normal, double dist) {
        if (!tracersByDistance) return normal;
        float t = (float) Math.max(0, Math.min(1, (dist - 8) / 52.0));
        int r = t < 0.5f ? 255 : (int) (255 * (1 - t) * 2);
        int gr = t < 0.5f ? (int) (255 * t * 2) : 255;
        return 0xFF000000 | (r << 16) | (gr << 8) | 0x40;
    }

    private static void drawNametag(GuiGraphicsExtractor g, Minecraft mc, LivingEntity e, AABB bb) {
        StringBuilder sb = new StringBuilder(e.getName().getString()); // Namen nie uebersetzen
        if (nametagsHealth) sb.append("  ").append((int) Math.ceil(e.getHealth())).append('❤');
        if (nametagsDistance) sb.append("  ").append(Math.round(mc.player.distanceTo(e))).append('m');
        if (nametagsPing && e instanceof Player pl && mc.getConnection() != null) {
            var info = mc.getConnection().getPlayerInfo(pl.getUUID());
            if (info != null) sb.append("  ").append(info.getLatency()).append("ms");
        }
        String text = sb.toString();
        double[] p = Render3D.project(bb.getCenter().x, bb.maxY, bb.getCenter().z);
        if (p[2] <= 0.05) return; // hinter der Kamera
        int color = (Friends.nametagColor(e, 0xFFFFFFFF) & 0x00FFFFFF) | 0xFF000000;
        float s = Math.max(50, Math.min(200, nametagsSize)) / 100f;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate((float) p[0], (float) p[1]);
        pose.scale(s, s);
        int tw = mc.font.width(text);
        int x = -tw / 2;
        int y = -12;
        Ui.fill(g, x - 2, y - 1, x + tw + 2, y + 9, 0x90000000);
        Ui.text(g, mc.font, text, x, y, color, true);
        if (nametagsArmor) {
            List<ItemStack> items = new ArrayList<>();
            for (var slot : new net.minecraft.world.entity.EquipmentSlot[] {
                    net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.entity.EquipmentSlot.HEAD,
                    net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS,
                    net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.entity.EquipmentSlot.OFFHAND}) {
                ItemStack st = e.getItemBySlot(slot);
                if (!st.isEmpty()) items.add(st);
            }
            int ix = -items.size() * 8;
            for (ItemStack st : items) {
                g.fakeItem(st, ix, y - 18);
                g.itemDecorations(mc.font, st, ix, y - 18);
                ix += 16;
            }
        }
        pose.popMatrix();
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
        if (enemy == mc.player || Friends.skipCity(enemy)) return;
        if (enemy.distanceTo(mc.player) > cityEspChunks * 16) return;
        BlockPos base = enemy.blockPosition();
        for (BlockPos p : new BlockPos[] {base.below(), base.north(), base.south(), base.east(), base.west()}) {
            if (solid(mc.level, p) && mc.level.getBlockState(p).getBlock() != Blocks.BEDROCK) {
                Render3D.outline(g, cube(p), C_CITY, false);
            }
        }
    }

    private static void drawMarkersAndWaypoints(GuiGraphicsExtractor g, Minecraft mc) {
        if (marker) drawPoints(g, mc, markerList, C_MARK, false);
        if (waypoints) drawPoints(g, mc, waypointList, 0xFFFFD24D, waypointsBeam);
    }

    /** Kurzname der Dimension (overworld, the_nether, the_end) - steht als 5. Feld in neuen Wegpunkten. */
    static String dimId(Level level) {
        return level.dimension().identifier().getPath();
    }

    /** Wegpunkt aus einer anderen Dimension (und die Einstellung blendet sie aus)? */
    static boolean otherDim(String[] f, Level level) {
        return waypointsDimOnly && f.length >= 5 && !f[4].trim().equals(dimId(level));
    }

    private static void drawPoints(GuiGraphicsExtractor g, Minecraft mc, String list, int color, boolean beam) {
        if (list == null || list.isEmpty()) return;
        Vec3 eye = mc.player.getEyePosition();
        boolean wp = list == waypointList;
        for (String part : list.split(";")) {
            String[] f = part.split(",");
            if (f.length < 4) continue;
            if (wp && otherDim(f, mc.level)) continue;
            try {
                double x = Double.parseDouble(f[1]) + 0.5;
                double y = Double.parseDouble(f[2]);
                double z = Double.parseDouble(f[3]) + 0.5;
                Vec3 at = new Vec3(x, y + 0.5, z);
                if (beam) Render3D.worldLine(g, new Vec3(x, y, z), new Vec3(x, y + 3, z), color);
                Render3D.dot(g, at, 2, color);
                int dist = (int) eye.distanceTo(new Vec3(x, y, z));
                Render3D.label(g, mc, at, wp && !waypointsDistance ? f[0] : f[0] + "  " + dist + "m", color, -12);
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
                Render3D.outline(g, new AABB(s.pos().x - 0.3, s.pos().y, s.pos().z - 0.3,
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
        String entry = name.replaceAll("[,;]", " ") + "," + p.getX() + "," + p.getY() + "," + p.getZ() + ","
                + dimId(mc.level);
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

    // ---------- Einstellungs-Menues (stehen in RenderScreens.java) ----------

    public static Screen screen(String module, Screen parent) {
        return RenderScreens.screen(module, parent);
    }
}
