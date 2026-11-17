package de.test;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Tunnel Miner: graebt einfach geradeaus - gedacht fuer die Overworld (Strip Mining), geht aber ueberall.
 * <ul>
 * <li>Fester Blickwinkel: Er schaut nicht jeden Block einzeln an, sondern haelt wie ein Spieler die Maustaste
 * gedrueckt und behaelt einen festen Winkel. Minecraft baut dann den Block unter dem Fadenkreuz ab, einen nach
 * dem anderen; er geht nach, sobald vorne Platz ist.</li>
 * <li>1x2-Tunnel oder 3x3 mit einer 3x3-Spitzhacke (Server-Item, das beim Abbau des mittleren Blocks die
 * acht drumherum mitnimmt): dann zielt er waagerecht auf den mittleren Block jedes Abschnitts.</li>
 * <li>Lava und Wasser: seitlich ausweichen oder anhalten; Loecher im Boden zubauen.</li>
 * <li>Erze an Wand und Decke mitnehmen, Gestein wegwerfen, essen, Werkzeug schonen.</li>
 * <li>Auto-Log: bei Spielern in der Naehe, wenig Leben oder nach einem Totem die Verbindung trennen.</li>
 * </ul>
 */
public final class TunnelMiner {
    private TunnelMiner() {
    }

    // =====================================================================================
    // Einstellungen (oeffentliche Felder werden von TestConfig gespeichert, Prefix "tm.")
    // =====================================================================================

    // --- Tunnel ---
    public static int size = 0;                 // 0 = 1x2, 1 = 3x3 mit 3x3-Spitzhacke
    public static int direction = 0;            // 0 = Blickrichtung, 1 = Norden, 2 = Osten, 3 = Sueden, 4 = Westen
    public static int maxLength = 0;            // 0 = unbegrenzt
    public static int method = 0;               // 0 = fester Blickwinkel (Taste halten), 1 = Block fuer Block
    public static boolean cleanup = true;       // 3x3: was die Spitzhacke nicht mitnimmt, einzeln abbauen
    public static boolean fillFloor = true;     // Loecher im Boden zubauen
    public static int fluidMode = 0;            // 0 = ausweichen, 1 = anhalten
    public static boolean returnAxis = true;    // nach dem Ausweichen zurueck auf die Linie
    public static boolean humanize = true;      // weicher drehen, leichtes Zittern

    // --- Erze ---
    public static boolean ores = true;          // Erze an Wand und Decke mitnehmen
    public static int oreSet = 0;               // 0 = wertvolle, 1 = alle

    // --- Inventar ---
    public static boolean dropJunk = true;      // Gestein wegwerfen
    public static int keepBlocks = 1;           // so viele Stapel Baubloecke behalten (zum Zubauen)
    public static boolean autoEat = true;
    public static int eatHunger = 14;           // essen bei Hunger <= Wert (20 = satt)
    public static boolean keepGapples = true;

    // --- Werkzeug ---
    public static boolean toolGuard = true;
    public static int toolPercent = 5;          // Grenze in Prozent der Haltbarkeit
    public static boolean toolStop = true;      // anhalten, wenn keine brauchbare Spitzhacke mehr da ist

    // --- Auto-Log ---
    public static boolean playerLog = true;     // bei Spieler in der Naehe trennen
    public static int playerChunks = 6;         // in Chunks, 0 = ganze Sichtweite
    public static boolean useFriends = true;    // Freundesliste des Clients mitbenutzen
    public static String whitelist = "";        // weitere Freunde, mit Komma getrennt
    public static boolean playerCheckStart = true;
    public static boolean healthLog = true;     // bei wenig Leben trennen
    public static int logHearts = 4;            // bei so vielen Herzen (oder weniger)
    public static boolean totemLog = true;      // nach einem Totem trennen

    // --- Anzeige ---
    public static boolean showStatus = true;
    public static boolean highlight = true;

    static final String[] SIZES = {"1x2", "3x3 (3x3-Spitzhacke)"};
    static final String[] METHODS = {"Fester Blickwinkel", "Block für Block"};
    static final String[] ORE_SETS = {"Wertvolle", "Alle"};
    static final String[] FLUID_MODES = {"Ausweichen", "Anhalten"};
    private static final int[] DIR_X = {0, 0, 1, 0, -1};
    private static final int[] DIR_Z = {0, -1, 0, 1, 0};

    /** Gestein, das weg kann (Namen im Register). Baubloecke davon behaelt er stapelweise. */
    private static final Set<String> JUNK = Set.of("cobblestone", "cobbled_deepslate", "stone", "deepslate",
            "andesite", "diorite", "granite", "tuff", "dirt", "gravel", "netherrack", "calcite", "smooth_basalt",
            "basalt", "blackstone", "sand", "red_sand", "sandstone", "flint", "dripstone_block", "pointed_dripstone");

    private static final int LOOK_FIXED = 5;    // so weit reicht der feste Blick (Reichweite 4,5)
    private static final int RED = 0xC0FF4040;

    // =====================================================================================
    // Zustand
    // =====================================================================================

    private enum Phase { TUNNEL, EAT, COLLECT, RETURN }

    private static boolean running;
    private static boolean paused;
    private static Phase phase = Phase.TUNNEL;
    private static String action = "";
    private static long startedAt;
    private static int mainX;
    private static int mainZ;
    private static int curX;
    private static int curZ;
    private static int startX;
    private static int startZ;
    private static int tunnelY;
    private static int sideLeft;               // noch so viele Felder seitlich (Ausweichen)
    private static int sinceSide;
    private static int length;
    private static int oresMined;
    private static int idleTicks;
    private static int lastSolid = -1;
    private static BlockPos lastFeet;
    private static BlockPos lastGood;          // letztes Feld auf der Tunnelhoehe (fuer den Rueckweg)
    private static int hammerSlot = -1;
    private static String hammerName = "";
    private static BlockPos digPos;
    private static int digTicks;
    private static int digLimit;
    private static boolean oreDig;
    private static BlockPos lastHit;
    private static boolean attackedLast;
    private static int wait;
    private static int eatTicks;
    private static int eatRounds;
    private static int dropSlot = -1;
    private static int aimedTicks;
    private static int dropCooldown;
    private static int swapCooldown;
    private static int placeCooldown;
    private static int placeAimed;
    private static int pendingTotem = -1;
    private static int collectTicks;
    private static BlockPos collectSpot;
    private static BlockPos collectBack;
    private static int walkerFails;
    private static float stopJitter;
    private static boolean digging;
    private static boolean walkerTicked;
    private static final Set<Long> badBlocks = new HashSet<>();
    private static final boolean[] alerted = new boolean[9];

    private static boolean kFwd;
    private static boolean kBack;
    private static boolean kLeft;
    private static boolean kRight;
    private static boolean kSneak;
    private static boolean kAttack;
    private static boolean kUse;

    // =====================================================================================
    // An / Aus
    // =====================================================================================

    public static boolean isRunning() {
        return running;
    }

    /** Fuer den Mixin (ueber Walker.forceAttack): Angriffstaste gilt als gedrueckt, solange abgebaut wird. */
    static boolean forceAttack() {
        return running && digging;
    }

