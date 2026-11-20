package de.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Debris Miner: graebt im Nether einen Tunnel auf fester Hoehe und baut Antiken Schrott ab, sobald er direkt
 * neben dem Tunnel sichtbar wird.
 *
 * <p>Gedacht fuer Server mit Anti-Xray: der Server zeigt Erze erst, wenn sie frei liegen bzw. der Spieler
 * direkt daneben steht. Es wird deshalb nicht in die Ferne gesucht, sondern nach jedem Schritt (mit
 * einstellbarer Verzoegerung fuer die Block-Updates des Servers) nur die direkte Umgebung des Tunnels geprueft.
 *
 * <p>Teile, die einzeln an- und ausgeschaltet werden (alle Einstellungen im Menue, siehe {@link DebrisScreen}):
 * <ul>
 * <li>Abbau (Kern): Tunnel 1x2/1x3/2x2, Ader komplett abbauen, danach zurueck auf die Tunnelachse.</li>
 * <li>Einsammeln: gedropptes Debris im Umkreis aufsammeln.</li>
 * <li>Lava-Schutz: Lava frueh erkennen, umgehen oder zubauen. Bloecke direkt neben Lava werden nie abgebaut.</li>
 * <li>Nachschub: Spitzhacken und Essen in die Hotbar holen (auch aus Shulker-Kisten/Endertruhe), essen.</li>
 * <li>Werkzeugschutz: Warnung und Wechsel bei fast kaputter Spitzhacke, Stopp ohne Ersatz.</li>
 * <li>Spieler-Schutz und Totem-Schutz: Verbindung trennen (Auto-Log).</li>
 * <li>Rueckzug: Befehl wie /home bei Gefahr.</li>
 * </ul>
 * Schutz und Rueckzug wirken, solange der Debris Miner laeuft. Laufen und Graben auf Umwegen uebernimmt
 * {@link Walker} (gleiche Wegsuche wie "Zu Koordinaten gehen", die ebenfalls nie neben Lava abbaut).
 */
public final class DebrisMiner {
    private DebrisMiner() {
    }

    // =====================================================================================
    // Einstellungen (oeffentliche Felder werden von TestConfig gespeichert, Prefix "dm.")
    // =====================================================================================

    // --- Abbau ---
    public static int targetY = 15;
    public static int pattern = 0;              // 0 = 1x2, 1 = 1x3, 2 = 2x2
    public static int scanDelay = 3;            // Ticks nach Abbau/Schritt, bis gescannt wird
    public static int scanRadius = 2;           // so weit um den Tunnel wird nach Debris geschaut
    public static int direction = 0;            // 0 = Blickrichtung, 1 = Norden, 2 = Osten, 3 = Sueden, 4 = Westen
    public static int maxLength = 0;            // Tunnel-Laenge, 0 = unbegrenzt
    public static boolean onlyExposed = true;   // nur echte Erze: an Luft oder nahe an selbst abgebauten Bloecken
    public static boolean dropJunk = true;      // Netherrack und anderes Gestein wegwerfen
    public static int keepNetherrack = 1;       // so viele Stapel Netherrack behalten (zum Bauen)
    public static boolean highlight = true;
    public static boolean showStatus = true;    // Zustand im Bild (ueber der Hotbar)
    public static boolean showPath = true;      // Weg bei Umwegen anzeigen
    public static boolean veinMine = true;      // ganze Ader abbauen (Nachbarn abgebauter Bloecke pruefen)
    public static boolean digToHidden = true;   // zu verdecktem Debris hin graben
    public static boolean mineFloor = true;     // Debris unter dem Tunnelboden abbauen
    public static boolean mineCeiling = true;   // Debris ueber der Tunneldecke abbauen
    public static boolean descendFirst = true;  // erst auf die Ziel-Hoehe gehen (sonst: Tunnel auf der Start-Hoehe)
    public static boolean fillFloor = true;     // Loecher im Boden zubauen (sonst ausweichen)
    public static boolean returnAxis = true;    // nach Umwegen zurueck auf die urspruengliche Achse
    public static boolean chunkBorder = true;   // an einer Chunk-Grenze entlang graben
    public static boolean humanize = true;      // menschlich bewegen (weicher drehen, nicht ducken beim Gehen)
    public static boolean lookAround = true;    // ab und zu kurz stehen bleiben und umschauen (nur menschlich)
    public static boolean sprintMine = true;    // Sofort-Abbau (z.B. Effizienz V): Taste halten und durchsprinten
    public static boolean chatFound = true;     // Fund im Chat melden
    public static boolean foundSound = false;   // Ton bei Fund

    // --- Einsammeln ---
    public static boolean collect = true;
    public static int collectRadius = 8;
    public static int collectTimeout = 10;      // Sekunden
    public static boolean collectHigh = true;   // hoch liegende Items herunterholen (Block darunter abbauen)
    public static boolean collectPits = true;   // fuer Items in Gruben hinuntersteigen
    public static boolean collectOther = true;  // auch andere eigene Funde (z.B. Gold, Quarz), kein Gestein

    // --- Lava ---
    public static boolean lavaAvoid = true;
    public static int lavaDistance = 3;
    public static int lavaMode = 0;             // 0 = Umgehen, 1 = Zubauen
    public static boolean lavaBacktrack = true; // zuruecklaufen, wenn es seitlich nicht weitergeht

    // --- Nachschub ---
    public static boolean refill = true;
    public static boolean refillPickaxe = true;
    public static boolean refillFood = true;
    public static int eatHunger = 14;           // essen bei Hunger <= Wert (20 = satt)
    public static boolean refillShulker = false;
    public static boolean refillEnderChest = false;
    public static boolean autoEat = true;       // bei Hunger essen
    public static boolean keepGapples = true;   // Goldaepfel nicht essen
    public static boolean refillBlocks = true;  // Baubloecke in die Hotbar holen
    public static boolean boxPickup = true;     // Shulker-Kiste / Endertruhe danach wieder abbauen

    // --- Werkzeugschutz ---
    public static boolean toolGuard = true;
    public static int toolUnit = 0;             // 0 = Prozent, 1 = Haltbarkeitspunkte
    public static int toolPercent = 5;
    public static int toolPoints = 60;
    public static boolean toolSound = true;
    public static boolean toolToast = true;
    public static boolean toolStop = true;
    public static boolean toolSwitch = true;    // auf eine andere Spitzhacke wechseln
    public static boolean toolChat = true;      // Warnung im Chat

    // --- Spieler in der Naehe ---
    public static boolean playerLog = true;
    public static int playerChunks = 6;         // in Chunks, 0 = ganze Sichtweite
    public static String whitelist = "";
    public static boolean playerCheckStart = true; // nicht starten, wenn schon jemand da ist

    // --- Totem ---
    public static boolean totemLog = true;
    public static int totemDelay = 0;           // Ticks, 0 = sofort
    public static boolean totemIgnoreSpare = false;

    // --- Rueckzug ---
    public static boolean home = false;
    public static boolean useFriends = true;      // Freundesliste des Clients mitbenutzen
    public static String homeCommand = "/home";   // frei einstellbar, mehrere Befehle mit ; trennen
    public static int homeCmdDelay = 20;          // Pause zwischen mehreren Befehlen (Ticks)
    // eigener Befehl je Ausloeser (leer = homeCommand)
    public static String homeCmdLowHp = "";
    public static String homeCmdFull = "";
    public static String homeCmdNoTool = "";
    public static String homeCmdPlayer = "";
    public static String homeCmdTotem = "";
    public static String homeCmdStuck = "";
    public static boolean homeLowHp = true;
    public static int homeHearts = 4;
    public static boolean homeFull = true;
    public static boolean homeNoTool = true;
    public static boolean homePlayer = false;
    public static boolean homeTotem = false;    // nach Totem (wenn "Nach Totem trennen" aus ist)
    public static boolean homeStuck = false;    // wenn er feststeckt (Lava ringsum, Weg blockiert ...)

    static final String[] PATTERNS = {"1x2", "1x3", "2x2"};
    static final String[] HOME_PRESETS = {"/home", "/spawn", "/hub", "/lobby"};

    // noch zu sendende Rueckzug-Befehle (laeuft auch weiter, wenn der Debris Miner schon angehalten ist)
    private static final List<String> queuedCommands = new ArrayList<>();
    private static int queueWait;
    static final String[] DIRECTIONS = {"Blickrichtung", "Norden", "Osten", "Süden", "Westen"};
    static final String[] LAVA_MODES = {"Umgehen", "Zubauen"};
    static final String[] TOOL_UNITS = {"Prozent", "Punkten"};
    private static final int[] DIR_X = {0, 0, 1, 0, -1};
    private static final int[] DIR_Z = {0, -1, 0, 1, 0};

    // =====================================================================================
    // Zustand
    // =====================================================================================

    private enum Phase { DESCEND, TUNNEL, VEIN, COLLECT, RETURN, BACKTRACK, EAT, CONTAINER }

    private enum Box { PREP, PLACE, WAIT_PLACED, OPEN, WAIT_OPEN, TAKE, CLOSE, BREAK, PICKUP }

    private static final int OK = 0;
    private static final int BUSY = 1;
    private static final int NO_TOOL = 2;

    private static final double REACH = 4.4;
    private static final int ORANGE = 0xFFFF8A3D;
    private static final int RED = 0xFFFF4A4A;

    private static boolean running;
    private static boolean paused;
    private static Phase phase = Phase.TUNNEL;
    private static Phase afterPause = Phase.TUNNEL;   // nach Essen/Nachschub weiter mit ...
    private static int mainX;
    private static int mainZ;
    private static int curX;
    private static int curZ;
    private static int sideLeft;                      // verbleibende Schritte eines Umwegs zur Seite
    private static int axisX;                         // ein Punkt auf der urspruenglichen Tunnelachse
    private static int axisZ;
    private static int sinceSide;                     // Schritte geradeaus seit dem letzten Ausweichen
    private static double lastMoveX = Double.NaN;     // fuer das Tempo beim Ausrollen
    private static double lastMoveZ;
    private static int bestAlong;                     // weitester Fortschritt entlang der Hauptrichtung
    private static int noAdvance;                     // Schritte ohne neuen Fortschritt (im Kreis?)
    private static int lastSide;                      // 1 = links, -1 = rechts, 0 = noch keiner
    private static boolean forceSide;                 // nach dem Zuruecklaufen sofort seitlich ausweichen
    private static int backtracks;
    private static BlockPos resume;
    private static int resumeX;
    private static int resumeZ;
    private static int resumeSide;

    private static final LinkedHashSet<BlockPos> targets = new LinkedHashSet<>();
    private static final Set<Long> ignored = new HashSet<>();
    /** Bloecke auf dem Weg zum Debris, die sich nicht abbauen liessen. */
    private static final Set<Long> badDig = new HashSet<>();
    /** Zuletzt abgebaute Bloecke: um sie herum zeigt der Server die echten Bloecke (Anti-Xray). */
    private static final LinkedHashSet<Long> mined = new LinkedHashSet<>();
    private static final List<BlockPos> rescan = new ArrayList<>();
    private static final Set<Integer> ignoredItems = new HashSet<>();
    private static final boolean[] alerted = new boolean[9];

    private static int wait;
    private static int ticks;
    private static int stuckTicks;
    private static int walkerFails;
    private static double bestDist;                   // Rueckweg: kleinster Abstand bisher
    private static int noProgress;                    // Rueckweg: Ticks ohne Annaeherung
    private static int nudge;                         // so viele Ticks ohne Wegsuche direkt hinschieben
    private static BlockPos veinTarget;               // Ader: aktuelles Ziel (nicht bei jedem Tick wechseln)
    private static int veinTicks;                     // so lange schon an diesem Ziel
    private static int collectItem = -1;              // Einsammeln: aktuelles Item (Entity-Nummer)
    private static int arrivedTicks;
    private static BlockPos walkGoal;
    private static BlockPos digPos;
    private static BlockPos stepTarget;               // Feld, in das er gerade einen Schritt macht
    private static int digTicks;
    private static int digLimit;
    private static boolean digging;
    private static int aimedTicks;
    private static int dropSlot = -1;
    private static int swapCooldown;
    private static int dropCooldown;
    private static int placeCooldown;
    private static int placeFails;
    private static int sealTries;
    private static int eatRounds;
    private static int tunnelLength;
    private static int debrisMined;
    private static int pendingTotem = -1;
    private static String action = "";
    private static long startedAt;
    private static int tunnelY;                       // Hoehe, auf der der Tunnel laeuft

    // Nachschub aus Shulker-Kiste / Endertruhe
    private static Box box;
    private static BlockPos boxPos;
    private static boolean boxEnder;
    private static Item boxItem;
    private static int boxCount;
    private static boolean needPick;
    private static boolean needFood;
    private static int picksTaken;
    private static int foodTaken;
    private static int openTries;
    private static boolean enderTried;
    private static int boxRetry;                      // Ticks, bis wieder eine Kiste versucht wird

    // Tasten fuer diesen Tick
    private static boolean kFwd;
    private static boolean kBack;
    private static boolean kLeft;
    private static boolean kRight;
    private static boolean kSneak;
    private static boolean kAttack;
    private static boolean kUse;
    private static boolean kSprint;
    private static boolean walkerTicked;

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

    /** Schalter im Menue / Taste. */
    static void toggle() {
        Minecraft mc = Minecraft.getInstance();
        if (running) {
            stop(mc, "Debris Miner aus");
        } else {
            String err = start(mc);
            if (err != null && mc.player != null) say(mc, err);
        }
    }

    /** Startet den Debris Miner. Gibt eine Fehlermeldung zurueck oder null. */
    static String start(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return "Nicht im Spiel";
        if (mc.level.dimension() != Level.NETHER) return "Debris Miner geht nur im Nether";
        if (playerCheckStart && (playerLog || home && homePlayer)) {
            // schon jemand da: nicht starten (sonst wuerde er sofort trennen bzw. /home senden)
            Player other = nearbyPlayer(mc, p);
            if (other != null) return Lang.t("Nicht gestartet – Spieler in der Nähe: ") + other.getName().getString();
        }
        TunnelMiner.stop(mc, null);
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
        running = true;
        startedAt = System.currentTimeMillis();
        Walker.toolOk = DebrisMiner::toolAllowed;
        BlockPos feet = feet(p);
        axisX = feet.getX();
        axisZ = feet.getZ();
        if (chunkBorder) {
            // senkrecht zur Laufrichtung auf die naechste Chunk-Grenze (hoechstens 8 Bloecke daneben);
            // ein 2x2-Tunnel liegt genau auf beiden Seiten der Grenze
            boolean alongX = mainX != 0;
            int c = alongX ? feet.getZ() : feet.getX();
            int b = Math.round(c / 16.0f) * 16; // die Grenze liegt zwischen b-1 und b
            int right = alongX ? mainX : -mainZ; // so liegt die zweite Spalte des 2x2-Tunnels
            int axis = pattern == 2 ? (right > 0 ? b - 1 : b) : (c >= b ? b : b - 1);
            if (alongX) {
                axisZ = axis;
            } else {
                axisX = axis;
            }
        }
        tunnelY = descendFirst ? targetY : feet.getY();
        phase = feet.getY() == tunnelY ? Phase.TUNNEL : Phase.DESCEND;
        borderFirst = chunkBorder && phase == Phase.DESCEND;
        borderFails = 0;
        action = phase == Phase.TUNNEL ? "Gräbt Tunnel" : "Geht auf Ziel-Höhe";
        if (chunkBorder) sinceSide = 1000; // gleich zuerst seitlich auf die Grenze
        say(mc, "Debris Miner gestartet: Richtung " + Lang.t(compass(mainX, mainZ)) + ", Höhe " + tunnelY
                + (chunkBorder ? Lang.t(", an der Chunk-Grenze") : ""));
        return null;
    }

    /** Beendet den Debris Miner (message = Chat-Meldung oder null). */
    static void stop(Minecraft mc, String message) {
        if (!running) return;
        running = false;
        Look.human = false;
        Walker.toolOk = st -> true;
        if (Walker.isActive() && !TestClient.areaActive) Walker.stop(mc);
        clearKeys();
        Look.releaseSneak();
        applyKeys(mc);
        digging = false;
        LocalPlayer p = mc.player;
        if (p != null && p.containerMenu != p.inventoryMenu && box != null) p.closeContainer();
        box = null;
        if (p != null && message != null) say(mc, message);
    }

    /** Welt verlassen: ohne Meldung aus. */
    static void reset() {
        running = false;
        box = null;
        digging = false;
        Walker.toolOk = st -> true;
    }

    private static void clearState() {
        sprintRun = false;
        sprintCooldown = 0;
        sprintWatch.clear();
        glanceTicks = 0;
        glanceTimer = 600 + Look.rng().nextInt(1200);
        targets.clear();
        ignored.clear();
        mined.clear();
        badDig.clear();
        rescan.clear();
        ignoredItems.clear();
        java.util.Arrays.fill(alerted, false);
        wait = 0;
        ticks = 0;
        stuckTicks = 0;
        walkerFails = 0;
        arrivedTicks = 0;
        walkGoal = null;
        digPos = null;
        stepTarget = null;
        digging = false;
        aimedTicks = 0;
        dropSlot = -1;
        swapCooldown = 0;
        dropCooldown = 0;
        placeCooldown = 0;
        placeFails = 0;
        sealTries = 0;
        eatRounds = 0;
        tunnelLength = 0;
        debrisMined = 0;
        pendingTotem = -1;
        sideLeft = 0;
        sinceSide = 0;
        bestAlong = Integer.MIN_VALUE;
        noAdvance = 0;
        lastSide = 0;
        forceSide = false;
        backtracks = 0;
        resume = null;
        box = null;
        enderTried = false;
        boxRetry = 0;
        paused = false;
    }

    /** Kurzer Zustand fuer das Menue. */
    static String status() {
        if (!running) return Lang.t("Bereit");
        return Lang.t(action) + "  ·  " + Lang.t("Tunnel-Länge: ") + tunnelLength + "  ·  " + Lang.t("Debris: ")
                + debrisMined;
    }

    // =====================================================================================
    // Tick
    // =====================================================================================

    /** Ein Tick (vom TestClient, solange der Debris Miner laeuft). */
    static void tick(Minecraft mc) {
        if (!running) return;
        Look.human = humanize; // gilt auch fuer den Walker, solange der Debris Miner ihn steuert
        LocalPlayer p = mc.player;
        Level level = mc.level;
        if (p == null || level == null) {
            reset();
            return;
        }
        try {
            if (guard(mc, p)) return;
            boolean boxOpen = phase == Phase.CONTAINER && box != null
                    && (box == Box.WAIT_OPEN || box == Box.TAKE || box == Box.CLOSE);
            if (mc.gui.screen() != null && !boxOpen) {
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
                if (running) {
                    if (walkerTicked) {
                        Look.setKey(mc.options.keyUse, false);
                    } else {
                        applyKeys(mc);
                    }
                }
            }
        } catch (RuntimeException | LinkageError e) {
            Safe.report("Debris Miner", e);
            stop(mc, "Debris Miner angehalten (Fehler)");
        }
    }

    // ---------- menschlich: ab und zu umschauen ----------

    private static int glanceTimer = 600 + (int) (Math.random() * 1200);
    private static int glanceTicks;
    private static float glanceYaw;
    private static float glancePitch;
    private static float glanceBackYaw;
    private static float glanceBackPitch;

    /**
     * Alle 45 bis 120 Sekunden bleibt er im Tunnel kurz stehen und schaut sich um (wie ein Spieler, der
     * nach Geraeuschen oder Lava schaut), dann schaut er zurueck und macht weiter. Nur, wenn es sicher ist.
     * true = schaut gerade (der Rest des Ticks entfaellt).
     */
    private static boolean glance(Minecraft mc, Level level, LocalPlayer p) {
        if (!humanize || !lookAround) return false;
        if (glanceTicks > 0) {
            glanceTicks--;
            action = "Schaut sich kurz um";
            clearKeys();
            if (glanceTicks > 12) {
                Look.turn(p, glanceYaw, glancePitch, 9.0f, 6.0f, 0.0f);
            } else {
                Look.turn(p, glanceBackYaw, glanceBackPitch, 12.0f, 8.0f, 0.0f); // zurueck in die Laufrichtung
            }
            return true; // Tasten setzt tick() danach (alle los)
        }
        if (--glanceTimer > 0) return false;
        // nur mitten im Tunnel, auf festem Boden, ohne Gefahr und ohne angefangenen Block
        boolean calm = phase == Phase.TUNNEL && !sprintRun && p.onGround() && digPos == null && !Walker.isActive() && wait == 0
                && sideLeft == 0
                && lavaAhead(level, feet(p), curX, curZ, Math.max(2, lavaDistance), true) == 0;
        if (!calm) {
            glanceTimer = 40; // gleich nochmal versuchen
            return false;
        }
        var rng = Look.rng();
        glanceTimer = 900 + rng.nextInt(1500);
        glanceTicks = 28 + rng.nextInt(26);
        glanceBackYaw = p.getYRot();
        glanceBackPitch = p.getXRot();
        float side = (rng.nextBoolean() ? 1 : -1) * (35 + rng.nextInt(70));
        glanceYaw = p.getYRot() + side;
        glancePitch = -8 + rng.nextInt(30); // mal etwas hoch, mal zum Boden
        digging = false;
        return true;
    }

    /** Menue oder Chat offen: alles loslassen und warten. */
    private static void pause(Minecraft mc) {
        if (paused) return;
        paused = true;
        clearKeys();
        Look.releaseSneak();
        applyKeys(mc);
        digging = false;
        if (Walker.isActive()) Walker.release(mc);
    }

    private static void run(Minecraft mc, Level level, LocalPlayer p) {
        if (swapCooldown > 0) swapCooldown--;
        if (boxRetry > 0) boxRetry--;
        if (dropCooldown > 0) dropCooldown--;
        if (placeCooldown > 0) placeCooldown--;
        toolAlerts(mc, p);
        if (glance(mc, level, p)) return;
        switch (phase) {
            case DESCEND -> descend(mc, level, p);
            case TUNNEL -> tunnel(mc, level, p);
            case VEIN -> vein(mc, level, p);
            case COLLECT -> collect(mc, level, p);
            case RETURN -> returnToAxis(mc, level, p);
            case BACKTRACK -> backtrack(mc, level, p);
            case EAT -> eat(p);
            case CONTAINER -> container(mc, level, p);
        }
    }

    // =====================================================================================
    // Schutz: Spieler, Totem, Leben, Inventar
    // =====================================================================================

    /** true = Debris Miner wurde beendet (Verbindung getrennt oder Rueckzug). */
    private static boolean guard(Minecraft mc, LocalPlayer p) {
        if (pendingTotem >= 0) {
            if (pendingTotem == 0) {
                pendingTotem = -1;
                disconnect(mc, "Totem ausgelöst");
                return true;
            }
            pendingTotem--;
        }
        if (playerLog || home && homePlayer) {
            Player other = nearbyPlayer(mc, p);
            if (other != null) {
                String reason = Lang.t("Spieler in der Nähe: ") + other.getName().getString();
                if (playerLog) {
                    disconnect(mc, reason);
                } else {
                    goHome(mc, reason, homeCmdPlayer);
                }
                return true;
            }
        }
        if (home && homeLowHp && p.getHealth() <= homeHearts * 2.0f) {
            goHome(mc, "Wenig Leben", homeCmdLowHp);
            return true;
        }
        if (phase != Phase.CONTAINER && inventoryFull(p)) {
            if (home && homeFull) {
                goHome(mc, "Inventar voll", homeCmdFull);
            } else {
                stop(mc, "Inventar voll – Debris Miner angehalten");
            }
            return true;
        }
        return false;
    }

    /** Naechster fremder Spieler im Radius (Freunde aus der Liste zaehlen nicht). */
    private static Player nearbyPlayer(Minecraft mc, LocalPlayer self) {
        // Freunde aus dieser Liste und (wenn eingeschaltet) aus der Freundesliste des Clients zaehlen nicht
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
        if (!running || mc.player == null || e != mc.player) return;
        if (!totemLog) {
            if (home && homeTotem) goHome(mc, "Totem ausgelöst", homeCmdTotem);
            return;
        }
        // Das ausgeloeste Totem ist in diesem Moment meist noch im Inventar: deshalb minus 1
        int spare = totemCount(mc.player) - 1;
        if (totemIgnoreSpare && spare > 0) {
            say(mc, "Totem ausgelöst – noch ein Ersatz-Totem da, Debris Miner läuft weiter");
            return;
        }
        pendingTotem = Math.max(0, totemDelay);
    }