    static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (running) {
            stop(mc, "Tunnel Miner aus");
        } else {
            String err = start(mc);
            if (err != null && mc.player != null) say(mc, err);
        }
    }

    /** Startet den Tunnel Miner. Gibt eine Fehlermeldung zurueck oder null. */
    static String start(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return "Nicht im Spiel";
        if (!p.onGround()) return "Erst auf festen Boden stellen";
        if (playerLog && playerCheckStart) {
            Player other = nearbyPlayer(mc, p);
            if (other != null) return Lang.t("Nicht gestartet – Spieler in der Nähe: ") + other.getName().getString();
        }
        hammerSlot = -1;
        hammerName = "";
        if (size == 1) {
            // die 3x3-Spitzhacke ist die, die man beim Start in der Hand hat
            ItemStack held = p.getMainHandItem();
            if (!isPickaxe(held)) return "Nimm die 3x3-Spitzhacke in die Hand";
            hammerSlot = p.getInventory().getSelectedSlot();
            hammerName = held.getHoverName().getString();
        }
        DebrisMiner.stop(mc, null);
        if (TestClient.areaActive) TestClient.stopArea(mc, null);
        TestClient.walking = false;
        clearState();
        int d = Math.max(0, Math.min(4, direction));
        if (d == 0) {
            Direction f = Direction.fromYRot(p.getYRot());
            mainX = f.getStepX();
            mainZ = f.getStepZ();
        } else {
            mainX = DIR_X[d];
            mainZ = DIR_Z[d];
        }
        curX = mainX;
        curZ = mainZ;
        BlockPos feet = feet(p);
        startX = feet.getX();
        startZ = feet.getZ();
        tunnelY = feet.getY();
        lastGood = feet;
        running = true;
        startedAt = System.currentTimeMillis();
        phase = Phase.TUNNEL;
        action = "Gräbt Tunnel";
        say(mc, "Tunnel Miner gestartet: Richtung " + Lang.t(compass(mainX, mainZ)) + ", " + Lang.t(SIZES[size == 1 ? 1 : 0])
                + ", Höhe " + tunnelY);
        return null;
    }

    /** Beendet den Tunnel Miner (message = Chat-Meldung oder null). */
    static void stop(Minecraft mc, String message) {
        if (!running) return;
        running = false;
        Look.human = false;
        if (Walker.isActive() && !TestClient.areaActive) Walker.stop(mc);
        clearKeys();
        applyKeys(mc);
        digging = false;
        if (mc.player != null && message != null) say(mc, message);
    }

    /** Welt verlassen: ohne Meldung aus. */
    static void reset() {
        running = false;
        digging = false;
    }

    private static void clearState() {
        sideLeft = 0;
        sinceSide = 0;
        length = 0;
        oresMined = 0;
        idleTicks = 0;
        lastSolid = -1;
        lastFeet = null;
        digPos = null;
        oreDig = false;
        lastHit = null;
        attackedLast = false;
        wait = 0;
        dropSlot = -1;
        aimedTicks = 0;
        dropCooldown = 0;
        swapCooldown = 0;
        placeCooldown = 0;
        placeAimed = 0;
        pendingTotem = -1;
        collectTicks = 0;
        collectSpot = null;
        collectBack = null;
        walkerFails = 0;
        digging = false;
        paused = false;
        badBlocks.clear();
        java.util.Arrays.fill(alerted, false);
        newStopJitter();
    }

    /** Kurzer Zustand fuer das Menue. */
    static String status() {
        if (!running) return Lang.t("Bereit");
        return Lang.t(action) + "  ·  " + Lang.t("Länge: ") + length + "  ·  " + Lang.t("Erze: ") + oresMined;
    }

    // =====================================================================================
    // Tick
    // =====================================================================================

    static void tick(Minecraft mc) {
        if (!running) return;
        Look.human = humanize;
        LocalPlayer p = mc.player;
        Level level = mc.level;
        if (p == null || level == null) {
            reset();
            return;
        }
        try {
            if (guard(mc, p)) return;
            if (BotGuard.fighting()) {
                pause(mc);
                action = "Wehrt einen Mob ab";
                return;
            }
            if (mc.gui.screen() != null) {
                pause(mc);
                return;
            }
            paused = false;
            clearKeys();
            digging = false;
            walkerTicked = false;
            try {
                run(mc, level, p);
            } finally {
                if (running && !walkerTicked) applyKeys(mc);
                attackedLast = kAttack;
            }
        } catch (RuntimeException | LinkageError e) {
            Safe.report("Tunnel Miner", e);
            stop(mc, "Tunnel Miner angehalten (Fehler)");
        }
    }

    private static void pause(Minecraft mc) {
        if (paused) return;
        paused = true;
        clearKeys();
        applyKeys(mc);
        digging = false;
        if (Walker.isActive()) Walker.release(mc);
    }

    private static void run(Minecraft mc, Level level, LocalPlayer p) {
        if (dropCooldown > 0) dropCooldown--;
        if (swapCooldown > 0) swapCooldown--;
        if (placeCooldown > 0) placeCooldown--;
        toolAlerts(mc, p);
        switch (phase) {
            case TUNNEL -> tunnel(mc, level, p);
            case EAT -> eat(p);
            case COLLECT -> collect(p);
            case RETURN -> returnToLine(mc, level, p);
        }
    }

    // =====================================================================================
    // Auto-Log
    // =====================================================================================

    /** true = beendet (Verbindung getrennt). */
    private static boolean guard(Minecraft mc, LocalPlayer p) {
        if (pendingTotem >= 0) {
            if (pendingTotem == 0) {
                pendingTotem = -1;
                disconnect(mc, "Totem ausgelöst");
                return true;
            }
            pendingTotem--;
        }
        if (playerLog) {
            Player other = nearbyPlayer(mc, p);
            if (other != null) {
                disconnect(mc, Lang.t("Spieler in der Nähe: ") + other.getName().getString());
                return true;
            }
        }
        float hp = p.getHealth();
        if (healthLog && hp > 0.0f && hp <= logHearts * 2.0f) {
            disconnect(mc, Lang.t("Wenig Leben: ") + String.format(Locale.ROOT, "%.1f", hp / 2.0f) + " ❤");
            return true;
        }
        return false;
    }

    /** Naechster fremder Spieler im Radius (Freunde zaehlen nicht). */
    private static Player nearbyPlayer(Minecraft mc, LocalPlayer self) {
        Set<String> friends = new HashSet<>();
        for (String s : whitelist.split(",")) {
            String n = s.strip().toLowerCase(Locale.ROOT);
            if (!n.isEmpty()) friends.add(n);
        }
        for (Player o : mc.level.players()) {
            if (o == self || o.getId() < 0 || PlayerMods.isFake(o) || !o.isAlive()) continue;
            if (friends.contains(o.getName().getString().toLowerCase(Locale.ROOT))) continue;
            if (useFriends && Friends.is(o)) continue;
            if (playerChunks > 0 && o.distanceTo(self) > playerChunks * 16) continue;
            return o;
        }
        return null;
    }

    /** Vom RenderMods (Ereignis 35): ein Totem wurde ausgeloest. */
    static void onTotemPop(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        if (!running || !totemLog || mc.player == null || e != mc.player) return;
        pendingTotem = 0;
    }

    private static void disconnect(Minecraft mc, String reason) {
        stop(mc, null);
        MiscMods.holdReconnect = true; // Auto Reconnect soll nicht gleich wieder verbinden
        var conn = mc.getConnection();
        if (conn != null) {
            conn.getConnection().disconnect(Lang.literal("1912: Tunnel Miner – " + Lang.t(reason)));
        }
    }

    // =====================================================================================
    // Tunnel
    // =====================================================================================

    private static void tunnel(Minecraft mc, Level level, LocalPlayer p) {
        BlockPos feet = feet(p);
        if (!p.onGround() && !p.isInWater()) return; // faellt oder rutscht gerade
        // vom Weg abgekommen (in ein Loch gefallen, von einem Mob weggestossen)? Erst zurueck auf die Linie
        if (feet.getY() != tunnelY || !onCorridor(feet) || p.isInWater()) {
            phase = Phase.RETURN;
            walkerFails = 0;
            digPos = null;
            return;
        }
        progress(feet);
        if (maxLength > 0 && length >= maxLength) {
            stop(mc, Lang.t("Tunnel fertig: ") + length + Lang.t(" Blöcke"));
            return;
        }
        if (++idleTicks > 900) {
            stop(mc, "Kommt nicht weiter – Tunnel Miner angehalten");
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        // 1. Essen
        if (needEat(p)) {
            phase = Phase.EAT;
            eatTicks = 0;
            eatRounds = 0;
            digPos = null;
            return;
        }
        // 2. Werkzeug
        if (!toolReady(mc, p)) return;
        // 3. Gestein wegwerfen
        if (throwJunk(p)) return;
        if (freeSlots(p) == 0 && junkSlot(p, true) < 0) {
            stop(mc, "Inventar voll – Tunnel Miner angehalten");
            return;
        }
        // 4. Erze an Wand und Decke
        if (ores && oreTick(mc, level, p, feet)) return;
        // 5. Ausweichen fertig / zurueck auf die Linie
        if (sideLeft == 0 && (curX != mainX || curZ != mainZ)) {
            curX = mainX;
            curZ = mainZ;
            sinceSide = 0;
        }
        if (returnAxis && sideLeft == 0 && lateral(feet) != 0 && sinceSide > look() + 2) tryReturn(level, feet);
        // 6. Lava oder Wasser voraus (im Ausweich-Gang nur bis zu dessen Ende)
        if (fluidAhead(level, feet, curX, curZ, reachAhead(feet)) > 0) {
            hazard(mc, level, feet, "Lava oder Wasser voraus");
            return;
        }
        // 7. Abbauen und weitergehen
        advance(mc, level, p, feet);
    }

    /** Zaehlt Schritte und Laenge, merkt sich das letzte gute Feld. */
    private static void progress(BlockPos feet) {
        if (lastFeet != null && !feet.equals(lastFeet)) {
            idleTicks = 0;
            if (curX != mainX || curZ != mainZ) {
                if (feet.equals(lastFeet.offset(curX, 0, curZ)) && sideLeft > 0) sideLeft--;
            } else {
                sinceSide++;
            }
            int along = (feet.getX() - startX) * mainX + (feet.getZ() - startZ) * mainZ;
            if (along > length) length = along;
        }
        lastFeet = feet;
        if (feet.getY() == tunnelY) lastGood = feet;
    }

    /** Seitlicher Abstand von der Startlinie (in Bloecken, quer zur Hauptrichtung). */
    private static int lateral(BlockPos feet) {
        return (feet.getX() - startX) * -mainZ + (feet.getZ() - startZ) * mainX;
    }

    /** Beim Ausweichen darf er bis zu so weit neben der Linie sein. */
    private static int sideSlack() {
        return size == 1 ? 6 : 5;
    }

    /** Steht er im Tunnel (auf der Linie, im Ausweich-Gang oder im 3x3-Querschnitt)? */
    private static boolean onCorridor(BlockPos feet) {
        return Math.abs(lateral(feet)) <= sideSlack();
    }

    private static int look() {
        return method == 0 ? LOOK_FIXED : 2;
    }

    /**
     * So viele Felder voraus darf er aufmachen: im Ausweich-Gang nur bis zu dessen Ende, auf der Linie nur bis
     * zur eingestellten Laenge (sonst wuerde der feste Blick ueber das Ende hinaus weitergraben).
     */
    private static int reachAhead(BlockPos feet) {
        int n = look();
        if (curX != mainX || curZ != mainZ) return Math.min(n, sideLeft);
        if (maxLength > 0) {
            int along = (feet.getX() - startX) * mainX + (feet.getZ() - startZ) * mainZ;
            n = Math.min(n, Math.max(0, maxLength - along));
        }
        return n;
    }

    // ---------- Abbauen und gehen ----------

    private static void advance(Minecraft mc, Level level, LocalPlayer p, BlockPos feet) {
        BlockPos next = feet.offset(curX, 0, curZ);
        action = curX != mainX || curZ != mainZ ? "Weicht aus" : "Gräbt Tunnel";
        int solidNow = solidAhead(level, feet);
        if (lastSolid >= 0 && solidNow < lastSolid) idleTicks = 0;
        lastSolid = solidNow;

        // laufendes Einzel-Abbauen erst fertig machen
        if (digPos != null && !oreDig) {
            if (!solid(level, digPos)) {
                digPos = null;
                wait = humanize ? Look.rng().nextInt(3) : 0;
                return;
            }
            if (!mineBlock(mc, level, p, digPos)) {
                badBlocks.add(digPos.asLong());
                digPos = null;
            }
            return;
        }

        boolean body = passable(level, next) && passable(level, next.above());
        // Loch im Boden (vor ihm oder - an der Kante stehend - schon unter ihm): zubauen oder ausweichen
        BlockPos hole = !solid(level, feet.below()) ? feet.below() : body && !solid(level, next.below()) ? next.below() : null;
        if (hole != null) {
            if (!fillFloor) {
                hazard(mc, level, feet, "Loch im Boden");
                return;
            }
            action = "Baut den Boden zu";
            int r = placeInto(mc, level, p, hole);
            if (r == PLACE_NO_BLOCKS && !buildBlocksToHotbar(p)) {
                stop(mc, "Keine Baublöcke zum Zubauen – Tunnel Miner angehalten");
            } else if (r == PLACE_NO_FACE) {
                // keine Seite zum Anklicken sichtbar (z.B. ueber einer Hoehle): geduckt an die Kante, dann
                // sieht er die Seite des Blocks, auf dem er steht - wie ein Spieler beim Bruecken bauen
                Look.turn(p, Look.yawOf(curX, curZ), 70.0f, 20.0f, 20.0f, 0.0f);
                double yawR = Math.toRadians(p.getYRot());
                kFwd = -curX * Math.sin(yawR) + curZ * Math.cos(yawR) > 0.3;
                kSneak = true;
            }
            return;
        }
        // 3x3: was die Spitzhacke nicht mitgenommen hat, einzeln abbauen - mit einer normalen Spitzhacke
        // (mit der 3x3-Spitzhacke wuerde jeder Rest wieder 3x3 aufreissen, auch in Wand und Boden)
        if (body && size == 1 && cleanup && hasOtherPick(p)) {
            BlockPos rest = firstSolid(level, next, true);
            if (rest != null) {
                startDig(mc, level, p, rest);
                return;
            }
        }
        if (method == 0 && !Float.isNaN(fixedPitch(p)) && fixedTick(level, p, feet)) return;
        // Block fuer Block: zuerst das naechste Feld frei machen, dann hingehen
        if (!body) {
            BlockPos t = firstSolid(level, next, false);
            if (t != null) {
                startDig(mc, level, p, t);
                return;
            }
        }
        moveTo(p, next, Look.yawOf(curX, curZ));
    }

    private static void startDig(Minecraft mc, Level level, LocalPlayer p, BlockPos t) {
        if (badBlocks.contains(t.asLong()) || !safeToBreak(level, t)) {
            hazard(mc, level, feet(p), "Block lässt sich nicht abbauen");
            return;
        }
        digPos = t;
        oreDig = false;
        digTicks = 0;
        if (!mineBlock(mc, level, p, t)) {
            badBlocks.add(t.asLong());
            digPos = null;
        }
    }

    /**
     * Fester Blickwinkel: Taste gedrueckt halten, Blick bleibt, wie er ist. 1x2: 19 Grad nach unten (bei
     * Reichweite 4,5) - dann trifft das Fadenkreuz erst den Kopfblock, danach durch das Loch den Fussblock und
     * die Fussbloecke weiter vorne, aber nie den Boden. 3x3: waagerecht auf die mittlere Reihe.
     * false = geht so nicht (zu nah an der Wand o.ae.), dann Block fuer Block.
     */
    private static boolean fixedTick(Level level, LocalPlayer p, BlockPos feet) {
        float pitch = fixedPitch(p);
        double reach = p.blockInteractionRange();
        BlockPos next = feet.offset(curX, 0, curZ);
        boolean body = passable(level, next) && passable(level, next.above());
        double a = wallDistance(level, p, feet);
        double stop = (size == 1 ? 0.9 : 1.25) + stopJitter;
        // 1x2: ganz nah an der Wand erreicht der flache Blick den Fussblock nicht mehr
        if (!body && size == 0 && a < 1.0) return false;

        double tx = curX != 0 ? p.getX() + curX * 4.0 : feet.getX() + 0.5;
        double tz = curZ != 0 ? p.getZ() + curZ * 4.0 : feet.getZ() + 0.5;
        float yaw = Look.yawOf(tx - p.getX(), tz - p.getZ());
        if (attackedLast) Look.holdSteady(); // waehrend des Abbaus die Maus ruhig halten
        Look.turn(p, yaw, pitch, 30.0f, 25.0f, 0.0f);
        float ey = Math.abs(wrapDeg(yaw - p.getYRot()));
        float ep = Math.abs(pitch - p.getXRot());
        if (ey > 3.0f || ep > 1.5f) return true; // erst fertig drehen, sonst trifft er etwas Falsches

        // gehen, solange die Wand weit genug weg ist (und der Boden vorne traegt)
        kFwd = a > stop && (!body || solid(level, next.below()));
        if (kFwd && a > stop + 0.6) newStopJitter();
        BlockPos h = rayHit(level, p, reach);
        if (h == null) {
            return kFwd || body; // nichts in Reichweite: weitergehen (oder Block fuer Block)
        }
        if (!allowed(level, p, feet, h)) {
            kAttack = false;
            return kFwd;
        }
        if (!h.equals(lastHit)) {
            selectTool(level, p, h);
            lastHit = h;
        }
        kAttack = true;
        digging = true;
        return true;
    }

    private static void newStopJitter() {
        stopJitter = humanize ? (float) (Look.rng().nextDouble() * 0.25) : 0.0f;
    }

    /** Fester Blickwinkel; NaN = geht nicht (sehr grosse Reichweite). */
    private static float fixedPitch(LocalPlayer p) {
        if (size == 1) return 0.0f; // 3x3: waagerecht, das Auge liegt in der mittleren Reihe
        double eyeH = p.getEyeY() - p.getY();
        double reach = p.blockInteractionRange();
        if (reach <= 0.0 || eyeH <= 1.2) return Float.NaN;
        double floorLimit = Math.toDegrees(Math.asin(Math.min(1.0, eyeH / reach)));
        float pitch = (float) Math.min(19.0, floorLimit - 1.8);
        return pitch < 14.0f ? Float.NaN : pitch;
    }

    /** Erster Block, auf den das Fadenkreuz gerade zeigt (oder null). */
    private static BlockPos rayHit(Level level, LocalPlayer p, double reach) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(reach));
        BlockHitResult r = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        if (r == null || r.getType() != HitResult.Type.BLOCK) return null;
        return r.getBlockPos();
    }

    /** Liegt h im Tunnelquerschnitt vor ihm (nie Boden, Decke oder Wand) und darf weg? */
    private static boolean allowed(Level level, LocalPlayer p, BlockPos feet, BlockPos h) {
        int dx = h.getX() - feet.getX();
        int dz = h.getZ() - feet.getZ();
        int along = dx * curX + dz * curZ;
        if (along < 0 || along > reachAhead(feet)) return false;
        if (!inSection(feet, h)) return false;
        if (badBlocks.contains(h.asLong())) return false;
        BlockState bs = level.getBlockState(h);
        return bs.getDestroySpeed(level, h) >= 0.0f && safeToBreak(level, h);
    }

    /** Gehoert h zum Tunnelquerschnitt der Linie, auf der er gerade steht (Richtung cur)? */
    private static boolean inSection(BlockPos feet, BlockPos h) {
        int dx = h.getX() - feet.getX();
        int dz = h.getZ() - feet.getZ();
        int side = dx * -curZ + dz * curX;
        int up = h.getY() - tunnelY;
        if (size == 1) return Math.abs(side) <= 1 && up >= 0 && up <= 2;
        return side == 0 && (up == 0 || up == 1);
    }

    /** Abstand vom Auge bis zur ersten festen Wand (Fuss- oder Kopfhoehe) voraus; gross, wenn frei. */
    private static double wallDistance(Level level, LocalPlayer p, BlockPos feet) {
        for (int k = 1; k <= LOOK_FIXED + 1; k++) {
            BlockPos base = feet.offset(curX * k, 0, curZ * k);
            if (!solid(level, base) && !solid(level, base.above())) continue;
            double pos = curX != 0 ? p.getX() : p.getZ();
            int dir = curX != 0 ? curX : curZ;
            int b = curX != 0 ? base.getX() : base.getZ();
            double face = dir > 0 ? b : b + 1;
            return (face - pos) * dir;
        }
        return 99.0;
    }

    /** Anzahl fester Bloecke im Tunnel voraus (fuer "kommt voran?"). */
    private static int solidAhead(Level level, BlockPos feet) {
        int n = 0;
        for (int k = 1; k <= LOOK_FIXED; k++) {
            for (BlockPos c : section(feet.offset(curX * k, 0, curZ * k), curX, curZ)) {
                if (solid(level, c)) n++;
            }
        }
        return n;
    }

    /** Querschnitt an der Stelle base: 1x2 = Kopf, Fuss; 3x3 = Mitte zuerst, dann der Rest. */
    private static java.util.List<BlockPos> section(BlockPos base, int dx, int dz) {
        java.util.List<BlockPos> list = new java.util.ArrayList<>();
        if (size == 1) {
            int px = -dz;
            int pz = dx;
            list.add(base.above());
            for (int up = 2; up >= 0; up--) {
                for (int s = -1; s <= 1; s++) {
                    BlockPos c = base.offset(px * s, up, pz * s);
                    if (!c.equals(base.above())) list.add(c);
                }
            }
        } else {
            list.add(base.above());
            list.add(base);
        }
        return list;
    }

    /** Erster fester Block im Querschnitt bei base (rest = ohne die Mitte, die die 3x3-Spitzhacke trifft). */
    private static BlockPos firstSolid(Level level, BlockPos base, boolean rest) {
        for (BlockPos c : section(base, curX, curZ)) {
            if (rest && c.equals(base.above())) continue;
            if (solid(level, c) && !badBlocks.contains(c.asLong())) return c;
        }
        // 3x3: Kopf- und Fussblock in der Mitte muessen auf jeden Fall frei sein
        if (rest) return null;
        for (BlockPos c : new BlockPos[] {base.above(), base}) {
            if (solid(level, c)) return c;
        }
        return null;
    }

    // ---------- Lava, Wasser, Loecher ----------

    /** Erstes Feld (1..dist) voraus, an dessen Querschnitt Lava oder Wasser liegt; 0 = keins. */
    private static int fluidAhead(Level level, BlockPos from, int dx, int dz, int dist) {
        for (int k = 1; k <= dist; k++) {
            BlockPos base = from.offset(dx * k, 0, dz * k);
            for (BlockPos c : section(base, dx, dz)) {
                if (fluid(level, c)) return k;
                for (Direction d : Direction.values()) {
                    if (fluid(level, c.relative(d))) return k;
                }
            }
            if (fluid(level, base.below())) return k;
        }
        return 0;
    }

    private static void hazard(Minecraft mc, Level level, BlockPos feet, String what) {
        digPos = null;
        if (fluidMode == 1 || curX != mainX || curZ != mainZ) {
            stop(mc, Lang.t(what) + Lang.t(" – Tunnel Miner angehalten"));
            return;
        }
        int shift = size == 1 ? 4 : 3;
        int px = -mainZ;
        int pz = mainX;
        int lat = lateral(feet);
        // zuerst zur Linie hin ausweichen (dann ist der Rueckweg kurz), sonst die andere Seite
        int[] order = lat > 0 ? new int[] {-1, 1} : new int[] {1, -1};
        for (int s : order) {
            int sx = px * s;
            int sz = pz * s;
            if (Math.abs(lat + s * shift) > sideSlack()) continue;
            BlockPos end = feet.offset(sx * shift, 0, sz * shift);
            if (fluidAhead(level, feet, sx, sz, shift + 1) == 0 && fluidAhead(level, end, mainX, mainZ, look()) == 0) {
                curX = sx;
                curZ = sz;
                sideLeft = shift;
                sinceSide = 0;
                say(mc, Lang.t(what) + Lang.t(" – weicht seitlich aus"));
                return;
            }
        }
        stop(mc, Lang.t(what) + Lang.t(" – kein Weg drumherum, Tunnel Miner angehalten"));
    }

    /** Nach dem Ausweichen zurueck auf die Startlinie, wenn der Weg dorthin sicher ist. */
    private static void tryReturn(Level level, BlockPos feet) {
        int lat = lateral(feet);
        int s = lat > 0 ? -1 : 1;
        int sx = -mainZ * s;
        int sz = mainX * s;
        int n = Math.abs(lat);
        BlockPos end = feet.offset(sx * n, 0, sz * n);
        if (fluidAhead(level, feet, sx, sz, n + 1) == 0 && fluidAhead(level, end, mainX, mainZ, look()) == 0) {
            curX = sx;
            curZ = sz;
            sideLeft = n;
            sinceSide = 0;
        } else {
            sinceSide = 0; // spaeter nochmal versuchen
        }
    }

    // ---------- Erze ----------

    private static boolean isOre(BlockState bs) {
        String id = BuiltInRegistries.BLOCK.getKey(bs.getBlock()).getPath();
        if (!id.endsWith("_ore") && !id.equals("ancient_debris")) return false;
        if (oreSet == 1) return true;
        if (id.startsWith("nether_")) return false;
        return id.contains("diamond") || id.contains("emerald") || id.contains("gold") || id.contains("iron")
                || id.contains("lapis") || id.contains("redstone") || id.equals("ancient_debris");
    }

    /** Sichtbare Erze direkt an Wand und Decke (nicht im Boden, sonst entsteht ein Loch). true = beschaeftigt. */
    private static boolean oreTick(Minecraft mc, Level level, LocalPlayer p, BlockPos feet) {
        if (digPos != null && oreDig) {
            if (!solid(level, digPos)) {
                oresMined++;
                idleTicks = 0;
                BlockPos was = digPos;
                digPos = null;
                oreDig = false;
                wait = humanize ? 2 + Look.rng().nextInt(4) : 2;
                // 3x3: zwei Bloecke neben der Mitte faellt das Item nicht in Reichweite - kurz hingehen
                int lat = (was.getX() - feet.getX()) * -curZ + (was.getZ() - feet.getZ()) * curX;
                int k = (was.getX() - feet.getX()) * curX + (was.getZ() - feet.getZ()) * curZ;
                if (size == 1 && Math.abs(lat) >= 2 && was.getY() - tunnelY <= 2) {
                    int s = lat > 0 ? 1 : -1;
                    collectSpot = feet.offset(curX * k - curZ * s, 0, curZ * k + curX * s);
                    collectBack = feet;
                    collectTicks = 0;
                    phase = Phase.COLLECT;
                }
                return true;
            }
            if (!mineBlock(mc, level, p, digPos)) {
                badBlocks.add(digPos.asLong());
                digPos = null;
                oreDig = false;
            }
            return true;
        }
        if (digPos != null) return false;
        double reach = p.blockInteractionRange();
        BlockPos best = null;
        double bestD = reach * reach;
        for (int k = -1; k <= 3; k++) {
            BlockPos base = feet.offset(curX * k, 0, curZ * k);
            for (BlockPos s : section(base, curX, curZ)) {
                if (!passable(level, s)) continue; // nur vom schon offenen Tunnel aus
                for (Direction d : Direction.values()) {
                    BlockPos n = s.relative(d);
                    if (n.getY() < tunnelY) continue; // Boden: lieber kein Loch
                    if (inSection(feet, n) || badBlocks.contains(n.asLong())) continue;
                    // 3x3: Erze zwei neben der Mitte erst, wenn er auf ihrer Hoehe steht (dann kann er das Item holen)
                    int lat = (n.getX() - feet.getX()) * -curZ + (n.getZ() - feet.getZ()) * curX;
                    int kk = (n.getX() - feet.getX()) * curX + (n.getZ() - feet.getZ()) * curZ;
                    if (size == 1 && Math.abs(lat) >= 2 && (kk > 0 || kk < -1)) continue;
                    BlockState bs = level.getBlockState(n);
                    if (!isOre(bs) || !safeToBreak(level, n)) continue;
                    double dd = Look.eyeDist2(p, n);
                    if (dd < bestD && TestClient.visiblePoint(level, p, n) != null) {
                        bestD = dd;
                        best = n;
                    }
                }
            }
        }
        if (best == null) return false;
        action = "Baut Erz ab";
        digPos = best;
        oreDig = true;
        digTicks = 0;
        if (!mineBlock(mc, level, p, best)) {
            badBlocks.add(best.asLong());
            digPos = null;
            oreDig = false;
        }
        return true;
    }

    private static void collect(LocalPlayer p) {
        action = "Sammelt ein";
        collectTicks++;
        if (collectTicks < 30 && collectSpot != null) {
            moveTo(p, collectSpot, Look.yawOf(curX, curZ));
            return;
        }
        if (collectBack == null || moveTo(p, collectBack, Look.yawOf(curX, curZ)) || collectTicks > 90) {
            phase = Phase.TUNNEL;
            collectSpot = null;
            collectBack = null;
        }
    }

    // ---------- Rueckweg ----------

    private static void returnToLine(Minecraft mc, Level level, LocalPlayer p) {
        action = "Kehrt zum Tunnel zurück";
        BlockPos feet = feet(p);
        if (feet.getY() == tunnelY && onCorridor(feet) && p.onGround()) {
            if (Walker.isActive()) Walker.stop(mc);
            phase = Phase.TUNNEL;
            lastFeet = feet;
            return;
        }
        if (!Walker.isActive()) {
            if (++walkerFails > 3) {
                stop(mc, "Rückweg zum Tunnel nicht gefunden – Tunnel Miner angehalten");
                return;
            }
            Walker.startDigExact(lastGood.getX(), lastGood.getY(), lastGood.getZ());
        }
        walkerTicked = true;
        String r;
        try {
            r = Walker.tick(mc, level, p);
        } catch (RuntimeException | LinkageError e) {
            Safe.report("Laufen", e);
            r = "Fehler";
        }
        if (r != null && Walker.isActive()) Walker.stop(mc);
    }

    // =====================================================================================
    // Abbauen (Block fuer Block), Werkzeug
    // =====================================================================================

    /** Einen Block gezielt abbauen (Blick auf eine sichtbare Stelle). false = geht nicht. */
    private static boolean mineBlock(Minecraft mc, Level level, LocalPlayer p, BlockPos pos) {
        BlockState bs = level.getBlockState(pos);
        float hardness = bs.getDestroySpeed(level, pos);
        if (hardness < 0.0f) return false;
        if (++digTicks == 1) {
            selectTool(level, p, pos);
            ItemStack st = p.getMainHandItem();
            digLimit = (int) Math.min(1500.0, ticksWith(st, bs, hardness) * 2.5 + 80.0);
        }
        if (digTicks > digLimit) return false;
        double[] pt = TestClient.visiblePoint(level, p, pos);
        if (pt == null) {
            if (digTicks > 40) return false;
            Look.aim(p, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4.0f);
            return true;
        }
        if (digTicks > 1) Look.holdSteady();
        boolean aimed = Look.aim(p, pt[0], pt[1], pt[2], 4.0f);
        boolean onTarget = false;
        HitResult hr = mc.hitResult;
        if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK) {
            onTarget = bh.getBlockPos().equals(pos);
        }
        kAttack = aimed && onTarget;
        digging = kAttack;
        return true;
    }

    /** Werkzeug fuer den Block: 3x3 - die Mitte eines Abschnitts mit der 3x3-Spitzhacke, sonst das schnellste. */
    private static void selectTool(Level level, LocalPlayer p, BlockPos pos) {
        var inv = p.getInventory();
        if (size == 1 && hammerSlot >= 0) {
            boolean center = inSection(feet(p), pos) && pos.getY() == tunnelY + 1
                    && (pos.getX() - feet(p).getX()) * -curZ + (pos.getZ() - feet(p).getZ()) * curX == 0;
            if (center) {
                inv.setSelectedSlot(hammerSlot);
                return;
            }
        }
        BlockState bs = level.getBlockState(pos);
        float hardness = bs.getDestroySpeed(level, pos);
        int best = -1;
        double bestT = Double.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            if (size == 1 && i == hammerSlot) continue; // Reste nicht mit der 3x3-Spitzhacke (die nimmt mehr mit)
            ItemStack st = inv.getItem(i);
            if (toolGuard && worn(st)) continue;
            double t = ticksWith(st, bs, hardness);
            if (t < bestT - 0.5) {
                bestT = t;
                best = i;
            }
        }
        if (size == 1 && hammerSlot >= 0) {
            // keine zweite brauchbare Spitzhacke: dann doch die 3x3-Spitzhacke (sonst dauert es ewig)
            double h = ticksWith(inv.getItem(hammerSlot), bs, hardness);
            if (best < 0 || bestT > h * 3.0 + 2.0) best = hammerSlot;
        }
        if (best >= 0) inv.setSelectedSlot(best);
    }

    private static double ticksWith(ItemStack st, BlockState bs, float hardness) {
        if (hardness <= 0.0f) return 1.0;
        float speed = st.getDestroySpeed(bs);
        boolean right = !bs.requiresCorrectToolForDrops() || st.isCorrectToolForDrops(bs);
        return Math.ceil(hardness * (right ? 30.0 : 100.0) / Math.max(0.1f, speed));
    }

    /** 3x3: noch eine brauchbare normale Spitzhacke in der Hotbar? */
    private static boolean hasOtherPick(LocalPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            if (i != hammerSlot && isPickaxe(st) && !(toolGuard && worn(st))) return true;
        }
        return false;
    }

    private static boolean isPickaxe(ItemStack st) {
        return !st.isEmpty() && st.isCorrectToolForDrops(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
    }

    private static boolean worn(ItemStack st) {
        if (!st.isDamageableItem()) return false;
        int max = Math.max(1, st.getMaxDamage());
        int left = max - st.getDamageValue();
        return left * 100 <= toolPercent * max;
    }

    /**
     * Hat er eine brauchbare Spitzhacke? 3x3: die vom Start (auch wenn sie in einen anderen Hotbar-Platz
     * gewandert ist). Sonst eine aus dem Inventar in die Hotbar holen. false = Tick vorbei.
     */
    private static boolean toolReady(Minecraft mc, LocalPlayer p) {
        var inv = p.getInventory();
        if (size == 1) {
            ItemStack h = hammerSlot >= 0 ? inv.getItem(hammerSlot) : ItemStack.EMPTY;
            if (!isPickaxe(h) || !h.getHoverName().getString().equals(hammerName)) {
                hammerSlot = -1;
                for (int i = 0; i < 9 && hammerSlot < 0; i++) {
                    ItemStack st = inv.getItem(i);
                    if (isPickaxe(st) && st.getHoverName().getString().equals(hammerName)) hammerSlot = i;
                }
                if (hammerSlot < 0) {
                    stop(mc, "3x3-Spitzhacke nicht mehr in der Hotbar – Tunnel Miner angehalten");
                    return false;
                }
            }
            if (toolGuard && toolStop && worn(inv.getItem(hammerSlot))) {
                stop(mc, "3x3-Spitzhacke fast kaputt – Tunnel Miner angehalten");
                return false;
            }
            return true;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            if (isPickaxe(st) && !(toolGuard && worn(st))) return true;
        }
        for (int i = 9; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (isPickaxe(st) && !(toolGuard && worn(st))) {
                if (swapCooldown == 0) {
                    PlayerMods.click(p, i, freeHotbarSlot(p), "SWAP");
                    swapCooldown = 5;
                    say(mc, "Neue Spitzhacke aus dem Inventar genommen");
                }
                return false;
            }
        }
        if (toolStop) {
            stop(mc, "Keine brauchbare Spitzhacke mehr – Tunnel Miner angehalten");
            return false;
        }
        return true;
    }

    /** Einmalige Warnung, wenn eine Spitzhacke in der Hotbar die Grenze erreicht. */
    private static void toolAlerts(Minecraft mc, LocalPlayer p) {
        if (!toolGuard) return;
        var inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            boolean bad = isPickaxe(st) && worn(st);
            if (bad && !alerted[i]) {
                alerted[i] = true;
                String text = "Spitzhacke fast kaputt (" + (st.getMaxDamage() - st.getDamageValue()) + "/"
                        + st.getMaxDamage() + ")";
                say(mc, text);
                Keybinds.message(text, "Tunnel Miner");
            } else if (!bad) {
                alerted[i] = false;
            }
        }
    }

    /** Hotbar-Platz fuer Nachschub: leer, Gestein, sonst etwas Unwichtiges. */
    private static int freeHotbarSlot(LocalPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).isEmpty()) return i;
        }
        for (int i = 8; i >= 0; i--) {
            if (isJunk(inv.getItem(i)) && i != hammerSlot) return i;
        }
        for (int i = 8; i >= 0; i--) {
            ItemStack st = inv.getItem(i);
            if (i != hammerSlot && !isPickaxe(st) && !isFood(st) && !st.is(Items.TOTEM_OF_UNDYING)) return i;
        }
        return inv.getSelectedSlot();
    }

    // =====================================================================================
    // Bauen (Loecher im Boden)
    // =====================================================================================

    private static final int PLACE_BUSY = 0;
    private static final int PLACE_NO_BLOCKS = 1;
    private static final int PLACE_NO_FACE = 2;

    /** Baublock nach t setzen: BUSY = zielt oder gesetzt, NO_BLOCKS = keine in der Hotbar, NO_FACE = nichts sichtbar. */
    private static int placeInto(Minecraft mc, Level level, LocalPlayer p, BlockPos t) {
        AABB cell = new AABB(t.getX(), t.getY(), t.getZ(), t.getX() + 1, t.getY() + 1, t.getZ() + 1);
        if (p.getBoundingBox().intersects(cell)) return PLACE_BUSY;
        if (TestClient.findBuildSlot(mc) < 0) return PLACE_NO_BLOCKS;
        Object[] f = findFace(level, p, t);
        if (f == null) return PLACE_NO_FACE;
        Vec3 hit = (Vec3) f[2];
        kSneak = true; // an der Kante nicht hinunterfallen
        if (!Look.aim(p, hit.x, hit.y, hit.z, 5.0f)) {
            placeAimed = 0;
            return PLACE_BUSY;
        }
        if (++placeAimed < 2 || placeCooldown > 0) return PLACE_BUSY;
        placeAimed = 0;
        placeCooldown = 4;
        TestClient.placeBlock(mc, (BlockPos) f[0], (Direction) f[1], hit, t);
        return PLACE_BUSY;
    }

    /** Baubloecke aus dem Hauptinventar in die Hotbar holen. true = welche gefunden. */
    private static boolean buildBlocksToHotbar(LocalPlayer p) {
        var inv = p.getInventory();
        for (int i = 9; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (st.getItem() instanceof BlockItem bi && TestClient.isBuildBlock(bi.getBlock())) {
                if (swapCooldown == 0) {
                    PlayerMods.click(p, i, freeHotbarSlot(p), "SWAP");
                    swapCooldown = 5;
                }
                return true;
            }
        }
        return false;
    }

    /** Sichtbare Seite eines festen Nachbarn von t: {Nachbar, Seite, Trefferpunkt} oder null. */
    private static Object[] findFace(Level level, LocalPlayer p, BlockPos t) {
        Vec3 eye = p.getEyePosition();
        double reach = p.blockInteractionRange();
        for (Direction d : Direction.values()) {
            BlockPos s = t.relative(d);
            if (!solid(level, s) || fluid(level, s)) continue;
            Direction face = d.getOpposite();
            Vec3 hit = new Vec3(t.getX() + 0.5 + d.getStepX() * 0.5, t.getY() + 0.5 + d.getStepY() * 0.5,
                    t.getZ() + 0.5 + d.getStepZ() * 0.5);
            if (eye.distanceToSqr(hit) > reach * reach) continue;
            Vec3 into = hit.add(d.getStepX() * 0.05, d.getStepY() * 0.05, d.getStepZ() * 0.05);
            BlockHitResult r = level.clip(new ClipContext(eye, into, ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, p));
            if (r.getType() == HitResult.Type.BLOCK && r.getBlockPos().equals(s) && r.getDirection() == face) {
                return new Object[] {s, face, hit};
            }
        }
        return null;
    }

    // =====================================================================================
    // Essen, Gestein
    // =====================================================================================

    private static boolean isFood(ItemStack st) {
        return !st.isEmpty() && st.get(DataComponents.FOOD) != null && !PlayerMods.badFood(st);
    }

    private static int foodSlot(LocalPlayer p) {
        var inv = p.getInventory();
        int best = -1;
        int bestN = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            if (!isFood(st)) continue;
            if (keepGapples && (st.is(Items.GOLDEN_APPLE) || st.is(Items.ENCHANTED_GOLDEN_APPLE))) continue;
            int n = st.get(DataComponents.FOOD).nutrition();
            if (n > bestN) {
                bestN = n;
                best = i;
            }
        }
        return best;
    }

    private static boolean needEat(LocalPlayer p) {
        return autoEat && !p.isCreative() && p.getFoodData().getFoodLevel() <= eatHunger && foodSlot(p) >= 0;
    }

    private static void eat(LocalPlayer p) {
        action = "Isst";
        eatTicks++;
        if (eatTicks == 1) {
            int slot = foodSlot(p);
            if (slot < 0) {
                phase = Phase.TUNNEL;
                return;
            }
            p.getInventory().setSelectedSlot(slot);
        }
        kUse = true;
        if (eatTicks > 5 && !p.isUsingItem() || eatTicks > 100) {
            kUse = false;
            if (p.getFoodData().getFoodLevel() <= eatHunger && foodSlot(p) >= 0 && ++eatRounds < 5) {
                eatTicks = 0;
                return;
            }
            phase = Phase.TUNNEL;
            eatTicks = 0;
            wait = 2;
            lastHit = null;
        }
    }

    private static boolean isJunk(ItemStack st) {
        return !st.isEmpty() && JUNK.contains(BuiltInRegistries.ITEM.getKey(st.getItem()).getPath());
    }

    private static boolean isBuild(ItemStack st) {
        return st.getItem() instanceof BlockItem bi && TestClient.isBuildBlock(bi.getBlock());
    }

    /**
     * Slot mit Gestein, das weg kann, sonst -1. Baubloecke: nur ueber die behaltenen Stapel hinaus. Wie ein
     * Spieler nur volle Stapel, ausser das Inventar wird knapp (any = jeder Stapel zaehlt).
     */
    private static int junkSlot(LocalPlayer p, boolean any) {
        var inv = p.getInventory();
        int build = 0;
        for (int i = 0; i < 36; i++) {
            if (isBuild(inv.getItem(i))) build++;
        }
        int sel = inv.getSelectedSlot();
        boolean tight = any || freeSlots(p) < 3;
        for (int i = 35; i >= 0; i--) {
            ItemStack st = inv.getItem(i);
            if (!isJunk(st) || i == sel || i == hammerSlot) continue;
            if (isBuild(st) && build <= keepBlocks) continue;
            if (!tight && st.getCount() < st.getMaxStackSize()) continue;
            return i;
        }
        return -1;
    }

    private static boolean throwJunk(LocalPlayer p) {
        if (!dropJunk) return false;
        if (dropSlot < 0) {
            if (dropCooldown > 0) return false;
            dropSlot = junkSlot(p, false);
            if (dropSlot < 0) return false;
            aimedTicks = 0;
        }
        action = "Wirft Gestein weg";
        if (Look.turn(p, Look.yawOf(-curX, -curZ), -15.0f, 40.0f, 25.0f, 3.0f)) {
            if (++aimedTicks >= 2) {
                int slot = dropSlot;
                dropSlot = -1;
                aimedTicks = 0;
                dropCooldown = 8;
                if (isJunk(p.getInventory().getItem(slot))) {
                    PlayerMods.click(p, slot < 9 ? 36 + slot : slot, 1, "THROW");
                }
                int more = junkSlot(p, false);
                if (more >= 0 && more != slot) {
                    dropSlot = more;
                    dropCooldown = 0;
                    aimedTicks = -(2 + Look.rng().nextInt(4));
                }
                lastHit = null;
            }
        } else {
            aimedTicks = 0;
        }
        return true;
    }

    private static int freeSlots(LocalPlayer p) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            if (p.getInventory().getItem(i).isEmpty()) n++;
        }
        return n;
    }

    // =====================================================================================
    // Hilfen
    // =====================================================================================

    /** In die Mitte von t gehen (Blick nach yaw). true = angekommen. */
    private static boolean moveTo(LocalPlayer p, BlockPos t, float yaw) {
        double dx = t.getX() + 0.5 - p.getX();
        double dz = t.getZ() + 0.5 - p.getZ();
        if (feet(p).equals(t) && Math.abs(dx) < 0.3 && Math.abs(dz) < 0.3) return true;
        float[] w = Look.wander();
        Look.turn(p, yaw + w[0], 25.0f + w[1], 30.0f, 10.0f, 0.0f);
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.35 && feet(p).equals(t)) return false; // ausrollen lassen
        double yawR = Math.toRadians(p.getYRot());
        double fwd = -dx * Math.sin(yawR) + dz * Math.cos(yawR);
        double left = dx * Math.cos(yawR) + dz * Math.sin(yawR);
        kFwd = fwd > 0.04;
        kBack = fwd < -0.04;
        kLeft = left > 0.17;
        kRight = left < -0.17;
        return false;
    }

    private static BlockPos feet(LocalPlayer p) {
        return BlockPos.containing(p.getX(), p.getY() + 0.2, p.getZ());
    }

    private static boolean fluid(Level level, BlockPos pos) {
        return !level.getFluidState(pos).isEmpty();
    }

    private static boolean passable(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static boolean solid(Level level, BlockPos pos) {
        return !passable(level, pos);
    }

    /** Darf der Block weg? Nicht, wenn darueber oder seitlich Fluessigkeit ist (die liefe sonst in den Tunnel). */
    private static boolean safeToBreak(Level level, BlockPos pos) {
        for (BlockPos n : new BlockPos[] {pos.above(), pos.north(), pos.south(), pos.east(), pos.west()}) {
            if (fluid(level, n)) return false;
        }
        return !fluid(level, pos);
    }

    private static float wrapDeg(float d) {
        d %= 360.0f;
        if (d >= 180.0f) d -= 360.0f;
        if (d < -180.0f) d += 360.0f;
        return d;
    }

    private static String compass(int dx, int dz) {
        if (dx > 0) return "Osten";
        if (dx < 0) return "Westen";
        return dz > 0 ? "Süden" : "Norden";
    }

    private static void say(Minecraft mc, String text) {
        if (mc.player != null) mc.player.sendSystemMessage(Lang.literal("1912: " + Lang.t(text)));
    }

    private static void clearKeys() {
        kFwd = false;
        kBack = false;
        kLeft = false;
        kRight = false;
        kSneak = false;
        kAttack = false;
        kUse = false;
    }

    private static void applyKeys(Minecraft mc) {
        var o = mc.options;
        Look.setKey(o.keyUp, kFwd);
        Look.setKey(o.keyDown, kBack);
        Look.setKey(o.keyLeft, kLeft);
        Look.setKey(o.keyRight, kRight);
        Look.setKey(o.keyJump, false);
        Look.setKey(o.keySprint, false);
        Look.setSneak(o.keyShift, kSneak);
        Look.setKey(o.keyAttack, kAttack);
        Look.setKey(o.keyUse, kUse);
    }

    // =====================================================================================
    // Anzeige
    // =====================================================================================

    /** Kleine Anzeige ueber der Hotbar: was er gerade macht, Laenge, Erze, Laufzeit. */
    static void renderHud(GuiGraphicsExtractor g, Minecraft mc) {
        if (!running || !showStatus || mc.player == null || mc.gui.screen() != null) return;
        var font = mc.font;
        long sec = Math.max(0L, (System.currentTimeMillis() - startedAt) / 1000L);
        String time = sec >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60)
                : String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
        String title = "Tunnel Miner";
        String act = Lang.t(action);
        String stats = Lang.t("Länge: ") + length + "   " + Lang.t("Erze: ") + oresMined + "   " + time;
        int w = Math.max(font.width(title) + 8 + font.width(act), font.width(stats)) + 14;
        int h = 26;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int x = (sw - w) / 2;
        int y = sh - 96;
        float old = Ui.alpha;
        Ui.alpha = 1f;
        Ui.roundRect(g, x, y, w, h, 4, 0xB0100C18);
        Ui.fill(g, x + 4, y, x + w - 4, y + 1, Theme.accent());
        Ui.text(g, font, title, x + 7, y + 4, Theme.accent(), true);
        Ui.text(g, font, act, x + 7 + font.width(title) + 8, y + 4, 0xFFFFFFFF, true);
        Ui.text(g, font, stats, x + 7, y + 15, 0xFFB8B8C8, true);
        Ui.alpha = old;
    }

    /** Naechsten Tunnelabschnitt hervorheben (rot = Lava/Wasser daneben). */
    static void render(GuiGraphicsExtractor g) {
        if (!running || !highlight || phase != Phase.TUNNEL) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        BlockPos next = feet(mc.player).offset(curX, 0, curZ);
        for (BlockPos c : section(next, curX, curZ)) {
            if (!solid(mc.level, c)) continue;
            int col = safeToBreak(mc.level, c) ? (Theme.accent() & 0xFFFFFF) | 0xA0000000 : RED;
            Render3D.outline(g, new AABB(c.getX(), c.getY(), c.getZ(), c.getX() + 1, c.getY() + 1, c.getZ() + 1),
                    col, false);
        }
    }
}