    private static int totemCount(LocalPlayer p) {
        int n = p.getOffhandItem().is(Items.TOTEM_OF_UNDYING) ? p.getOffhandItem().getCount() : 0;
        var inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(Items.TOTEM_OF_UNDYING)) n += st.getCount();
        }
        return n;
    }

    private static void disconnect(Minecraft mc, String reason) {
        stop(mc, null);
        MiscMods.holdReconnect = true; // Auto Reconnect soll nicht gleich wieder verbinden
        var conn = mc.getConnection();
        if (conn != null) {
            conn.getConnection().disconnect(Lang.literal("1912: Debris Miner – " + Lang.t(reason)));
        }
    }

    /**
     * Rueckzug: schickt den Befehl des Ausloesers (own) oder, wenn der leer ist, den allgemeinen Befehl.
     * Mehrere Befehle (mit ; getrennt) gehen nacheinander raus, dazwischen die eingestellte Pause.
     */
    private static void goHome(Minecraft mc, String reason, String own) {
        List<String> cmds = commands(own);
        if (cmds.isEmpty()) cmds = commands(homeCommand);
        if (cmds.isEmpty()) cmds = List.of("/home");
        MiscMods.sendChat(mc, cmds.get(0));
        queuedCommands.clear();
        queuedCommands.addAll(cmds.subList(1, cmds.size()));
        queueWait = homeCmdDelay;
        stop(mc, "Rückzug: " + String.join("; ", cmds) + " gesendet (" + Lang.t(reason) + ")");
    }

    /** Zerlegt die Eingabe in einzelne Befehle; der / am Anfang ist optional. */
    static List<String> commands(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String part : raw.split("[;\\n]")) {
            String c = part.strip();
            while (c.startsWith("/")) c = c.substring(1).strip();
            if (!c.isEmpty()) out.add("/" + c);
        }
        return out;
    }

    /** Jeden Tick (auch ohne laufenden Miner): weitere Rueckzug-Befehle nach der Pause senden. */
    static void tickCommands(Minecraft mc) {
        if (queuedCommands.isEmpty()) return;
        if (mc.getConnection() == null) {
            queuedCommands.clear();
            return;
        }
        if (queueWait > 0) {
            queueWait--;
            return;
        }
        MiscMods.sendChat(mc, queuedCommands.remove(0));
        queueWait = homeCmdDelay;
    }

    /** Er kommt nicht weiter: Rueckzug-Befehl (wenn eingestellt), sonst anhalten. */
    private static void giveUp(Minecraft mc, String message, String reason) {
        if (home && homeStuck) {
            goHome(mc, reason, homeCmdStuck);
        } else {
            stop(mc, message);
        }
    }

    private static boolean noEmptySlot(LocalPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).isEmpty()) return false;
        }
        return true;
    }

    /** Kein freier Platz mehr (Gestein, das gleich weggeworfen wird, zaehlt als frei). */
    private static boolean inventoryFull(LocalPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).isEmpty()) return false;
        }
        return !dropJunk || junkSlot(p) < 0;
    }

    // =====================================================================================
    // Tunnel
    // =====================================================================================

    private static boolean borderFirst;   // Abstieg mit Chunk-Grenze: erst seitlich auf die Grenze, dann hinunter
    private static int borderFails;

    private static void descend(Minecraft mc, Level level, LocalPlayer p) {
        action = "Geht auf Ziel-Höhe";
        BlockPos feet = feet(p);
        if (borderFirst && descendToBorder(mc, level, p, feet)) return;
        if (p.onGround() && Math.abs(feet.getY() - tunnelY) <= (walkerFails > 0 ? 1 : 0)) {
            stopWalker(mc);
            phase = Phase.TUNNEL;
            wait = scanDelay;
            return;
        }
        if (walkGoal == null || !Walker.isActive()) {
            if (walkerFails > 3) {
                giveUp(mc, "Ziel-Höhe nicht erreichbar – Debris Miner angehalten", "Ziel-Höhe nicht erreichbar");
                return;
            }
            // mit Chunk-Grenze schon beim Abstieg seitlich auf die Grenze (dann liegt alles auf ihr)
            // (klappt das zweimal nicht, geht er einfach gerade hinunter und wechselt spaeter auf die Grenze)
            boolean alongX = mainX != 0;
            boolean border = chunkBorder && walkerFails < 2;
            int gx = border && !alongX ? axisX : feet.getX();
            int gz = border && alongX ? axisZ : feet.getZ();
            startWalkExact(new BlockPos(gx, tunnelY, gz));
        }
        String r = tickWalker(mc, level, p);
        if (r != null) {
            walkGoal = null;
            walkerFails++;
        }
    }

    /**
     * Mit Chunk-Grenze: vor dem Abstieg auf der Start-Hoehe seitlich auf die Grenze gehen, damit er auf ihr
     * senkrecht hinuntergraebt (wie ein Spieler, der sich erst an die Grenze stellt). true = ist noch dabei.
     * Klappt das nicht (z.B. Abgrund daneben), geht er schraeg hinunter wie sonst.
     */
    private static boolean descendToBorder(Minecraft mc, Level level, LocalPlayer p, BlockPos feet) {
        boolean alongX = mainX != 0;
        int col = alongX ? feet.getZ() : feet.getX();
        int want = alongX ? axisZ : axisX;
        if (col == want && p.onGround() || borderFails >= 2 || Math.abs(col - want) > 9) {
            borderFirst = false;
            if (walkGoal != null) stopWalker(mc);
            walkGoal = null;
            return false;
        }
        action = "Geht zur Chunk-Grenze";
        if (walkGoal == null || !Walker.isActive()) {
            startWalkExact(new BlockPos(alongX ? feet.getX() : axisX, feet.getY(), alongX ? axisZ : feet.getZ()));
        }
        String r = tickWalker(mc, level, p);
        if (r != null) {
            walkGoal = null;
            BlockPos now = feet(p);
            if ((alongX ? now.getZ() : now.getX()) != want) borderFails++;
        }
        return true;
    }

    private static void tunnel(Minecraft mc, Level level, LocalPlayer p) {
        BlockPos feet = feet(p);
        if (sprintCooldown > 0) sprintCooldown--;
        // Schritt geschafft, sobald er im naechsten Feld steht (nicht erst in der Mitte: sonst laeuft er
        // durch und zaehlt nie einen Schritt)
        if (stepTarget != null) {
            if (feet.equals(stepTarget)) {
                stepTarget = null;
                stepDone();
                // kommt er in der Hauptrichtung nicht mehr voran (laeuft nur hin und her)? Dann aufgeben
                int along = (feet.getX() - axisX) * mainX + (feet.getZ() - axisZ) * mainZ;
                if (along > bestAlong) {
                    bestAlong = along;
                    noAdvance = 0;
                    backtracks = 0; // wirklich weitergekommen: Zuruecklaufen ist wieder erlaubt
                } else if (++noAdvance > 30 + 4 * lavaDistance) {
                    giveUp(mc, "Weg blockiert – Debris Miner angehalten", "Weg blockiert");
                    return;
                }
            } else if (!feet.equals(stepTarget.offset(-curX, 0, -curZ))) {
                stepTarget = null; // woanders gelandet (geschoben, Umweg): neu ansetzen
            }
        }
        // Block gerade weg: kurz warten, bis der Server die Nachbarn zeigt (Anti-Xray), dann pruefen
        if (digPos != null && !solid(level, digPos)) {
            remember(digPos);
            digPos = null;
            wait = scanDelay + (humanize ? Look.rng().nextInt(4) : 0); // menschlich: nicht immer gleich schnell
        }
        if (wait > 0) {
            wait--;
            return;
        }
        action = "Gräbt Tunnel";

        // nicht (mehr) auf der Tunnelhoehe, z.B. in eine Grube gefallen: erst zurueck auf die Hoehe
        if (p.onGround() && feet.getY() != tunnelY) {
            stepTarget = null;
            walkGoal = null;
            walkerFails = 0;
            phase = Phase.DESCEND;
            return;
        }

        // 1. Antiker Schrott direkt am Tunnel?
        scanTunnel(level, feet);
        if (!targets.isEmpty()) {
            beginVein(mc, feet);
            return;
        }
        // 1b. Andere Funde (z.B. Gold, Quarz), die neben oder hinter ihm liegen geblieben sind: kurz holen
        if (collect && collectOther && Pickup.has(mc, p, want(p))) {
            resume = feet;
            resumeX = curX;
            resumeZ = curZ;
            resumeSide = sideLeft;
            stopWalker(mc);
            digPos = null;
            stepTarget = null;
            phase = Phase.COLLECT;
            ticks = 0;
            walkerFails = 0;
            stuckTicks = 0;
            ignoredItems.clear();
            collectItem = -1;
            return;
        }

        // 2. Essen, Nachschub, Werkzeug
        if (needEat(p)) {
            startEat(Phase.TUNNEL);
            return;
        }
        int sup = supplies(mc, p);
        if (sup == BUSY) return;
        if (sup == NO_TOOL) {
            noTool(mc);
            return;
        }

        // 3. Gestein wegwerfen (nach hinten, damit er es nicht gleich wieder aufsammelt) - beim Durchsprinten
        //    erst, wenn das Inventar fast voll ist (sonst muesste er alle paar Sekunden anhalten)
        if (!(sprintRun && freeSlots(p) > 3) && throwJunk(p)) return;

        // 4. Laenge erreicht?
        if (maxLength > 0 && bestAlong >= maxLength) { // so weit in der Hauptrichtung vorangekommen
            stop(mc, "Tunnel fertig: " + tunnelLength + " Blöcke, " + debrisMined + " Debris");
            return;
        }

        // 5. Nach dem Zuruecklaufen: sofort seitlich ausweichen
        if (forceSide) {
            forceSide = false;
            if (!chooseSide(level, feet, false)) {
                hazard(mc, level, p, feet, true);
            }
            return;
        }

        // 5b. Nach einem Umweg (wenn das Hindernis hinter ihm liegt) zurueck auf die urspruengliche Achse
        if ((returnAxis || chunkBorder) && sideLeft == 0 && curX == mainX && curZ == mainZ
                && sinceSide >= lavaDistance + 2) {
            int off = (feet.getX() - axisX) * mainZ - (feet.getZ() - axisZ) * mainX; // > 0: links der Achse
            if (off != 0) {
                int sx = off > 0 ? -mainZ : mainZ;
                int sz = off > 0 ? mainX : -mainX;
                if (lavaAhead(level, feet, sx, sz, lavaDistance, lavaMode == 0) == 0 && breakableAhead(level, feet, sx, sz)) {
                    curX = sx;
                    curZ = sz;
                    sideLeft = Math.abs(off);
                    action = "Zurück zur Tunnelachse";
                    return;
                }
                sinceSide = 0; // spaeter noch einmal versuchen
            }
        }

        // 6. Lava voraus?
        if (lavaAvoid && lavaAhead(level, feet, curX, curZ, lavaDistance, lavaMode == 0) > 0) {
            hazard(mc, level, p, feet, true);
            return;
        }

        // 7. Naechsten Abschnitt freiraeumen (von oben nach unten)
        BlockPos next = feet.offset(curX, 0, curZ);
        BlockPos todo = null;
        for (BlockPos c : section(next, curX, curZ)) {
            if (isLava(level, c)) {
                hazard(mc, level, p, feet, true);
                return;
            }
            if (!solid(level, c)) continue;
            if (!safeToBreak(level, c)) {
                hazard(mc, level, p, feet, true);
                return;
            }
            // von oben nach unten, aber zuerst, was schon zu sehen ist (der oberste Block bei 1x3 ist erst
            // sichtbar, wenn der darunter weg ist); den angefangenen Block fertig machen
            if (c.equals(digPos)) {
                todo = c;
                break;
            }
            if (todo == null || TestClient.visiblePoint(level, p, todo) == null
                    && TestClient.visiblePoint(level, p, c) != null) {
                todo = c;
            }
        }
        if (todo != null) {
            sprintRun = false; // der Strahl war nicht schnell genug: diesen Block normal abbauen
            if (!mine(mc, level, p, todo, -1)) {
                hazard(mc, level, p, feet, false); // laesst sich nicht abbauen
            }
            return;
        }
        digPos = null;

        // 8. Boden unter dem naechsten Schritt (und unter ihm selbst - eine Ader kann ihn weggenommen haben)
        boolean ownFloor = !solid(level, feet.below()) && !isLava(level, feet.below());
        BlockPos floor = ownFloor ? feet.below() : next.below();
        if (!solid(level, floor) || isLava(level, floor)) {
            if (!ownFloor && !fillFloor && !isLava(level, floor)) {
                hazard(mc, level, p, feet, false); // Loch voraus: ausweichen statt zubauen
                return;
            }
            if (isLava(level, floor) && (!lavaAvoid || lavaMode == 0)) {
                hazard(mc, level, p, feet, true);
                return;
            }
            action = isLava(level, floor) ? "Baut Lava zu" : "Baut Boden";
            // er steht still in seinem Block - Ducken braucht es nur, wenn er an die Kante muss (unten)
            if (placeInto(mc, level, p, floor)) {
                placeFails = 0;
                wait = 2;
            } else {
                if (findFace(level, p, floor) == null && TestClient.findBuildSlot(mc) >= 0) {
                    // Wie beim Brueckenbauen: geduckt bis an die Kante, dann ist die Seite des eigenen
                    // Bodenblocks zu sehen, an die der neue Block gesetzt wird
                    push(p, curX, curZ, 0.0);
                    kSneak = true;
                }
                if (++placeFails > 80) {
                    placeFails = 0;
                    hazard(mc, level, p, feet, false);
                }
            }
            return;
        }

        // 9. Netherrack durchsprinten (Sofort-Abbau) - sonst einen Schritt weiter
        if (sprintMine && sprintTick(level, p, feet)) return;
        sprintRun = false;
        stepTarget = next;
        moveTo(p, next, Look.yawOf(curX, curZ));
        if (stuckTicks > 80) {
            stuckTicks = 0;
            stepTarget = null;
            hazard(mc, level, p, feet, false);
        }
    }

    // ---------- Durchsprinten (Sofort-Abbau) ----------

    private static final int SPRINT_LOOK = 6;      // so viele Felder voraus muss alles passen (Reichweite + Bremsweg)
    private static boolean sprintRun;
    private static int sprintCooldown;
    private static int sprintStill;
    private static boolean sprintPushed;
    private static double sprintLastX;
    private static double sprintLastZ;
    private static BlockPos sprintFeet;
    private static final List<BlockPos> sprintWatch = new ArrayList<>();

    /**
     * Netherrack durchsprinten: Baut das Werkzeug die Tunnelbloecke sofort ab (z.B. Effizienz V), haelt er
     * wie ein Spieler die Angriffstaste gedrueckt, schaut mit festem Winkel schraeg nach unten und sprintet.
     * Minecraft baut dann jeden Tick den Block unter dem Fadenkreuz ab: zuerst den Fussblock weiter vorne,
     * beim Naeherkommen den Kopfblock. Durchgerechnet (Strahl wie in Minecraft, Sprint-Physik): bei Reichweite
     * 4,5 klappt es mit 18 bis 20 Grad nach unten; flacher bleibt der Fussblock stehen, steiler trifft der
     * Strahl den Boden. Nur im 1x2-Tunnel und nur, wenn die naechsten Felder sicher sind.
     * true = sprintet (der Rest des Ticks entfaellt).
     */
    private static boolean sprintTick(Level level, LocalPlayer p, BlockPos feet) {
        double eyeH = p.getEyeY() - p.getY();
        double reach = p.blockInteractionRange();
        float pitch = sprintPitch(eyeH, reach);
        if (!canSprint(level, p, feet, pitch)) return false;
        if (!sprintRun) {
            // Einstieg: die erste feste Wand muss etwas weg sein, sonst erreicht der flache Strahl den
            // Fussblock direkt davor nicht (er wuerde dagegen laufen)
            if (wallDistance(level, p, feet) < 1.15) return false;
            sprintRun = true;
            sprintFeet = feet;
            sprintStill = 0;
            sprintPushed = false;
            sprintWatch.clear();
        }
        stepTarget = null;

        // abgebaute Bloecke merken (Anti-Xray: daneben ist echt) und Schritte zaehlen wie beim normalen Graben
        for (BlockPos b : sprintWatch) {
            if (!solid(level, b)) remember(b);
        }
        sprintWatch.clear();
        for (int k = 1; k <= SPRINT_LOOK; k++) {
            for (BlockPos c : section(feet.offset(curX * k, 0, curZ * k), curX, curZ)) {
                if (solid(level, c)) sprintWatch.add(c);
            }
        }
        if (!feet.equals(sprintFeet)) {
            sprintFeet = feet;
            tunnelLength++;
            sinceSide++;
            stuckTicks = 0;
            sealTries = 0;
            placeFails = 0;
            int along = (feet.getX() - axisX) * mainX + (feet.getZ() - axisZ) * mainZ;
            if (along > bestAlong) {
                bestAlong = along;
                noAdvance = 0;
                backtracks = 0;
            }
        }

        // kommt nicht voran (Block bricht doch nicht sofort o.ae.): normal weitergraben
        double moved = Math.abs(p.getX() - sprintLastX) + Math.abs(p.getZ() - sprintLastZ);
        sprintLastX = p.getX();
        sprintLastZ = p.getZ();
        if (sprintPushed && moved < 0.02) {
            if (++sprintStill > 8) {
                sprintRun = false;
                sprintCooldown = 60;
                return false;
            }
        } else {
            sprintStill = 0;
        }

        action = "Sprintet durch Netherrack";
        // Blick: fester Winkel nach unten; seitlich auf einen Punkt der Tunnelmitte 4 Bloecke voraus (so bleibt
        // er in der Mitte, ohne A/D zu tippen, und der Strahl trifft nie die Seitenwand)
        double tx = curX != 0 ? p.getX() + curX * 4.0 : feet.getX() + 0.5;
        double tz = curZ != 0 ? p.getZ() + curZ * 4.0 : feet.getZ() + 0.5;
        float yaw = Look.yawOf(tx - p.getX(), tz - p.getZ());
        Look.turn(p, yaw, pitch, 30.0f, 25.0f, 0.0f);
        float ey = Math.abs(wrapDeg(yaw - p.getYRot()));
        float ep = Math.abs(pitch - p.getXRot());
        if (ey > 3.0f || ep > 1.2f) {
            sprintPushed = false; // erst fertig drehen, sonst koennte der Strahl den Boden treffen
            return true;
        }
        kFwd = true;
        kSprint = true;
        sprintPushed = true;
        kAttack = sprintSafeHit(level, p, feet, reach);
        digging = kAttack;
        return true;
    }

    /** Fester Blickwinkel nach unten (Grad); NaN = geht nicht. */
    private static float sprintPitch(double eyeH, double reach) {
        if (reach <= 0.0 || eyeH <= 1.2) return Float.NaN;
        // steiler als das erreicht der Strahl den Boden (Bodenoberkante liegt eyeH unter dem Auge)
        double floorLimit = Math.toDegrees(Math.asin(Math.min(1.0, eyeH / reach)));
        float pitch = (float) Math.min(19.0, floorLimit - 1.8);
        return pitch < 14.0f ? Float.NaN : pitch; // sehr grosse Reichweite: zu flach, lieber normal graben
    }

    /** Laesst sich ab hier durchsprinten? */
    private static boolean canSprint(Level level, LocalPlayer p, BlockPos feet, float pitch) {
        if (pattern != 0 || Float.isNaN(pitch) || sprintCooldown > 0) return false;
        if (curX != mainX || curZ != mainZ || sideLeft != 0) return false;
        if (!p.onGround() || p.isInWater() || Math.abs(p.getY() - tunnelY) > 0.01) return false;
        if (maxLength > 0 && bestAlong + SPRINT_LOOK >= maxLength) return false; // das letzte Stueck normal
        double off = curX != 0 ? p.getZ() - (feet.getZ() + 0.5) : p.getX() - (feet.getX() + 0.5);
        if (Math.abs(off) > 0.22) return false;
        if (!fullBlock(level, feet.below())) return false;
        // Werkzeug wie beim normalen Graben (fuer den ersten festen Block)
        boolean toolSet = false;
        for (int k = 1; k <= SPRINT_LOOK; k++) {
            BlockPos base = feet.offset(curX * k, 0, curZ * k);
            for (BlockPos c : section(base, curX, curZ)) {
                if (!level.getFluidState(c).isEmpty()) return false;
                if (passable(level, c)) continue;
                BlockState bs = level.getBlockState(c);
                if (bs.is(Blocks.ANCIENT_DEBRIS)) return false;
                if (!toolSet) {
                    toolSet = true;
                    int slot = bestTool(p, bs, bs.getDestroySpeed(level, c));
                    if (slot >= 0) p.getInventory().setSelectedSlot(slot);
                }
                if (bs.getDestroyProgress(p, level, c) < 1.0f) return false; // bricht nicht sofort
                if (!safeToBreak(level, c)) return false;
            }
            // Kies o.ae. ueber dem Kopfblock wuerde in den Tunnel fallen; der Boden muss ein voller Block sein
            if (level.getBlockState(base.above(2)).getBlock() instanceof FallingBlock) return false;
            if (!fullBlock(level, base.below()) || isLava(level, base.below())) return false;
        }
        return lavaAhead(level, feet, curX, curZ, SPRINT_LOOK + 1, true) == 0;
    }

    /** Abstand vom Spieler (Auge) bis zur ersten festen Tunnelwand voraus; gross, wenn frei. */
    private static double wallDistance(Level level, LocalPlayer p, BlockPos feet) {
        for (int k = 1; k <= SPRINT_LOOK; k++) {
            BlockPos base = feet.offset(curX * k, 0, curZ * k);
            for (BlockPos c : section(base, curX, curZ)) {
                if (!solid(level, c)) continue;
                double pos = curX != 0 ? p.getX() : p.getZ();
                int dir = curX != 0 ? curX : curZ;
                int b = curX != 0 ? base.getX() : base.getZ();
                double face = dir > 0 ? b : b + 1;
                return (face - pos) * dir;
            }
        }
        return 99.0;
    }

    /**
     * Darf die Taste gedrueckt bleiben? Nur, wenn das Fadenkreuz auf einem Tunnelblock voraus liegt (oder auf
     * nichts) - nie auf Boden, Decke oder Seitenwand.
     */
    private static boolean sprintSafeHit(Level level, LocalPlayer p, BlockPos feet, double reach) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(reach));
        BlockHitResult r = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        if (r == null || r.getType() != HitResult.Type.BLOCK) return true;
        BlockPos h = r.getBlockPos();
        int dx = h.getX() - feet.getX();
        int dz = h.getZ() - feet.getZ();
        int along = dx * curX + dz * curZ;
        int side = dx * curZ - dz * curX;
        if (side != 0 || along < 1 || along > SPRINT_LOOK) return false;
        if (h.getY() != tunnelY && h.getY() != tunnelY + 1) return false;
        BlockState bs = level.getBlockState(h);
        return !bs.is(Blocks.ANCIENT_DEBRIS) && bs.getDestroyProgress(p, level, h) >= 1.0f && safeToBreak(level, h);
    }

    private static boolean fullBlock(Level level, BlockPos pos) {
        return level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
    }

    private static float wrapDeg(float d) {
        d %= 360.0f;
        if (d >= 180.0f) d -= 360.0f;
        if (d < -180.0f) d += 360.0f;
        return d;
    }

    /** Freie Plaetze im Inventar (ohne Ruestung und zweite Hand). */
    private static int freeSlots(LocalPlayer p) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            if (p.getInventory().getItem(i).isEmpty()) n++;
        }
        return n;
    }

    private static void stepDone() {
        tunnelLength++;
        stuckTicks = 0;
        sealTries = 0;
        placeFails = 0;
        wait = scanDelay;
        if (sideLeft > 0 && --sideLeft == 0) {
            curX = mainX;
            curZ = mainZ;
            sinceSide = 0;
        } else if (sideLeft == 0) {
            sinceSide++;
        }
    }

    /** Querschnitt des Tunnels an Position base (Fusshoehe), oberste Bloecke zuerst. */
    private static List<BlockPos> section(BlockPos base, int dx, int dz) {
        List<BlockPos> out = new ArrayList<>(4);
        switch (pattern) {
            case 1 -> {
                out.add(base.above(2));
                out.add(base.above());
                out.add(base);
            }
            case 2 -> {
                int rx = -dz; // rechts von der Laufrichtung
                int rz = dx;
                out.add(base.above());
                out.add(base.offset(rx, 1, rz));
                out.add(base);
                out.add(base.offset(rx, 0, rz));
            }
            default -> {
                out.add(base.above());
                out.add(base);
            }
        }
        return out;
    }

    // =====================================================================================
    // Lava und Hindernisse
    // =====================================================================================

    /**
     * Abstand (1..dist) zur ersten Lava im Weg, sonst 0. Geprueft werden die Bloecke, die abgebaut werden,
     * und alles, aus dem Lava hineinlaufen koennte (darueber und seitlich); mit floor auch der Boden.
     */
    private static int lavaAhead(Level level, BlockPos from, int dx, int dz, int dist, boolean floor) {
        for (int k = 1; k <= Math.max(1, dist); k++) {
            BlockPos base = from.offset(dx * k, 0, dz * k);
            for (BlockPos c : section(base, dx, dz)) {
                if (isLava(level, c) || isLava(level, c.above())) return k;
                if (isLava(level, c.north()) || isLava(level, c.south())
                        || isLava(level, c.east()) || isLava(level, c.west())) return k;
            }
            if (floor && isLava(level, base.below())) return k;
        }
        return 0;
    }

    /** Lava oder Hindernis vor dem Tunnel: zubauen (wenn eingestellt), seitlich ausweichen oder zuruecklaufen. */
    private static void hazard(Minecraft mc, Level level, LocalPlayer p, BlockPos feet, boolean lava) {
        digPos = null;
        if (lava && !lavaAvoid) {
            stop(mc, "Lava im Weg – Debris Miner angehalten");
            return;
        }
        if (lava && lavaMode == 1 && sealTries < 10) {
            BlockPos l = sealTarget(level, p, feet);
            if (l != null) {
                action = "Baut Lava zu";
                if (placeInto(mc, level, p, l)) {
                    sealTries++;
                    wait = 3;
                }
                return;
            }
        }
        if (chooseSide(level, feet, true)) return;
        if (lavaBacktrack && backtracks < 3) {
            backtracks++;
            int back = lavaDistance + 3;
            BlockPos target = feet.offset(-mainX * back, 0, -mainZ * back);
            curX = mainX;
            curZ = mainZ;
            sideLeft = 0;
            startWalk(target);
            phase = Phase.BACKTRACK;
            action = "Geht zurück (Lava)";
            return;
        }
        if (lava) {
            giveUp(mc, "Lava ringsum – Debris Miner angehalten", "Lava ringsum");
        } else {
            giveUp(mc, "Weg blockiert – Debris Miner angehalten", "Weg blockiert");
        }
    }

    /**
     * Seitlich ausweichen: ein paar Bloecke nach links oder rechts graben, danach geht es parallel in der
     * Hauptrichtung weiter. Bei einem laufenden Umweg wird auch die Hauptrichtung wieder versucht.
     */
    private static boolean chooseSide(Level level, BlockPos feet, boolean allowMain) {
        int len = lavaDistance + 2;
        boolean sideways = curX != mainX || curZ != mainZ;
        if (allowMain && sideways && lavaAhead(level, feet, mainX, mainZ, lavaDistance, lavaMode == 0) == 0
                && breakableAhead(level, feet, mainX, mainZ)) {
            curX = mainX;
            curZ = mainZ;
            sideLeft = 0;
            action = "Umgeht Lava";
            return true;
        }
        int first = lastSide != 0 ? lastSide : 1;
        for (int sign : new int[] {first, -first}) {
            int sx = sign == 1 ? mainZ : -mainZ;   // links von der Hauptrichtung
            int sz = sign == 1 ? -mainX : mainX;
            if (sx == -curX && sz == -curZ) continue; // nicht in den eigenen Umweg zurueck
            if (sideways && sx == curX && sz == curZ) continue; // genau dort ging es nicht weiter
            if (lavaAhead(level, feet, sx, sz, len, lavaMode == 0) > 0) continue;
            if (!breakableAhead(level, feet, sx, sz)) continue;
            curX = sx;
            curZ = sz;
            sideLeft = len;
            lastSide = sign;
            sinceSide = 0;
            action = "Umgeht Lava";
            return true;
        }
        return false;
    }

    /** Laesst sich der naechste Abschnitt in dieser Richtung ueberhaupt abbauen (kein Grundgestein o.ae.)? */
    private static boolean breakableAhead(Level level, BlockPos feet, int dx, int dz) {
        for (BlockPos c : section(feet.offset(dx, 0, dz), dx, dz)) {
            BlockState bs = level.getBlockState(c);
            if (bs.getDestroySpeed(level, c) < 0.0f) return false;
        }
        return true;
    }

    private static void backtrack(Minecraft mc, Level level, LocalPlayer p) {
        String r = tickWalker(mc, level, p);
        if (r == null) return;
        walkGoal = null;
        phase = Phase.TUNNEL;
        forceSide = true;
        wait = scanDelay;
    }

    /** Sichtbare Lava im Bereich vor dem Tunnel, die sich jetzt zubauen laesst (oder null). */
    private static BlockPos sealTarget(Level level, LocalPlayer p, BlockPos feet) {
        for (int k = 1; k <= Math.max(1, lavaDistance); k++) {
            BlockPos base = feet.offset(curX * k, 0, curZ * k);
            List<BlockPos> check = new ArrayList<>();
            for (BlockPos c : section(base, curX, curZ)) {
                check.add(c);
                check.add(c.above());
                check.add(c.north());
                check.add(c.south());
                check.add(c.east());
                check.add(c.west());
            }
            check.add(base.below());
            for (BlockPos c : check) {
                if (isLava(level, c) && findFace(level, p, c) != null) return c;
            }
        }
        return null;
    }

    // =====================================================================================
    // Debris-Ader
    // =====================================================================================

    /** Bloecke rund um den Tunnelquerschnitt, in dem der Spieler steht. */
    private static void scanTunnel(Level level, BlockPos feet) {
        for (BlockPos c : section(feet, curX, curZ)) scanAround(level, c);
    }

    private static void scanAround(Level level, BlockPos c) {
        int r = Math.max(1, Math.min(3, scanRadius));
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) consider(level, c.offset(dx, dy, dz));
                }
            }
        }
    }

    private static void consider(Level level, BlockPos b) {
        if (ignored.contains(b.asLong()) || targets.contains(b)) return;
        if (!level.getBlockState(b).is(Blocks.ANCIENT_DEBRIS)) return;
        if (onlyExposed && !trusted(level, b)) return;
        int top = tunnelY + (pattern == 1 ? 2 : 1);
        if (!mineFloor && b.getY() < tunnelY || !mineCeiling && b.getY() > top) return;
        if (!digToHidden) {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null || TestClient.visiblePoint(level, p, b) == null) return; // nur, was schon zu sehen ist
        }
        targets.add(b);
    }

    /**
     * Ist das sicher echtes Debris? Mit Anti-Xray zeigt der Server echte Bloecke nur, wenn sie an Luft grenzen
     * oder nahe an einem gerade abgebauten Block bzw. am Spieler liegen; im verdeckten Gestein koennen sonst
     * falsche Erze stehen.
     */
    private static boolean trusted(Level level, BlockPos b) {
        if (exposed(level, b)) return true;
        for (long m : mined) {
            BlockPos q = BlockPos.of(m);
            if (Math.abs(q.getX() - b.getX()) <= 2 && Math.abs(q.getY() - b.getY()) <= 2
                    && Math.abs(q.getZ() - b.getZ()) <= 2) return true;
        }
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return false;
        BlockPos f = feet(p);
        return Math.abs(f.getX() - b.getX()) <= 1 && Math.abs(f.getZ() - b.getZ()) <= 1
                && b.getY() >= f.getY() - 1 && b.getY() <= f.getY() + 2;
    }

    /** Selbst abgebauten Block merken (nur die letzten 256). */
    private static void remember(BlockPos b) {
        mined.remove(b.asLong());
        mined.add(b.asLong());
        if (mined.size() > 256) {
            var it = mined.iterator();
            it.next();
            it.remove();
        }
    }

    private static void beginVein(Minecraft mc, BlockPos feet) {
        resume = feet;
        resumeX = curX;
        resumeZ = curZ;
        resumeSide = sideLeft;
        BlockPos first = targets.iterator().next();
        if (chatFound) say(mc, "Antiker Schrott gefunden bei " + first.getX() + " " + first.getY() + " " + first.getZ());
        if (foundSound && mc.level != null && mc.player != null) {
            mc.level.playLocalSound(mc.player.getX(), mc.player.getY(), mc.player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.PLAYERS, 0.7f, 1.4f, false);
        }
        phase = Phase.VEIN;
        digPos = null;
        walkGoal = null;
        sealTries = 0;
        ticks = 0;
    }

    private static void vein(Minecraft mc, Level level, LocalPlayer p) {
        action = "Baut Debris ab";
        // Abgebaute Ziele: zaehlen und die neu freigelegten Nachbarn pruefen (die Ader geht vielleicht weiter)
        var it = targets.iterator();
        while (it.hasNext()) {
            BlockPos t = it.next();
            if (level.getBlockState(t).is(Blocks.ANCIENT_DEBRIS)) continue;
            it.remove();
            remember(t);
            if (t.equals(digPos)) {
                debrisMined++;
                digPos = null;
                if (veinMine) rescan.add(t);
                wait = scanDelay;
            }
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (!rescan.isEmpty()) {
            for (BlockPos b : rescan) scanAround(level, b);
            rescan.clear();
        }
        BlockPos feet = feet(p);
        scanAround(level, feet);
        scanAround(level, feet.above());
        if (targets.isEmpty()) {
            stopWalker(mc);
            digPos = null;
            phase = collect ? Phase.COLLECT : Phase.RETURN;
            ticks = 0;
            walkerFails = 0;
            stuckTicks = 0;
            ignoredItems.clear();
            collectItem = -1;
            return;
        }
        int sup = supplies(mc, p);
        if (sup == BUSY) return;
        if (sup == NO_TOOL) {
            noTool(mc);
            return;
        }

        if (noEmptySlot(p) && !Walker.isActive() && throwJunk(p)) return;

        // beim gewaehlten Ziel bleiben, solange es noch da ist (sonst springt die Wahl hin und her)
        BlockPos t = veinTarget != null && targets.contains(veinTarget) ? veinTarget : nearest(p);
        if (!t.equals(veinTarget)) {
            veinTarget = t;
            veinTicks = 0;
        }
        if (++veinTicks > 1800) { // 90 s an einem Block: aufgeben
            stopWalker(mc);
            skip(mc, t, "Debris nicht erreichbar");
            return;
        }
        double[] pt = TestClient.visiblePoint(level, p, t);
        if (pt != null && !(Walker.isActive() && Walker.inCriticalMove(p))) {
            stopWalker(mc);
            if (!safeToBreak(level, t)) {
                if (lavaAvoid && lavaMode == 1 && sealTries < 6) {
                    BlockPos l = lavaNeighborToSeal(level, p, t);
                    if (l != null) {
                        action = "Baut Lava zu";
                        if (placeInto(mc, level, p, l)) {
                            sealTries++;
                            wait = 3;
                        }
                        return;
                    }
                }
                skip(mc, t, "Debris neben Lava übersprungen");
                return;
            }
            if (!floorSafe(level, p, t)) {
                skip(mc, t, "Debris über Lava oder Loch übersprungen");
                return;
            }
            if (!mine(mc, level, p, t, -1)) skip(mc, t, "Debris ließ sich nicht abbauen");
            return;
        }
        // nicht zu sehen: erst versuchen, vom Tunnel aus den Block davor abzubauen (bleibt im Tunnel) ...
        if (!Walker.isActive()) {
            BlockPos o = obstruction(level, p, t);
            if (o != null && !badDig.contains(o.asLong()) && Look.eyeDist2(p, o) <= REACH * REACH && solid(level, o)
                    && level.getBlockState(o).getDestroySpeed(level, o) >= 0.0f && safeToBreak(level, o)
                    && floorSafe(level, p, o)) {
                action = "Gräbt zum Debris";
                if (!mine(mc, level, p, o, -1)) badDig.add(o.asLong());
                return;
            }
        }
        // ... sonst hinlaufen (die Wegsuche graebt sich sicher hin)
        if (walkGoal == null || !walkGoal.equals(t) || !Walker.isActive()) {
            Walker.start(t.getX(), t.getY(), t.getZ(), true);
            walkGoal = t;
        }
        String r = tickWalker(mc, level, p);
        if (r != null) {
            walkGoal = null;
            if (!Walker.ARRIVED.equals(r) || TestClient.visiblePoint(level, p, t) == null) {
                skip(mc, t, "Debris nicht erreichbar");
            }
        }
    }

    /** Erster feste Block zwischen Auge und Mitte von t (null = t selbst ist das Erste oder nichts im Weg). */
    private static BlockPos obstruction(Level level, LocalPlayer p, BlockPos t) {
        Vec3 eye = p.getEyePosition();
        Vec3 c = new Vec3(t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5);
        BlockHitResult r = level.clip(new ClipContext(eye, c, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        if (r == null || r.getType() != HitResult.Type.BLOCK || r.getBlockPos() == null) return null;
        BlockPos o = r.getBlockPos();
        return o.equals(t) ? null : o;
    }

    private static BlockPos nearest(LocalPlayer p) {
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (BlockPos b : targets) {
            double d = Look.eyeDist2(p, b);
            if (d < bd) {
                bd = d;
                best = b;
            }
        }
        return best;
    }

    private static void skip(Minecraft mc, BlockPos t, String why) {
        ignored.add(t.asLong());
        targets.remove(t);
        digPos = null;
        sealTries = 0;
        if (why != null) say(mc, Lang.t(why) + " (" + t.getX() + " " + t.getY() + " " + t.getZ() + ")");
    }

    /** Liegt der Block unter dem Spieler, muss darunter fester Boden sein (sonst faellt er in Lava/ein Loch). */
    private static boolean floorSafe(Level level, LocalPlayer p, BlockPos t) {
        AABB bb = p.getBoundingBox();
        boolean under = t.getY() < bb.minY && t.getY() >= bb.minY - 1.01
                && bb.maxX > t.getX() && bb.minX < t.getX() + 1 && bb.maxZ > t.getZ() && bb.minZ < t.getZ() + 1;
        if (!under) return true;
        BlockPos below = t.below();
        return solid(level, below) && !isLava(level, below);
    }

    /** Lava neben dem Debris, die man von hier aus zubauen kann. */
    private static BlockPos lavaNeighborToSeal(Level level, LocalPlayer p, BlockPos t) {
        for (BlockPos n : new BlockPos[] {t.above(), t.north(), t.south(), t.east(), t.west()}) {
            if (isLava(level, n) && findFace(level, p, n) != null) return n;
        }
        return null;
    }

    // =====================================================================================
    // Einsammeln und Rueckweg
    // =====================================================================================

    private static void collect(Minecraft mc, Level level, LocalPlayer p) {
        action = "Sammelt ein";
        if (noEmptySlot(p) && throwJunk(p)) return; // Platz schaffen, sonst passt das Debris nicht rein
        if (++ticks > Math.max(1, collectTimeout) * 20) {
            endCollect(mc);
            return;
        }
        ItemEntity item = nearestDrop(mc, p);
        if (item == null) {
            endCollect(mc);
            return;
        }
        if (!collectPits && item.getY() < p.getY() - 0.5) {
            ignoreItem(item); // liegt in einer Grube: so eingestellt, nicht hinunter
            return;
        }
        if (item.getId() != collectItem) {
            collectItem = item.getId();
            bestDist = Double.MAX_VALUE;
            noProgress = 0;
            nudge = 0;
        }
        if (inPickupRange(p, item)) {
            stopWalker(mc); // wird gleich aufgesammelt
            if (++arrivedTicks > 60) ignoreItem(item);
            return;
        }
        if (nudge > 0) {
            // festgehangen (z.B. am Rand einer Grube): ohne Ducken direkt hinschieben, dann faellt er hinein
            nudge--;
            push(p, item.getX() - p.getX(), item.getZ() - p.getZ(), 0.05);
            kSneak = false;
            return;
        }
        double gx = item.getX() - p.getX();
        double gy = item.getY() - p.getY();
        double gz = item.getZ() - p.getZ();
        double gd = gx * gx + gy * gy + gz * gz;
        if (gd < bestDist - 0.1) {
            bestDist = gd;
            noProgress = 0;
        } else if (++noProgress > 60) {
            noProgress = 0;
            bestDist = Double.MAX_VALUE;
            stopWalker(mc);
            nudge = 25;
            return;
        }
        // Liegt es zu hoch (oben in einer Nische)? Dann den Block darunter abbauen, damit es herunterfaellt -
        // statt hochzubauen
        BlockPos at = BlockPos.containing(item.getX(), item.getY() + 0.1, item.getZ());
        if (collectHigh && at.getY() > feet(p).getY() + 2 && !Walker.isActive()) {
            BlockPos sup = at.below();
            if (solid(level, sup) && !badDig.contains(sup.asLong()) && Look.eyeDist2(p, sup) <= REACH * REACH
                    && level.getBlockState(sup).getDestroySpeed(level, sup) >= 0.0f && safeToBreak(level, sup)
                    && TestClient.visiblePoint(level, p, sup) != null) {
                if (!mine(mc, level, p, sup, -1)) badDig.add(sup.asLong());
                return;
            }
        }
        BlockPos ip = pickupSpot(level, p, item);
        if (walkGoal == null || !walkGoal.equals(ip) || !Walker.isActive()) {
            if (walkGoal != null && !Walker.isActive()) {
                // schon hingelaufen, aber nicht drangekommen
                if (++arrivedTicks > 40) {
                    ignoreItem(item);
                    arrivedTicks = 0;
                    walkGoal = null;
                }
                return;
            }
            startWalkExact(ip);
            arrivedTicks = 0;
        }
        String r = tickWalker(mc, level, p);
        if (r != null && !Walker.ARRIVED.equals(r)) {
            ignoreItem(item);
            walkGoal = null;
        }
    }

    /** Nah genug, dass Minecraft das Item gleich aufsammelt (Sammelbereich: 1 Block um den Spieler herum). */
    private static boolean inPickupRange(LocalPlayer p, ItemEntity item) {
        double dy = item.getY() - p.getY();
        return Math.abs(item.getX() - p.getX()) < 1.2 && Math.abs(item.getZ() - p.getZ()) < 1.2 && dy > -0.6
                && dy < 2.2;
    }

    /**
     * Wo er sich hinstellen soll, um das Item aufzusammeln: liegt es oben in einer Nische (z.B. Debris aus der
     * Decke), reicht ein freies Feld darunter oder daneben auf Tunnelhoehe - dann muss er nicht hochbauen.
     */
    private static BlockPos pickupSpot(Level level, LocalPlayer p, ItemEntity item) {
        BlockPos ip = BlockPos.containing(item.getX(), item.getY() + 0.1, item.getZ());
        int fy = feet(p).getY();
        if (ip.getY() <= fy || ip.getY() > fy + 2) return ip;
        BlockPos low = new BlockPos(ip.getX(), fy, ip.getZ());
        if (open(level, low)) return low;
        for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos n = low.relative(d);
            if (open(level, n) && solid(level, n.below())) return n;
        }
        return ip;
    }

    /** Gedropptes Debris (und eigene Shulker-Kisten/Endertruhe) im Umkreis. */
    private static void ignoreItem(ItemEntity item) {
        ignoredItems.add(item.getId());
        Pickup.ignore(item);
    }

    /** Anderer eigener Fund (aus einem selbst abgebauten Block), der mit soll? */
    private static boolean wantOther(LocalPlayer p, ItemEntity ie) {
        ItemStack st = ie.getItem();
        return collectOther && Pickup.isOwn(ie) && !isJunk(st) && Pickup.canTake(p, st);
    }

    private static final Pickup.Want WANT = new Pickup.Want();

    /** Fuer den Tunnel: liegengebliebene Funde neben oder hinter ihm (voraus laeuft er sowieso drueber). */
    private static Pickup.Want want(LocalPlayer p) {
        WANT.radius = collectRadius;
        WANT.minAge = 12; // kurz landen lassen - er laeuft schnell weiter, sonst ist es schon zu weit weg
        WANT.dirX = curX;
        WANT.dirZ = curZ;
        WANT.minY = tunnelY - (collectPits ? 3.0 : 0.5);
        WANT.maxY = tunnelY + 3.5;
        WANT.ok = (st, source) -> !isJunk(st);
        return WANT;
    }

    private static ItemEntity nearestDrop(Minecraft mc, LocalPlayer p) {
        ItemEntity best = null;
        double bd = Double.MAX_VALUE;
        double r = Math.max(2, collectRadius);
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(r, 4.0, r))) {
            if (!(e instanceof ItemEntity ie) || !ie.isAlive() || ignoredItems.contains(ie.getId())) continue;
            ItemStack st = ie.getItem();
            boolean want = st.is(Items.ANCIENT_DEBRIS) || isShulker(st) || st.is(Items.ENDER_CHEST)
                    || wantOther(p, ie);
            if (!want) continue;
            double d = ie.distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = ie;
            }
        }
        return best;
    }

    private static void endCollect(Minecraft mc) {
        // was jetzt noch an anderen Funden herumliegt (nicht erreicht, Zeit um): aufgeben, sonst geht er
        // vom Tunnel aus immer wieder hin
        LocalPlayer pl = mc.player;
        if (pl != null && mc.level != null) {
            double r = Math.max(2, collectRadius);
            for (Entity e : mc.level.getEntities(pl, pl.getBoundingBox().inflate(r, 4.0, r))) {
                if (e instanceof ItemEntity ie && ie.isAlive() && Pickup.isOwn(ie) && !ie.getItem().is(Items.ANCIENT_DEBRIS)) {
                    Pickup.ignore(ie);
                }
            }
        }
        stopWalker(mc);
        bestDist = Double.MAX_VALUE;
        noProgress = 0;
        nudge = 0;
        TestClient.forgetPlaced(); // auf dem Rueckweg darf er eigene Saeulen/Stufen wieder abbauen
        phase = Phase.RETURN;
        ticks = 0;
        walkerFails = 0;
        stuckTicks = 0;
    }

    private static void returnToAxis(Minecraft mc, Level level, LocalPlayer p) {
        action = "Kehrt zum Tunnel zurück";
        if (resume == null) {
            phase = Phase.TUNNEL;
            return;
        }
        BlockPos feet = feet(p);
        if (feet.equals(resume)) {
            stopWalker(mc);
            // in die Mitte des Feldes, dann geht es auf der alten Achse weiter
            if (moveTo(p, resume, Look.yawOf(resumeX, resumeZ)) || stuckTicks > 40) {
                curX = resumeX;
                curZ = resumeZ;
                sideLeft = resumeSide;
                phase = Phase.TUNNEL;
                wait = scanDelay;
                stuckTicks = 0;
                stepTarget = null;
            }
            return;
        }
        // direkt daneben (oder einen Block hoeher, z.B. auf einer Kante): das letzte Stueck ohne Wegsuche
        int up = feet.getY() - resume.getY();
        boolean adjacent = (up == 0 || up == 1)
                && Math.abs(feet.getX() - resume.getX()) + Math.abs(feet.getZ() - resume.getZ()) <= 1;
        // einen Block hoeher direkt daneben, aber ueber dem Tunnel ist die Decke (er steht z.B. auf einem
        // gesetzten Block im Tunnel): den Block unter sich abbauen und hinunterfallen, statt gegen die Decke zu laufen
        BlockPos under = feet.below();
        if (adjacent && up == 1 && open(level, resume) && solid(level, resume.above(2)) && p.onGround()
                && under.getY() == resume.getY() && solid(level, under) && safeToBreak(level, under)) {
            stopWalker(mc);
            if (!mine(mc, level, p, under, -1)) stuckTicks = 60; // geht nicht: Wegsuche
            return;
        }
        if (adjacent && open(level, resume) && stuckTicks < 60) {
            stopWalker(mc); // das letzte Stueck direkt
            moveTo(p, resume, Look.yawOf(resume.getX() - feet.getX(), resume.getZ() - feet.getZ()));
            if (up > 0) kSneak = false; // muss hinunter: nicht an der Kante festhalten
            return;
        }
        if (nudge > 0) {
            // festgehangen (z.B. an einer Kante): ohne Ducken direkt Richtung Tunnel schieben, damit er hinunterfaellt
            nudge--;
            push(p, resume.getX() + 0.5 - p.getX(), resume.getZ() + 0.5 - p.getZ(), 0.05);
            kSneak = false;
            return;
        }
        if (walkGoal == null || !Walker.isActive()) {
            if (walkerFails > 3) {
                if (feet.getY() == resume.getY() && p.onGround()) {
                    // Notfalls von hier aus parallel weitergraben
                    curX = resumeX;
                    curZ = resumeZ;
                    sideLeft = 0;
                    phase = Phase.TUNNEL;
                    return;
                }
                giveUp(mc, "Rückweg zum Tunnel nicht gefunden – Debris Miner angehalten", "Rückweg nicht gefunden");
                return;
            }
            startWalkExact(resume);
        }
        String r = tickWalker(mc, level, p);
        if (r != null) {
            walkGoal = null;
            if (!feet(p).equals(resume)) walkerFails++;
            return;
        }
        // haengt er fest (kommt nicht naeher)? Dann neu planen
        double ex = p.getX() - (resume.getX() + 0.5);
        double ey = p.getY() - resume.getY();
        double ez = p.getZ() - (resume.getZ() + 0.5);
        double d = ex * ex + ey * ey + ez * ez;
        if (d < bestDist - 0.25) {
            bestDist = d;
            noProgress = 0;
        } else if (++noProgress > 200) {
            noProgress = 0;
            bestDist = Double.MAX_VALUE;
            stopWalker(mc);
            walkerFails++;
            nudge = 20;
        }
    }

    // =====================================================================================
    // Essen, Nachschub, Werkzeug
    // =====================================================================================

    private static boolean needEat(LocalPlayer p) {
        return autoEat && !p.isCreative() && p.getFoodData().getFoodLevel() <= eatHunger && foodSlot(p) >= 0;
    }

    private static void startEat(Phase back) {
        afterPause = back;
        phase = Phase.EAT;
        ticks = 0;
        eatRounds = 0;
        digPos = null;
    }

    private static void eat(LocalPlayer p) {
        action = "Isst";
        ticks++;
        if (ticks == 1) {
            int slot = foodSlot(p);
            if (slot < 0) {
                phase = afterPause;
                return;
            }
            p.getInventory().setSelectedSlot(slot);
        }
        kUse = true;
        if (ticks > 5 && !p.isUsingItem() || ticks > 100) {
            kUse = false;
            if (p.getFoodData().getFoodLevel() <= eatHunger && foodSlot(p) >= 0 && ++eatRounds < 5) {
                ticks = 0; // noch nicht satt genug: noch einmal
                return;
            }
            phase = afterPause;
            ticks = 0;
            wait = 2;
        }
    }

    /**
     * Haelt Spitzhacke und Essen in der Hotbar bereit. BUSY = gerade umgeraeumt (naechster Tick),
     * NO_TOOL = keine brauchbare Spitzhacke mehr da.
     */
    private static int supplies(Minecraft mc, LocalPlayer p) {
        var inv = p.getInventory();
        boolean goodInHotbar = false;
        boolean anyInHotbar = false;
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            if (canMineDebris(st)) anyInHotbar = true;
            if (goodPick(st)) goodInHotbar = true;
        }
        if (!goodInHotbar) {
            // Wechseln aus: mit der fast kaputten weiter - oder anhalten, wenn "Anhalten" an ist
            if (toolGuard && !toolSwitch && anyInHotbar) return toolStop ? NO_TOOL : OK;
            if (refill && refillPickaxe) {
                int from = -1;
                for (int i = 9; i < 36 && from < 0; i++) {
                    if (goodPick(inv.getItem(i))) from = i;
                }
                if (from >= 0) {
                    if (swapCooldown == 0) {
                        PlayerMods.click(p, from, freeHotbarSlot(p, true), "SWAP");
                        swapCooldown = 5;
                        say(mc, "Neue Spitzhacke aus dem Inventar genommen");
                    }
                    return BUSY;
                }
                if (startContainer(p, true, false)) return BUSY;
            }
            // Ohne Ersatz: mit der fast kaputten weiter, wenn "Anhalten" aus ist
            if (!toolStop && anyInHotbar) return OK;
            return NO_TOOL;
        }
        if (refill && refillFood && foodSlot(p) < 0) {
            int from = -1;
            for (int i = 9; i < 36 && from < 0; i++) {
                if (isFood(inv.getItem(i))) from = i;
            }
            if (from >= 0) {
                if (swapCooldown == 0) {
                    PlayerMods.click(p, from, freeHotbarSlot(p, false), "SWAP");
                    swapCooldown = 5;
                }
                return BUSY;
            }
            if (p.getFoodData().getFoodLevel() <= eatHunger && startContainer(p, false, true)) return BUSY;
        }
        return OK;
    }

    private static void noTool(Minecraft mc) {
        if (home && homeNoTool) {
            goHome(mc, "Keine Spitzhacke mehr", homeCmdNoTool);
        } else if (!toolSwitch) {
            stop(mc, "Spitzhacke an der Grenze – Debris Miner angehalten");
        } else {
            stop(mc, "Keine brauchbare Spitzhacke mehr – Debris Miner angehalten");
        }
    }

    /** Hotbar-Platz fuer Nachschub: fast kaputte Spitzhacke, leer, Gestein, sonst der aktuelle. */
    private static int freeHotbarSlot(LocalPlayer p, boolean forPick) {
        var inv = p.getInventory();
        if (forPick) {
            for (int i = 0; i < 9; i++) {
                ItemStack st = inv.getItem(i);
                if (canMineDebris(st) && worn(st)) return i;
            }
        }
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).isEmpty()) return i;
        }
        for (int i = 8; i >= 0; i--) {
            if (isJunk(inv.getItem(i))) return i;
        }
        for (int i = 8; i >= 0; i--) {
            ItemStack st = inv.getItem(i);
            if (!canMineDebris(st) && !isFood(st) && !st.is(Items.TOTEM_OF_UNDYING)) return i;
        }
        return inv.getSelectedSlot();
    }

    /** Einmalige Warnung, wenn eine Spitzhacke in der Hotbar die Grenze erreicht. */
    private static void toolAlerts(Minecraft mc, LocalPlayer p) {
        if (!toolGuard) return;
        var inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            boolean bad = canMineDebris(st) && worn(st);
            if (bad && !alerted[i]) {
                alerted[i] = true;
                int max = st.getMaxDamage();
                int left = max - st.getDamageValue();
                boolean spare = false;
                for (int j = 0; j < 36 && !spare; j++) spare = goodPick(inv.getItem(j));
                String text = "Spitzhacke fast kaputt (" + left + "/" + max + ")"
                        + (!toolSwitch ? "" : spare ? " – wechsle" : " – kein Ersatz");
                if (toolChat) say(mc, text);
                if (toolToast) Keybinds.message(text, "Debris Miner");
                if (toolSound && mc.level != null) {
                    mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND,
                            SoundSource.PLAYERS, 0.5f, 1.8f, false);
                }
            } else if (!bad) {
                alerted[i] = false;
            }
        }
    }

    static boolean canMineDebris(ItemStack st) {
        return !st.isEmpty() && st.isCorrectToolForDrops(Blocks.ANCIENT_DEBRIS.defaultBlockState());
    }

    /** Haltbarkeit an oder unter der Grenze? */
    static boolean worn(ItemStack st) {
        if (!st.isDamageableItem()) return false;
        int max = Math.max(1, st.getMaxDamage());
        int left = max - st.getDamageValue();
        return toolUnit == 0 ? left * 100 <= toolPercent * max : left <= toolPoints;
    }

    private static boolean goodPick(ItemStack st) {
        return canMineDebris(st) && !(toolGuard && worn(st));
    }

    /** Fuer Walker und eigenes Abbauen: fast kaputte Werkzeuge nicht benutzen (solange Ersatz da ist). */
    private static boolean toolAllowed(ItemStack st) {
        if (!toolGuard || !st.isDamageableItem() || !worn(st)) return true;
        if (!toolSwitch) return !toolStop;
        return !toolStop && !hasGoodPick();
    }

    private static boolean hasGoodPick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (goodPick(inv.getItem(i))) return true;
        }
        return false;
    }

    private static boolean isFood(ItemStack st) {
        return !st.isEmpty() && st.get(DataComponents.FOOD) != null && !PlayerMods.badFood(st);
    }

    /** Hotbar-Slot mit dem nahrhaftesten Essen (ohne Goldaepfel) oder -1. */
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

    // =====================================================================================
    // Gestein wegwerfen
    // =====================================================================================

    private static boolean isJunk(ItemStack st) {
        if (st.isEmpty()) return false;
        return st.is(Items.NETHERRACK) || st.is(Items.BASALT) || st.is(Items.SMOOTH_BASALT)
                || st.is(Items.BLACKSTONE) || st.is(Items.GRAVEL) || st.is(Items.SOUL_SAND)
                || st.is(Items.SOUL_SOIL) || st.is(Items.MAGMA_BLOCK);
    }

    /** Slot mit Gestein, das weg kann (Netherrack: nur ueber die behaltenen Stapel hinaus), sonst -1. */
    private static int junkSlot(LocalPlayer p) {
        var inv = p.getInventory();
        int netherrack = 0;
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).is(Items.NETHERRACK)) netherrack++;
        }
        int sel = inv.getSelectedSlot();
        // wie ein Spieler: nur volle Stapel wegwerfen (nicht alle paar Bloecke ein paar Stueck), ausser das
        // Inventar wird knapp
        boolean tight = freeSlots(p) < 3;
        for (int i = 35; i >= 0; i--) {
            ItemStack st = inv.getItem(i);
            if (!isJunk(st) || i == sel) continue;
            if (st.is(Items.NETHERRACK)) {
                if (netherrack <= keepNetherrack) continue;
            }
            if (!tight && st.getCount() < st.getMaxStackSize()) continue;
            return i;
        }
        return -1;
    }

    private static boolean throwJunk(LocalPlayer p) {
        if (!dropJunk) return false;
        if (dropSlot < 0) {
            if (dropCooldown > 0) return false;
            dropSlot = junkSlot(p);
            if (dropSlot < 0) return false;
            aimedTicks = 0;
        }
        action = "Wirft Gestein weg";
        if (Look.turn(p, Look.yawOf(-curX, -curZ), -15.0f, 40.0f, 25.0f, 3.0f)) {
            // einen Tick warten, damit der Server die neue Blickrichtung schon kennt
            if (++aimedTicks >= 2) {
                int slot = dropSlot;
                dropSlot = -1;
                aimedTicks = 0;
                dropCooldown = 8;
                if (isJunk(p.getInventory().getItem(slot))) {
                    PlayerMods.click(p, slot < 9 ? 36 + slot : slot, 1, "THROW");
                }
                // noch mehr Gestein (z.B. nach dem Durchsprinten)? Gleich hinterher werfen, statt sich fuer
                // jeden Stapel neu umzudrehen
                int more = junkSlot(p);
                if (more >= 0 && more != slot) {
                    dropSlot = more;
                    dropCooldown = 0;
                    aimedTicks = -(2 + Look.rng().nextInt(4));
                }
            }
        } else {
            aimedTicks = 0;
        }
        return true;
    }

    // =====================================================================================
    // Nachschub aus Shulker-Kiste / Endertruhe
    // =====================================================================================

    private static boolean isShulker(ItemStack st) {
        return st.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
    }

    private static boolean isBox(BlockState bs) {
        return bs.getBlock() instanceof ShulkerBoxBlock || bs.is(Blocks.ENDER_CHEST);
    }

    /** Shulker-Kiste im Inventar, die das Gesuchte enthaelt (Inhalt steht im Item), sonst -1. */
    private static int shulkerWith(LocalPlayer p, boolean pick, boolean food) {
        var inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (!isShulker(st)) continue;
            ItemContainerContents contents = st.get(DataComponents.CONTAINER);
            if (contents == null) continue;
            for (var template : contents.nonEmptyItems()) {
                ItemStack c = template.create();
                if (pick && goodPick(c) || food && isFood(c)) return i;
            }
        }
        return -1;
    }

    private static boolean startContainer(LocalPlayer p, boolean pick, boolean food) {
        if (!refill || boxRetry > 0) return false;
        int slot = refillShulker ? shulkerWith(p, pick, food) : -1;
        boolean ender = false;
        if (slot < 0 && refillEnderChest && !enderTried) {
            var inv = p.getInventory();
            for (int i = 0; i < 36 && slot < 0; i++) {
                if (inv.getItem(i).is(Items.ENDER_CHEST)) slot = i;
            }
            ender = slot >= 0;
        }
        if (slot < 0) return false;
        boxItem = p.getInventory().getItem(slot).getItem();
        boxEnder = ender;
        boxCount = countItem(p, boxItem);
        needPick = pick;
        needFood = food;
        picksTaken = 0;
        foodTaken = 0;
        openTries = 0;
        box = Box.PREP;
        afterPause = phase == Phase.VEIN ? Phase.VEIN : Phase.TUNNEL;
        phase = Phase.CONTAINER;
        ticks = 0;
        aimedTicks = 0;
        digPos = null;
        return true;
    }

    private static int countItem(LocalPlayer p, Item item) {
        var inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).is(item)) n += inv.getItem(i).getCount();
        }
        return n;
    }

    private static void container(Minecraft mc, Level level, LocalPlayer p) {
        action = "Holt Nachschub";
        if (box == null) {
            phase = afterPause;
            return;
        }
        var inv = p.getInventory();
        switch (box) {
            case PREP -> {
                int s = -1;
                for (int i = 0; i < 36 && s < 0; i++) {
                    if (inv.getItem(i).is(boxItem)) s = i;
                }
                if (s < 0) {
                    endBox(mc, false);
                    return;
                }
                if (s >= 9) {
                    if (swapCooldown == 0) {
                        PlayerMods.click(p, s, freeHotbarSlot(p, false), "SWAP");
                        swapCooldown = 5;
                    }
                    return;
                }
                BlockPos spot = boxSpot(level, p);
                if (spot == null) {
                    // Platz schaffen: das naechste Tunnelfeld freiraeumen (wird sowieso gegraben)
                    BlockPos ahead = feet(p).offset(curX, 0, curZ);
                    for (BlockPos c : new BlockPos[] {ahead.above(), ahead}) {
                        if (!solid(level, c)) continue;
                        if (isLava(level, c) || !safeToBreak(level, c) || ++ticks > 600
                                || level.getBlockState(c).getDestroySpeed(level, c) < 0.0f
                                || !mine(mc, level, p, c, -1)) {
                            break;
                        }
                        action = "Macht Platz für die Kiste";
                        return;
                    }
                    if (solid(level, ahead) || solid(level, ahead.above()) || !solid(level, ahead.below())) {
                        say(mc, "Kein Platz für die Kiste");
                        endBox(mc, false);
                        return;
                    }
                    return; // naechster Tick: boxSpot findet das freie Feld
                }
                boxPos = spot;
                inv.setSelectedSlot(s);
                box = Box.PLACE;
                aimedTicks = 0;
            }
            case PLACE -> {
                Vec3 hit = new Vec3(boxPos.getX() + 0.5, boxPos.getY(), boxPos.getZ() + 0.5);
                if (!Look.aim(p, hit.x, hit.y, hit.z, 5.0f)) {
                    aimedTicks = 0;
                    return;
                }
                if (++aimedTicks < 2) return;
                if (!inv.getItem(inv.getSelectedSlot()).is(boxItem)) {
                    box = Box.PREP;
                    return;
                }
                mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND,
                        new BlockHitResult(hit, Direction.UP, boxPos.below(), false));
                p.swing(InteractionHand.MAIN_HAND);
                box = Box.WAIT_PLACED;
                ticks = 0;
            }
            case WAIT_PLACED -> {
                if (isBox(level.getBlockState(boxPos))) {
                    box = Box.OPEN;
                    aimedTicks = 0;
                    ticks = 0;
                } else if (++ticks > 30) {
                    say(mc, "Kiste ließ sich nicht aufstellen");
                    endBox(mc, false);
                }
            }
            case OPEN -> {
                Vec3 c = new Vec3(boxPos.getX() + 0.5, boxPos.getY() + 0.9, boxPos.getZ() + 0.5);
                if (!Look.aim(p, c.x, c.y, c.z, 5.0f)) {
                    aimedTicks = 0;
                    return;
                }
                if (++aimedTicks < 2) return;
                mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(c, Direction.UP, boxPos, false));
                p.swing(InteractionHand.MAIN_HAND);
                box = Box.WAIT_OPEN;
                ticks = 0;
            }
            case WAIT_OPEN -> {
                if (p.containerMenu != p.inventoryMenu) {
                    box = Box.TAKE;
                    ticks = 0;
                } else if (++ticks > 40) {
                    if (++openTries < 3) {
                        box = Box.OPEN;
                        aimedTicks = 0;
                    } else {
                        box = Box.BREAK;
                        ticks = 0;
                    }
                }
            }
            case TAKE -> {
                var menu = p.containerMenu;
                if (menu == p.inventoryMenu) {
                    box = Box.BREAK;
                    ticks = 0;
                    return;
                }
                if (++ticks % 3 != 0) return; // ruhig, ein Klick nach dem anderen
                int n = Math.min(27, Math.max(0, menu.slots.size() - 36));
                for (int i = 0; i < n; i++) {
                    ItemStack st = menu.getSlot(i).getItem();
                    if (st.isEmpty()) continue;
                    boolean pick = needPick && picksTaken < 2 && goodPick(st);
                    boolean food = !pick && needFood && foodTaken < 2 && isFood(st);
                    if (!pick && !food) continue;
                    PlayerMods.clickIn(p, menu.containerId, i, 0, "QUICK_MOVE");
                    if (pick) {
                        picksTaken++;
                    } else {
                        foodTaken++;
                    }
                    return;
                }
                box = Box.CLOSE;
            }
            case CLOSE -> {
                if (p.containerMenu != p.inventoryMenu) p.closeContainer();
                if (picksTaken + foodTaken > 0) {
                    say(mc, "Nachschub geholt: " + picksTaken + " Spitzhacken, " + foodTaken + " Essen");
                } else if (boxEnder) {
                    enderTried = true; // nichts Passendes drin: nicht noch einmal versuchen
                }
                box = Box.BREAK;
                ticks = 0;
                digPos = null;
                if (!boxPickup) endBox(mc, true); // so eingestellt: Kiste bleibt stehen
            }
            case BREAK -> {
                if (!isBox(level.getBlockState(boxPos))) {
                    box = Box.PICKUP;
                    ticks = 0;
                    return;
                }
                int slot = -1;
                if (boxEnder) {
                    slot = silkSlot(p);
                    if (slot < 0) {
                        say(mc, "Endertruhe bleibt stehen (keine Spitzhacke mit Behutsamkeit)");
                        endBox(mc, true);
                        return;
                    }
                }
                if (!mine(mc, level, p, boxPos, slot) || ++ticks > 600) endBox(mc, true);
            }
            case PICKUP -> {
                if (countItem(p, boxItem) >= boxCount || ++ticks > 60) endBox(mc, true);
            }
        }
    }

    /** Platz fuer die Kiste: Tunnelfeld hinter oder vor dem Spieler (frei, Boden fest, Luft darueber). */
    private static BlockPos boxSpot(Level level, LocalPlayer p) {
        BlockPos feet = feet(p);
        // bleibt die Kiste stehen, dann nur hinter ihm (nicht im Weg des Tunnels)
        BlockPos[] spots = boxPickup ? new BlockPos[] {feet.offset(-curX, 0, -curZ), feet.offset(curX, 0, curZ)}
                : new BlockPos[] {feet.offset(-curX, 0, -curZ)};
        for (BlockPos c : spots) {
            if (!passable(level, c) || isLava(level, c) || !passable(level, c.above())) continue;
            if (!solid(level, c.below())) continue;
            AABB cell = new AABB(c.getX(), c.getY(), c.getZ(), c.getX() + 1, c.getY() + 1, c.getZ() + 1);
            if (p.getBoundingBox().intersects(cell)) continue;
            if (Look.eyeDist2(p, c) > REACH * REACH) continue;
            return c;
        }
        return null;
    }

    private static void endBox(Minecraft mc, boolean ok) {
        if (!ok && boxEnder) enderTried = true;
        if (!ok) boxRetry = 600; // nicht sofort wieder versuchen (30 s)
        LocalPlayer p = mc.player;
        if (p != null && p.containerMenu != p.inventoryMenu) p.closeContainer();
        box = null;
        digPos = null;
        phase = afterPause;
        ticks = 0;
        wait = 2;
    }

    /** Hotbar-Slot mit einem Werkzeug mit Behutsamkeit oder -1. */
    private static int silkSlot(LocalPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || !toolAllowed(st)) continue;
            for (var holder : st.getEnchantments().keySet()) {
                if (holder.is(Enchantments.SILK_TOUCH)) return i;
            }
        }
        return -1;
    }

    // =====================================================================================
    // Abbauen, Setzen, Laufen
    // =====================================================================================

    /**
     * Baut pos ab: bestes erlaubtes Werkzeug (oder forcedSlot), auf eine sichtbare Stelle zielen, Angriff
     * halten. false = laesst sich nicht abbauen (zu lange, nicht zu sehen, unzerstoerbar).
     */
    private static boolean mine(Minecraft mc, Level level, LocalPlayer p, BlockPos pos, int forcedSlot) {
        BlockState bs = level.getBlockState(pos);
        float hardness = bs.getDestroySpeed(level, pos);
        if (hardness < 0.0f) return false;
        int slot = forcedSlot >= 0 ? forcedSlot : bestTool(p, bs, hardness);
        if (!pos.equals(digPos)) {
            digPos = pos;
            digTicks = 0;
            double t = slot >= 0 ? ticksWith(p.getInventory().getItem(slot), bs, hardness) : hardness * 100.0;
            digLimit = (int) Math.min(1500.0, t * 2.5 + 60.0);
        }
        if (++digTicks > digLimit) {
            digPos = null;
            return false;
        }
        if (slot >= 0) p.getInventory().setSelectedSlot(slot);
        double[] pt = TestClient.visiblePoint(level, p, pos);
        if (pt == null) {
            if (digTicks > 40) {
                digPos = null;
                return false;
            }
            Look.aim(p, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4.0f);
            return true;
        }
        if (digTicks > 1) Look.holdSteady(); // waehrend des Abbaus die Maus ruhig halten
        boolean aimed = Look.aim(p, pt[0], pt[1], pt[2], 4.0f);
        boolean onTarget = false;
        HitResult hr = mc.hitResult;
        if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK) {
            BlockPos hp = bh.getBlockPos();
            onTarget = hp.equals(pos) || passable(level, hp);
        }
        kAttack = aimed && onTarget;
        digging = kAttack;
        action = bs.is(Blocks.ANCIENT_DEBRIS) ? "Baut Debris ab" : action;
        return true;
    }

    /** Schnellstes erlaubtes Werkzeug der Hotbar; fuer Debris nur eins, das ihn auch droppt. */
    private static int bestTool(LocalPlayer p, BlockState bs, float hardness) {
        var inv = p.getInventory();
        boolean needCorrect = bs.is(Blocks.ANCIENT_DEBRIS);
        int best = -1;
        double bestT = Double.MAX_VALUE;
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            if (!toolAllowed(st)) continue;
            if (needCorrect && !st.isCorrectToolForDrops(bs)) continue;
            double t = ticksWith(st, bs, hardness);
            if (t < bestT - 0.5) {
                bestT = t;
                best = i;
            }
        }
        return best;
    }

    private static double ticksWith(ItemStack st, BlockState bs, float hardness) {
        if (hardness <= 0.0f) return 1.0;
        float speed = st.getDestroySpeed(bs);
        boolean right = !bs.requiresCorrectToolForDrops() || st.isCorrectToolForDrops(bs);
        return Math.ceil(hardness * (right ? 30.0 : 100.0) / Math.max(0.1f, speed));
    }

    /**
     * Setzt einen Baublock (Netherrack o.ae. aus der Hotbar) nach t - auch in Lava hinein. Geklickt wird auf
     * eine sichtbare Seite eines festen Nachbarn. true = gesetzt.
     */
    private static boolean placeInto(Minecraft mc, Level level, LocalPlayer p, BlockPos t) {
        AABB cell = new AABB(t.getX(), t.getY(), t.getZ(), t.getX() + 1, t.getY() + 1, t.getZ() + 1);
        if (p.getBoundingBox().intersects(cell)) return false;
        if (TestClient.findBuildSlot(mc) < 0) {
            buildBlocksToHotbar(p);
            return false;
        }
        Object[] f = findFace(level, p, t);
        if (f == null) return false;
        Vec3 hit = (Vec3) f[2];
        if (!Look.aim(p, hit.x, hit.y, hit.z, 5.0f)) {
            aimedTicks = 0;
            return false;
        }
        if (++aimedTicks < 2 || placeCooldown > 0) return false;
        aimedTicks = 0;
        placeCooldown = 4;
        return TestClient.placeBlock(mc, (BlockPos) f[0], (Direction) f[1], hit, t);
    }

    /** Baubloecke aus dem Hauptinventar in die Hotbar holen. */
    private static void buildBlocksToHotbar(LocalPlayer p) {
        if (!refillBlocks || swapCooldown > 0) return;
        var inv = p.getInventory();
        for (int i = 9; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (st.getItem() instanceof BlockItem bi && TestClient.isBuildBlock(bi.getBlock())) {
                PlayerMods.click(p, i, freeHotbarSlot(p, false), "SWAP");
                swapCooldown = 5;
                return;
            }
        }
    }

    /**
     * Sichtbare Seite eines festen Nachbarn von t, gegen die man klicken kann: {Nachbar, Seite, Trefferpunkt}
     * oder null. Durch Lava hindurch wird gezielt (Fluessigkeiten halten den Blick nicht auf).
     */
    private static Object[] findFace(Level level, LocalPlayer p, BlockPos t) {
        Vec3 eye = p.getEyePosition();
        for (Direction d : Direction.values()) {
            BlockPos s = t.relative(d);
            if (!solid(level, s) || isLava(level, s)) continue;
            Direction face = d.getOpposite();
            Vec3 hit = new Vec3(t.getX() + 0.5 + d.getStepX() * 0.5, t.getY() + 0.5 + d.getStepY() * 0.5,
                    t.getZ() + 0.5 + d.getStepZ() * 0.5);
            if (eye.distanceToSqr(hit) > REACH * REACH) continue;
            Vec3 into = hit.add(d.getStepX() * 0.05, d.getStepY() * 0.05, d.getStepZ() * 0.05);
            BlockHitResult r = level.clip(new ClipContext(eye, into, ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE, p));
            if (r.getType() == HitResult.Type.BLOCK && r.getBlockPos().equals(s) && r.getDirection() == face) {
                return new Object[] {s, face, hit};
            }
        }
        return null;
    }

    /** In die Mitte von t gehen (Blick nach yaw). true = angekommen. */
    private static boolean moveTo(LocalPlayer p, BlockPos t, float yaw) {
        double dx = t.getX() + 0.5 - p.getX();
        double dz = t.getZ() + 0.5 - p.getZ();
        double tolMove = humanize ? 0.3 : 0.2; // ohne Ducken nicht ganz so genau in der Mitte
        if (feet(p).equals(t) && Math.abs(dx) < tolMove && Math.abs(dz) < tolMove) {
            stuckTicks = 0;
            lastMoveX = Double.NaN;
            return true;
        }
        float[] w = Look.wander(); // menschlich: der Blick wandert ein paar Grad hin und her
        Look.turn(p, yaw + w[0], 25.0f + w[1], 30.0f, 10.0f, 0.0f);
        double dist = Math.sqrt(dx * dx + dz * dz);
        double speed = Double.isNaN(lastMoveX) ? 0 : Math.hypot(p.getX() - lastMoveX, p.getZ() - lastMoveZ);
        lastMoveX = p.getX();
        lastMoveZ = p.getZ();
        if (humanize) {
            // wie ein Spieler: kurz vor der Mitte loslassen und ausrollen lassen (statt sich zu ducken).
            // Steht er noch nicht auf dem Feld (z.B. einen Block hoeher auf der Kante), weiter schieben,
            // bis er hinunterfaellt - sonst bliebe er mit der Ferse auf der Kante stehen.
            BlockPos f = feet(p);
            boolean above = f.getX() == t.getX() && f.getZ() == t.getZ() && f.getY() > t.getY();
            if (!above && dist < 0.3 + speed * 1.3) {
                kFwd = false;
                kBack = false;
                kLeft = false;
                kRight = false;
            } else {
                // kleine seitliche Abweichungen laesst ein Spieler einfach stehen, statt staendig A/D zu tippen
                push(p, dx, dz, above ? 0.02 : 0.04, above ? 0.02 : 0.17);
            }
            kSneak = false;
            if (above) Look.releaseSneak();
        } else {
            push(p, dx, dz, 0.04);
            kSneak = dist < 0.6; // geduckt genau auf die Mitte
        }
        stuckTicks++;
        return false;
    }

    private static void startWalk(BlockPos goal) {
        Walker.startDig(goal.getX(), goal.getY(), goal.getZ());
        walkGoal = goal;
    }

    /** Wie startWalk, aber genau auf das Feld (Rueckweg in den Tunnel, Ziel-Hoehe). */
    private static void startWalkExact(BlockPos goal) {
        Walker.startDigExact(goal.getX(), goal.getY(), goal.getZ());
        walkGoal = goal;
    }

    private static String tickWalker(Minecraft mc, Level level, LocalPlayer p) {
        walkerTicked = true;
        try {
            return Walker.tick(mc, level, p);
        } catch (RuntimeException | LinkageError e) {
            Safe.report("Laufen", e);
            return "Fehler";
        }
    }

    private static void stopWalker(Minecraft mc) {
        if (Walker.isActive()) Walker.stop(mc);
        walkGoal = null;
    }

    // =====================================================================================
    // Hilfen
    // =====================================================================================

    private static BlockPos feet(LocalPlayer p) {
        return BlockPos.containing(p.getX(), p.getY() + 0.2, p.getZ());
    }

    private static boolean isLava(Level level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.LAVA);
    }

    private static boolean passable(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static boolean solid(Level level, BlockPos pos) {
        return !passable(level, pos);
    }

    private static boolean open(Level level, BlockPos pos) {
        return passable(level, pos) && passable(level, pos.above()) && !isLava(level, pos)
                && !isLava(level, pos.above());
    }

    /** Darf der Block weg? Nicht, wenn darueber oder seitlich Fluessigkeit ist (die liefe sonst in den Tunnel). */
    private static boolean safeToBreak(Level level, BlockPos pos) {
        for (BlockPos n : new BlockPos[] {pos.above(), pos.north(), pos.south(), pos.east(), pos.west()}) {
            if (!level.getFluidState(n).isEmpty()) return false;
        }
        return level.getFluidState(pos).isEmpty();
    }

    /** Grenzt der Block an Luft (dann zeigt ihn der Server echt, nicht als Tarnung)? */
    private static boolean exposed(Level level, BlockPos b) {
        for (Direction d : Direction.values()) {
            BlockPos n = b.relative(d);
            if (passable(level, n) && level.getFluidState(n).isEmpty()) return true;
        }
        return false;
    }
    private static String compass(int dx, int dz) {
        if (dx > 0) return "Osten";
        if (dx < 0) return "Westen";
        return dz > 0 ? "Süden" : "Norden";
    }
    /** Blick weich drehen. true = innerhalb der Toleranz. */    /** In Weltrichtung (vx, vz) gehen, egal wohin er schaut: W/A/S/D passend zum Blick. */
    private static void push(LocalPlayer p, double vx, double vz, double dead) {
        push(p, vx, vz, dead, dead);
    }

    /** Wie push, aber mit eigener Toleranz fuer seitlich (A/D). */
    private static void push(LocalPlayer p, double vx, double vz, double dead, double sideDead) {
        double yaw = Math.toRadians(p.getYRot());
        double fwd = -vx * Math.sin(yaw) + vz * Math.cos(yaw);
        double left = vx * Math.cos(yaw) + vz * Math.sin(yaw);
        kFwd = fwd > dead;
        kBack = fwd < -dead;
        kLeft = left > sideDead;
        kRight = left < -sideDead;
    }

    /** Chat-Meldung mit "1912 Utilities"-Absender (text auf Deutsch, wird uebersetzt). */
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
        kSprint = false;
    }

    private static void applyKeys(Minecraft mc) {
        var o = mc.options;
        Look.setKey(o.keyUp, kFwd);
        Look.setKey(o.keyDown, kBack);
        Look.setKey(o.keyLeft, kLeft);
        Look.setKey(o.keyRight, kRight);
        Look.setKey(o.keyJump, false);
        Look.setKey(o.keySprint, kSprint);
        Look.setSneak(o.keyShift, kSneak);
        Look.setKey(o.keyAttack, kAttack);
        Look.setKey(o.keyUse, kUse);
    }
    // =====================================================================================
    // Anzeige
    // =====================================================================================

    /** Kleine Anzeige ueber der Hotbar: was er gerade macht, Tunnel-Laenge, Debris, Laufzeit. */
    static void renderHud(GuiGraphicsExtractor g, Minecraft mc) {
        if (!running || !showStatus || mc.player == null || mc.gui.screen() != null) return;
        var font = mc.font;
        long sec = Math.max(0L, (System.currentTimeMillis() - startedAt) / 1000L);
        String time = sec >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60)
                : String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
        String title = "Debris Miner";
        String act = Lang.t(action);
        String stats = Lang.t("Tunnel-Länge: ") + tunnelLength + "   " + Lang.t("Debris: ") + debrisMined + "   " + time;
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

    /** Gefundenes Debris (orange) und den naechsten Tunnelabschnitt zeigen. */
    static void render(GuiGraphicsExtractor g) {
        if (!running) return;
        if (highlight) {
            for (BlockPos b : targets) {
                Render3D.outline(g, new AABB(b.getX(), b.getY(), b.getZ(), b.getX() + 1, b.getY() + 1, b.getZ() + 1),
                        ORANGE, false);
            }
            Minecraft mc = Minecraft.getInstance();
            if (phase == Phase.TUNNEL && mc.player != null && mc.level != null) {
                BlockPos next = feet(mc.player).offset(curX, 0, curZ);
                for (BlockPos c : section(next, curX, curZ)) {
                    if (!solid(mc.level, c)) continue;
                    int col = safeToBreak(mc.level, c) ? (Theme.accent() & 0xFFFFFF) | 0xA0000000 : RED;
                    Render3D.outline(g, new AABB(c.getX(), c.getY(), c.getZ(), c.getX() + 1, c.getY() + 1,
                            c.getZ() + 1), col, false);
                }
            }
        }
        if (showPath && Walker.isActive()) Walker.render(g);
    }
}
