package de.test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.test.Pathfinder.Move;
import de.test.Pathfinder.Step;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Laeuft zu Koordinaten wie Baritone. {@link Pathfinder} plant den Weg (A*), der Walker faehrt ihn ab:
 * springt auf Stufen und ueber Luecken, laeuft freie Strecken in gerader Linie, schwimmt, oeffnet
 * Tueren und baut nur dort ab oder setzt Bloecke, wo der Weg es wirklich verlangt. Klemmt etwas, wird
 * sofort neu geplant statt zu warten. Weite Ziele werden Stueck fuer Stueck geplant, waehrend er laeuft.
 */
public final class Walker {
    private Walker() {
    }

    // ---------- Einstellungen (Abbauen: TestClient.autoMine) ----------

    public static boolean allowPlace = true;
    public static boolean allowParkour = true;
    public static boolean sprint = true;
    public static boolean showPath = true;
    public static int maxFall = 3;

    /** Meldung, wenn das Ziel erreicht ist. */
    static final String ARRIVED = "Ziel erreicht";

    /**
     * Welche Werkzeuge beim Abbauen benutzt werden duerfen (Debris Miner: fast kaputte Spitzhacken nicht).
     * Standard: alle.
     */
    static java.util.function.Predicate<ItemStack> toolOk = st -> true;

    // ---------- Grenzen ----------

    /** Rechenzeit je Tick fuer die Suche (das Spiel soll nie haengen). */
    private static final long SEARCH_NS_PER_TICK = 6_000_000L;
    /** Laenger gesucht: den besten Teilweg nehmen und unterwegs weiterplanen. */
    private static final int MAX_SEARCH_TICKS = 50;
    /** So viele Schritte vor dem Ende eines Teilwegs wird der Anschluss geplant. */
    private static final int PLAN_AHEAD_STEPS = 16;
    /** Hinter ungeladenen Chunks: so viele Knoten ohne Annaeherung, dann den Teilweg nehmen. */
    private static final int FRONTIER_GIVE_UP = 6000;
    private static final int OFF_PATH_TICKS = 12;
    private static final int STUCK_TICKS = 40;
    /** So lange ohne dem Ziel naeher zu kommen: aufgeben. */
    private static final int MAX_IDLE_TICKS = 1200;
    private static final int MAX_FRUITLESS_REPLANS = 15;
    /** So weit wird nach einer geraden Abkuerzung geschaut (Schritte). */
    private static final int LOOKAHEAD = 16;
    private static final float WALK_PITCH = 12.0f;

    // ---------- Zustand ----------

    private static boolean active;
    /** Bereich abbauen / Bloecke suchen: Abbauen auf dem Weg ist immer erlaubt. */
    private static boolean miningMode;
    /** Debris Miner: normales Ziel, aber Abbauen auf dem Weg ist immer erlaubt. */
    private static boolean digMode;
    /** Genau auf dem Zielfeld ankommen (Debris Miner: zurueck in den Tunnel). */
    private static boolean exactGoal;
    private static int goalX;
    private static int goalY;
    private static int goalZ;

    private static List<Step> path;
    private static boolean pathComplete;
    private static int index;
    private static List<Step> next;
    private static boolean nextComplete;
    private static int lookahead = -1;
    /** Laeuft gerade eine geprueft freie Gerade zum Wegpunkt lookahead entlang (Abkuerzung). */
    private static boolean onLine;

    private static Pathfinder.Search search;
    private static PathWorld searchWorld;
    private static boolean searchAhead;
    private static int searchTicks;

    private static final Set<Long> blocked = new HashSet<>();
    private static final PathWorld tickWorld = new PathWorld(null, null);

    private static int stepTicks;
    private static int stillTicks;
    private static int offTicks;
    private static int airTicks;
    private static int waitTicks;
    private static int idleTicks;
    private static int replans;
    private static int chunkWaits;
    private static int placeTries;
    private static int placeCooldown;
    private static int doorTries;
    private static int doorCooldown;
    private static int sneakTicks;
    private static double bestDist = Double.MAX_VALUE;
    private static double lastX;
    private static double lastY;
    private static double lastZ;
    private static BlockPos digPos;
    private static int digTicks;
    private static int digLimit;
    /** Gerade am Abbauen (fuer den Mixin: klappt dann auch, wenn das Fenster nicht im Vordergrund ist). */
    private static boolean digging;
    private static String action = "";

    // Tasten fuer diesen Tick (werden am Ende des Ticks alle auf einmal gesetzt)
    private static boolean kFwd;
    private static boolean kBack;
    private static boolean kLeft;
    private static boolean kRight;
    private static boolean kJump;
    private static boolean kSprint;
    private static boolean kSneak;
    private static boolean kAttack;

    // ---------- Start / Stopp ----------

    static void start(int x, int y, int z) {
        start(x, y, z, false);
    }

    /** mining: fuer "Bereich abbauen" und "Bloecke suchen" - dort darf auf dem Weg immer abgebaut werden. */
    static void start(int x, int y, int z, boolean mining) {
        reset();
        active = true;
        miningMode = mining;
        digMode = false;
        exactGoal = false;
        goalX = x;
        goalY = y;
        goalZ = z;
        action = "Plant den Weg";
    }

    /** Zu einer Stelle gehen und unterwegs abbauen duerfen, egal wie "Bloecke abbauen" eingestellt ist. */
    static void startDig(int x, int y, int z) {
        start(x, y, z, false);
        digMode = true;
    }

    /** Wie startDig, aber er muss genau auf dem Zielfeld stehen. */
    static void startDigExact(int x, int y, int z) {
        startDig(x, y, z);
        exactGoal = true;
    }

    /** Beendet das Gehen und laesst alle Tasten los. */
    static void stop(Minecraft mc) {
        boolean was = active;
        reset();
        if (was) release(mc);
    }

    /** Zustand verwerfen, ohne Tasten anzufassen (z.B. beim Verlassen der Welt). */
    static void reset() {
        active = false;
        path = null;
        next = null;
        search = null;
        searchWorld = null;
        blocked.clear();
        index = 0;
        lookahead = -1;
        onLine = false;
        stepTicks = 0;
        stillTicks = 0;
        offTicks = 0;
        airTicks = 0;
        waitTicks = 0;
        idleTicks = 0;
        replans = 0;
        chunkWaits = 0;
        placeTries = 0;
        placeCooldown = 0;
        doorTries = 0;
        doorCooldown = 0;
        sneakTicks = 0;
        bestDist = Double.MAX_VALUE;
        digPos = null;
        digging = false;
        action = "";
    }

    /** Alle Tasten loslassen, die der Walker benutzt. */
    static void release(Minecraft mc) {
        clearKeys();
        Look.releaseSneak(); // sofort aufstehen, nicht erst nach der Ducken-Pause
        applyKeys(mc);
        digging = false;
    }

    /** Fuer den Mixin: Angriffstaste gilt als gedrueckt, solange der Walker einen Block abbaut. */
    public static boolean forceAttack() {
        return active && digging || DebrisMiner.forceAttack();
    }

    static boolean isActive() {
        return active;
    }

    /** Gerade mitten in einem Sprung, beim Hochbauen, Brueckenbauen oder Runtergraben (nicht unterbrechen). */
    static boolean inCriticalMove(LocalPlayer p) {
        if (!active || path == null || index >= path.size()) return false;
        if (!p.onGround() && !p.isInWater()) return true;
        Step s = path.get(index);
        return s.move == Move.PILLAR || s.move == Move.DOWN || s.move == Move.PARKOUR || s.place != null;
    }

    /** Kurzer Text fuer das Menue. */
    static String status(Minecraft mc) {
        if (!active) return "Bereit";
        String dist = "";
        if (mc.player != null) {
            dist = Lang.t(" – noch ") + Math.round(goalDistance(mc.player)) + Lang.t(" Blöcke");
        }
        if (path == null) return Lang.t(action.isEmpty() ? "Plant den Weg" : action) + dist;
        return Lang.t(action) + dist;
    }

    // ---------- Tick ----------

    /** Ein Tick. Gibt null zurueck, solange es laeuft, sonst die Abschlussmeldung. */
    static String tick(Minecraft mc, Level level, LocalPlayer p) {
        if (!active) return "Gehen beendet";
        clearKeys();
        digging = false;
        String result = null;
        try {
            result = run(mc, level, p);
        } finally {
            if (result != null) {
                clearKeys();
                Look.releaseSneak();
                digging = false;
            }
            applyKeys(mc);
        }
        return result;
    }

    private static String run(Minecraft mc, Level level, LocalPlayer p) {
        tickWorld.reset(level, p);
        Pathfinder.Options opt = options(mc);
        Pathfinder.Mover mover = new Pathfinder.Mover(tickWorld, opt);
        int[] node = playerNode(mover, p);

        if (node != null && atGoal(node[0], node[1], node[2])) return ARRIVED;

        // Insgesamt naeher gekommen? Sonst irgendwann aufgeben.
        double d = goalDistance(p);
        if (d < bestDist - 1.0) {
            bestDist = d;
            idleTicks = 0;
            replans = 0;
        } else if (++idleTicks > MAX_IDLE_TICKS) {
            return "Ziel nicht erreichbar (kein Fortschritt)";
        }
        if (replans > MAX_FRUITLESS_REPLANS) return "Ziel nicht erreichbar (Weg immer wieder blockiert)";

        double moved = Math.abs(p.getX() - lastX) + Math.abs(p.getY() - lastY) + Math.abs(p.getZ() - lastZ);
        lastX = p.getX();
        lastY = p.getY();
        lastZ = p.getZ();
        stillTicks = moved < 0.01 ? stillTicks + 1 : 0;
        airTicks = node == null ? airTicks + 1 : 0;
        if (placeCooldown > 0) placeCooldown--;
        if (doorCooldown > 0) doorCooldown--;

        // 1. Kein Weg: neue Suche ab der eigenen Position
        if (path == null && search == null) {
            if (waitTicks > 0) {
                waitTicks--;
                idle(p);
                return null;
            }
            int[] from = node;
            if (from == null) {
                if (airTicks < 60) { // erst landen
                    idle(p);
                    return null;
                }
                from = new int[] {Mth.floor(p.getX()), Mth.floor(p.getY() + 0.51), Mth.floor(p.getZ())};
            }
            startSearch(level, p, opt, from[0], from[1], from[2], false, false);
        }

        // 2. Suche weiterrechnen (auch die Vorausplanung, waehrend er laeuft)
        if (search != null) {
            String err = continueSearch(mc, p);
            if (err != null) return err;
        }
        if (path == null) {
            idle(p);
            return null;
        }

        // 3. Wo auf dem Weg sind wir?
        if (node != null) {
            sync(node, p);
        } else if (airTicks > 100) {
            replan(false);
            return null;
        }
        if (offTicks > OFF_PATH_TICKS) {
            replan(false);
            return null;
        }

        // 4. Ende des (Teil-)Wegs
        if (index >= path.size()) {
            if (pathComplete) return ARRIVED;
            if (next != null) {
                List<Step> n = next;
                next = null;
                if (n.isEmpty()) {
                    path = null;
                } else {
                    setPath(n, nextComplete);
                }
            } else if (search == null) {
                path = null;
            }
            idle(p);
            return null;
        }

        // 5. Anschluss schon jetzt planen, damit er ohne Pause weiterlaeuft
        if (!pathComplete && next == null && search == null && path.size() - index <= PLAN_AHEAD_STEPS) {
            Step last = path.get(path.size() - 1);
            startSearch(level, p, opt, last.tx, last.ty, last.tz,
                    last.place != null || last.move == Move.PILLAR, true);
        }

        // 6. Schritt ausfuehren; zu Beginn pruefen, ob er (noch) geht
        Step s = path.get(index);
        if (stepTicks == 0) {
            double c = mover.evaluate(s, mover.solidFloor(s.fx, s.fy, s.fz));
            if (c >= Pathfinder.INF) {
                replan(false);
                return null;
            }
        }
        stepTicks++;
        execute(mc, level, p, mover, s);
        return null;
    }

    // ---------- Suche ----------

    private static Pathfinder.Options options(Minecraft mc) {
        Pathfinder.Options o = new Pathfinder.Options();
        o.allowBreak = TestClient.autoMine || miningMode || digMode;
        o.mineGoal = miningMode;
        o.exactGoal = exactGoal;
        o.allowPlace = allowPlace;
        o.placeAvailable = TestClient.findBuildSlot(mc) >= 0;
        o.allowParkour = allowParkour;
        o.maxFall = Math.max(1, Math.min(10, maxFall));
        o.maxNodes = 40_000;
        o.blockedEdges = blocked;
        return o;
    }

    private static void startSearch(Level level, LocalPlayer p, Pathfinder.Options opt, int x, int y, int z,
                                    boolean floorPlaced, boolean ahead) {
        searchWorld = new PathWorld(level, p);
        searchWorld.setRefY(y);
        search = new Pathfinder.Search(searchWorld, opt, x, y, z, goalX, goalY, goalZ, floorPlaced);
        searchAhead = ahead;
        searchTicks = 0;
        if (!ahead) action = "Plant den Weg";
    }

    private static String continueSearch(Minecraft mc, LocalPlayer p) {
        long until = System.nanoTime() + SEARCH_NS_PER_TICK;
        boolean done = false;
        while (!done && System.nanoTime() < until) done = search.step(64);
        // Zeit um, oder das Ziel liegt hinter ungeladenen Chunks und es geht nicht mehr naeher heran
        if (!done && (++searchTicks > MAX_SEARCH_TICKS
                || (searchWorld.sawUnloaded && search.sinceBest() > FRONTIER_GIVE_UP))) {
            done = search.finishNow();
        }
        if (!done) return null;

        List<Step> r = search.result();
        boolean complete = search.reachedGoal();
        boolean unloaded = searchWorld.sawUnloaded;
        boolean ahead = searchAhead;
        search = null;
        searchWorld = null;
        if (ahead) {
            next = r;
            nextComplete = complete;
            return null;
        }
        if (!r.isEmpty()) {
            setPath(r, complete);
            chunkWaits = 0;
            return null;
        }
        return noPath(mc, p, unloaded);
    }

    private static String noPath(Minecraft mc, LocalPlayer p, boolean unloaded) {
        if (!blocked.isEmpty()) {
            // vielleicht war nur eine gesperrte Stelle schuld: noch einmal ohne Sperren
            blocked.clear();
            replans++;
            waitTicks = 5;
            return null;
        }
        if (unloaded && ++chunkWaits <= 15) {
            // das Ziel liegt hinter noch nicht geladenen Chunks
            action = "Wartet auf Chunks";
            waitTicks = 40;
            return null;
        }
        if (goalY > Mth.floor(p.getY()) + 1 && allowPlace && TestClient.findBuildSlot(mc) < 0) {
            return "Kein Weg gefunden (für höhere Ziele Baublöcke wie Bruchstein in die Hotbar)";
        }
        return "Kein Weg zum Ziel gefunden";
    }

    private static void setPath(List<Step> r, boolean complete) {
        path = r;
        pathComplete = complete;
        index = 0;
        lookahead = -1;
        onLine = false;
        offTicks = 0;
        newStep();
    }

    private static void newStep() {
        stepTicks = 0;
        stillTicks = 0;
        placeTries = 0;
        doorTries = 0;
        sneakTicks = 0;
        digPos = null;
    }

    private static void setIndex(int i) {
        if (i != index) {
            index = i;
            newStep();
        }
    }

    /** Weg verwerfen und neu planen; blockCurrent: der aktuelle Schritt hat nicht geklappt, ihn meiden. */
    private static void replan(boolean blockCurrent) {
        if (blockCurrent && path != null && index < path.size()) {
            Step s = path.get(index);
            blocked.add(Pathfinder.edgeKey(s.fx, s.fy, s.fz, s.tx, s.ty, s.tz));
        }
        path = null;
        next = null;
        search = null;
        searchWorld = null;
        index = 0;
        lookahead = -1;
        onLine = false;
        offTicks = 0;
        newStep();
        replans++;
        waitTicks = 1;
        action = "Plant den Weg";
        clearKeys(); // nicht mit Schwung weiterlaufen
    }

    // ---------- Position ----------

    /** Knoten, auf dem der Spieler steht (bzw. schwimmt); null, wenn er gerade in der Luft ist. */
    private static int[] playerNode(Pathfinder.Mover m, LocalPlayer p) {
        boolean water = p.isInWater();
        if (!p.onGround() && !water) return null;
        double px = p.getX();
        double pz = p.getZ();
        int y0 = Mth.floor(p.getY() + (water ? 0.2 : 0.51));
        // erst die Spalte unter der Mitte, dann die anderen unter dem Koerper (Stehen an einer Kante)
        int[][] cols = {
                {Mth.floor(px), Mth.floor(pz)},
                {Mth.floor(px - 0.3), Mth.floor(pz - 0.3)}, {Mth.floor(px + 0.3), Mth.floor(pz - 0.3)},
                {Mth.floor(px - 0.3), Mth.floor(pz + 0.3)}, {Mth.floor(px + 0.3), Mth.floor(pz + 0.3)}};
        int[] dys = {0, 1, -1};
        for (int[] c : cols) {
            for (int dy : dys) {
                int y = y0 + dy;
                if (m.floor(c[0], y, c[1]) && m.open(c[0], y, c[1], true) && m.open(c[0], y + 1, c[1], false)) {
                    return new int[] {c[0], y, c[1]};
                }
            }
        }
        return null;
    }

    private static boolean atGoal(int x, int y, int z) {
        return Pathfinder.atGoal(x, y, z, goalX, goalY, goalZ, miningMode, exactGoal);
    }

    private static double goalDistance(LocalPlayer p) {
        double dx = goalX + 0.5 - p.getX();
        double dz = goalZ + 0.5 - p.getZ();
        return Math.sqrt(dx * dx + dz * dz) + Math.abs(goalY - p.getY());
    }

    /** Index auf dem Weg an die Position des Spielers anpassen. */
    private static void sync(int[] n, LocalPlayer p) {
        int lo = Math.max(0, index - 3);
        int hi = Math.min(path.size(), index + LOOKAHEAD + 2);
        for (int i = lo; i < hi; i++) {
            Step s = path.get(i);
            if (s.tx == n[0] && s.ty == n[1] && s.tz == n[2]) {
                setIndex(i + 1);
                offTicks = 0;
                return;
            }
        }
        for (int i = lo; i < hi; i++) {
            Step s = path.get(i);
            if (s.fx == n[0] && s.fy == n[1] && s.fz == n[2]) {
                setIndex(i);
                offTicks = 0;
                return;
            }
        }
        // Auf einer geprueft freien Abkuerzung (Gerade quer zu den Wegpunkten): das ist kein Abweichen.
        // Der Index rueckt bis zum naechstgelegenen Wegpunkt vor, damit das Ziel der Geraden mitwandert.
        if (onLine && lookahead > index && lookahead < path.size() && n[1] == path.get(index).fy) {
            int best = index;
            double bd = Double.MAX_VALUE;
            for (int i = index; i <= lookahead; i++) {
                Step s = path.get(i);
                double dx = s.tx + 0.5 - p.getX();
                double dz = s.tz + 0.5 - p.getZ();
                double dd = dx * dx + dz * dz;
                if (dd < bd) {
                    bd = dd;
                    best = i;
                }
            }
            setIndex(best);
            offTicks = 0;
            return;
        }
        offTicks++;
    }

    // ---------- Schritte ausfuehren ----------

    private static void execute(Minecraft mc, Level level, LocalPlayer p, Pathfinder.Mover m, Step s) {
        if (p.isInWater()) kJump = true; // an der Oberflaeche bleiben
        if (s.move == Move.DOWN) {
            down(mc, level, p, s);
            return;
        }
        if (s.move == Move.PILLAR) {
            pillar(mc, level, p, s);
            return;
        }
        // Hindernisse abbauen (von oben nach unten)
        for (int[] b : s.breaks) {
            BlockPos bp = new BlockPos(b[0], b[1], b[2]);
            if (!passable(level, bp)) {
                dig(mc, level, p, bp);
                return;
            }
        }
        if (openDoor(mc, level, p, s)) return;
        if (s.place != null) {
            BlockPos pp = new BlockPos(s.place[0], s.place[1], s.place[2]);
            if (!solid(level, pp)) {
                bridge(mc, level, p, s, pp);
                return;
            }
        }
        switch (s.move) {
            case ASCEND -> ascend(level, p, s);
            case PARKOUR -> {
                Look.releaseSneak(); // geduckt springt er zu kurz
                parkour(p, s);
            }
            default -> walk(level, p, m, s); // WALK, DIAGONAL, DESCEND, FALL
        }
    }

    /** Laufen, auch herunterfallen. Freie gerade Strecken werden in einem Zug gelaufen. */
    private static void walk(Level level, LocalPlayer p, Pathfinder.Mover m, Step s) {
        if (s.place != null && s.move == Move.WALK) {
            bridgeWalk(p, s); // Bruecke: rueckwaerts weiter auf den neuen Block
            return;
        }
        action = p.isInWater() ? "Schwimmt" : "Läuft";
        double tx = s.tx + 0.5;
        double tz = s.tz + 0.5;
        boolean flat = (s.move == Move.WALK || s.move == Move.DIAGONAL) && s.ty == s.fy;
        lookahead = -1;
        onLine = false;
        if (flat && (p.onGround() || p.isInWater())) {
            int k = farthestVisible(level, m, p, s.fy);
            if (k >= index) {
                lookahead = k;
                onLine = k > index; // Gerade zu einem spaeteren Wegpunkt ist geprueft frei
                Step t = path.get(k);
                tx = t.tx + 0.5;
                tz = t.tz + 0.5;
            }
        }
        double dx = tx - p.getX();
        double dz = tz - p.getZ();
        double len = Math.max(1e-6, Math.sqrt(dx * dx + dz * dz));
        // Danach eine Kurve, eine Kante oder eine Aktion? Dann genau am Punkt anhalten.
        boolean precise = needsStop(m, lookahead >= 0 ? lookahead : index, dx / len, dz / len);
        boolean falling = s.move == Move.FALL || s.move == Move.DESCEND;
        if (falling) Look.releaseSneak(); // geduckt kaeme er nicht ueber die Kante
        drive(p, tx, tz, precise || falling, !falling && !p.isInWater());
        if (falling && !p.onGround()) {
            // ueber der Landestelle nicht weiter nach vorne druecken
            double ox = p.getX() - (s.tx + 0.5);
            double oz = p.getZ() - (s.tz + 0.5);
            if (Math.abs(ox) < 0.3 && Math.abs(oz) < 0.3) {
                kFwd = false;
                kSprint = false;
            }
        }
        // an einer Ecke haengen geblieben: kurz springen
        if (stillTicks > 6 && p.horizontalCollision && p.onGround()) kJump = true;
        if (stillTicks > STUCK_TICKS) replan(true);
    }

    /** Eine Stufe hoch: hinlaufen und springen (bei halben Stufen reicht Laufen). */
    private static void ascend(Level level, LocalPlayer p, Step s) {
        action = "Springt hoch";
        double tx = s.tx + 0.5;
        double tz = s.tz + 0.5;
        drive(p, tx, tz, false, false);
        double rise = topOf(level, s.tx, s.fy, s.tz) - p.getY();
        double dx = tx - p.getX();
        double dz = tz - p.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float err = Math.abs(Mth.wrapDegrees(yaw - p.getYRot()));
        if ((rise > 0.6 || p.isInWater()) && dist < 1.7 && err < 35.0f) kJump = true;
        if (stillTicks > STUCK_TICKS) replan(true);
    }

    /** Ueber eine Luecke springen: mit Anlauf an der Kante abspringen. */
    private static void parkour(LocalPlayer p, Step s) {
        action = "Springt";
        int dx = Integer.signum(s.tx - s.fx);
        int dz = Integer.signum(s.tz - s.fz);
        double cx = s.fx + 0.5;
        double cz = s.fz + 0.5;
        if (p.onGround()) {
            double along = (p.getX() - cx) * dx + (p.getZ() - cz) * dz;
            double side = (p.getX() - cx) * dz - (p.getZ() - cz) * dx;
            if (along < -0.6 || Math.abs(side) > 0.45) {
                drive(p, cx, cz, true, false); // erst auf den Absprungblock
            } else {
                float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
                Look.turn(p, yaw, 0.0f, 40.0f, 15.0f, 0.0f);
                float err = Math.abs(Mth.wrapDegrees(yaw - p.getYRot()));
                kFwd = err < 25.0f;
                kJump = kFwd && along > 0.15;
            }
            // ohne Sprint springen: ein Sprintsprung (gut 4 Bloecke) wuerde ueber die Landestelle hinaus fliegen
            if (p.isSprinting()) p.setSprinting(false);
        } else {
            drive(p, s.tx + 0.5, s.tz + 0.5, true, false); // in der Luft zur Landestelle steuern
            kJump = false;
        }
        if (stillTicks > STUCK_TICKS) replan(true);
    }

    /**
     * Nach oben bauen wie ein Spieler: schleichen, in die Mitte, senkrecht nach unten schauen, springen
     * und im Sprung den Block unter sich setzen.
     */
    private static void pillar(Minecraft mc, Level level, LocalPlayer p, Step s) {
        action = "Baut hoch";
        stillTicks = 0;
        kSneak = !Look.human; // senkrecht hochbauen geht auch ungeduckt (beim Springen bleibt er ueber dem Block)
        if (p.isSprinting()) p.setSprinting(false);
        BlockPos placePos = new BlockPos(s.fx, s.fy, s.fz);
        BlockPos base = placePos.below();
        if (solid(level, placePos)) {
            // Block steht schon: landen lassen
            if (stepTicks > 40) replan(false);
            return;
        }
        if (p.onGround() && !center(p, s.fx + 0.5, s.fz + 0.5, 0.2)) return;
        for (int[] b : s.breaks) { // Platz ueber dem Kopf
            BlockPos bp = new BlockPos(b[0], b[1], b[2]);
            if (!passable(level, bp)) {
                dig(mc, level, p, bp);
                return;
            }
        }
        if (TestClient.findBuildSlot(mc) < 0 || !solid(level, base)) {
            replan(false);
            return;
        }
        double hx = s.fx + 0.5;
        double hy = s.fy;
        double hz = s.fz + 0.5;
        Look.holdSteady();
        boolean aimed = Look.turn(p, p.getYRot(), 90.0f, 30.0f, 30.0f, 6.0f); // senkrecht nach unten
        if (p.onGround()) {
            kJump = aimed;
        } else if (aimed && p.getY() >= s.fy + 1.05 && placeCooldown <= 0) {
            if (++placeTries > 6) {
                replan(true);
                return;
            }
            TestClient.placeBlock(mc, base, Direction.UP, new Vec3(hx, hy, hz), placePos);
            placeCooldown = 3;
        }
        if (stepTicks > 100) replan(true);
    }

    /** Senkrecht nach unten graben: in die Mitte stellen, Block unter den Fuessen abbauen, fallen. */
    private static void down(Minecraft mc, Level level, LocalPlayer p, Step s) {
        action = "Gräbt nach unten";
        stillTicks = 0;
        BlockPos below = new BlockPos(s.fx, s.fy - 1, s.fz);
        if (passable(level, below)) {
            if (stepTicks > 60) replan(false); // faellt gerade
            return;
        }
        if (p.onGround() && !center(p, s.fx + 0.5, s.fz + 0.5, 0.15)) return;
        dig(mc, level, p, below);
    }

    /**
     * Bruecke wie ein Spieler: umdrehen (Ruecken zur Luecke), schleichen, rueckwaerts bis an die Kante
     * gehen und dann schraeg unter sich an die Seite des eigenen Blocks setzen. Das Schleichen verhindert,
     * dass er dabei herunterfaellt.
     */
    private static void bridge(Minecraft mc, Level level, LocalPlayer p, Step s, BlockPos placePos) {
        action = "Baut Brücke";
        stillTicks = 0;
        BlockPos against = new BlockPos(s.fx, s.fy - 1, s.fz);
        if (TestClient.findBuildSlot(mc) < 0 || !solid(level, against)) {
            replan(false); // keine Bloecke mehr: ohne Bauen neu planen
            return;
        }
        int dx = Integer.signum(s.tx - s.fx);
        int dz = Integer.signum(s.tz - s.fz);
        Direction face = dir(dx, dz);
        kSneak = true;
        sneakTicks++;
        if (p.isSprinting()) p.setSprinting(false);
        double cx = s.fx + 0.5;
        double cz = s.fz + 0.5;
        double along = (p.getX() - cx) * dx + (p.getZ() - cz) * dz; // > 0.5: ueber der Kante
        double side = (p.getX() - cx) * dz - (p.getZ() - cz) * dx;
        Vec3 hit = Vec3.atCenterOf(against).add(dx * 0.5, 0.0, dz * 0.5);
        float back = Look.yawOf(-dx, -dz);

        // 1. umdrehen und nach unten schauen (dabei stehen bleiben)
        boolean aimed;
        if (along > 0.5) {
            aimed = Look.aim(p, hit.x, hit.y, hit.z, 6.0f); // ueber der Kante: genau auf die Seite des Blocks
        } else {
            Look.turn(p, back, 78.0f, 30.0f, 25.0f, 0.0f);
            aimed = false;
        }
        if (Math.abs(Mth.wrapDegrees(back - p.getYRot())) > 25.0f) return;

        // 2. rueckwaerts an die Kante, seitlich auf der Mitte bleiben
        double mx = along < 0.62 ? dx : 0.0;
        double mz = along < 0.62 ? dz : 0.0;
        if (Math.abs(side) > 0.12) {
            mx -= side * dz;
            mz += side * dx;
        }
        push(p, mx, mz, 0.05);

        // 3. setzen, sobald er ueber der Kante steht (klemmt es, nach einer Weile trotzdem)
        if (((along > 0.5 && aimed) || sneakTicks > 40) && sneakTicks > 2 && placeCooldown <= 0) {
            if (++placeTries > 6) {
                replan(true);
                return;
            }
            TestClient.placeBlock(mc, against, face, hit, placePos);
            placeCooldown = 4;
        }
    }

    /** Ueber die gerade gesetzte Bruecke: rueckwaerts und geduckt weiter, der Blick bleibt nach hinten unten. */
    private static void bridgeWalk(LocalPlayer p, Step s) {
        action = "Baut Brücke";
        int dx = Integer.signum(s.tx - s.fx);
        int dz = Integer.signum(s.tz - s.fz);
        Look.turn(p, Look.yawOf(-dx, -dz), 78.0f, 30.0f, 25.0f, 0.0f);
        kSneak = true;
        if (p.isSprinting()) p.setSprinting(false);
        push(p, s.tx + 0.5 - p.getX(), s.tz + 0.5 - p.getZ(), 0.1);
        if (stillTicks > STUCK_TICKS) replan(true);
    }

    /** Geschlossene Tuer oder Zauntor auf dem naechsten Feld oeffnen. true = ist damit beschaeftigt. */
    private static boolean openDoor(Minecraft mc, Level level, LocalPlayer p, Step s) {
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos dp = new BlockPos(s.tx, s.ty + dy, s.tz);
            if (!closedDoor(level.getBlockState(dp))) continue;
            action = "Öffnet Tür";
            stillTicks = 0;
            Vec3 c = Vec3.atCenterOf(dp);
            boolean aimed = Look.aim(p, c.x, c.y, c.z, 6.0f);
            if (aimed && doorCooldown <= 0 && mc.gameMode != null) {
                if (++doorTries > 4) {
                    replan(true);
                    return true;
                }
                Direction face = dir(s.fx - s.tx, s.fz - s.tz);
                mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(c, face, dp, false));
                p.swing(InteractionHand.MAIN_HAND);
                doorCooldown = 10;
            }
            return true;
        }
        return false;
    }

    static boolean isOpenable(BlockState bs) {
        if (!bs.hasProperty(BlockStateProperties.OPEN)) return false;
        return (bs.getBlock() instanceof DoorBlock && !bs.is(Blocks.IRON_DOOR))
                || bs.getBlock() instanceof FenceGateBlock;
    }

    private static boolean closedDoor(BlockState bs) {
        return isOpenable(bs) && !bs.getValue(BlockStateProperties.OPEN);
    }

    /** Block abbauen: bestes Werkzeug nehmen, auf eine sichtbare Stelle zielen, Angriff halten. */
    private static void dig(Minecraft mc, Level level, LocalPlayer p, BlockPos pos) {
        action = "Baut ab";
        stillTicks = 0;
        BlockState bs = level.getBlockState(pos);
        float hardness = bs.getDestroySpeed(level, pos);
        if (!pos.equals(digPos)) {
            digPos = pos;
            digTicks = 0;
            double t = toolTicks(p, bs, hardness) * (p.isInWater() ? 5.0 : 1.0);
            digLimit = (int) Math.min(900.0, t * 2.5 + 60.0);
        }
        if (++digTicks > digLimit) {
            replan(true); // laesst sich nicht abbauen
            return;
        }
        selectTool(p, bs, hardness);
        double[] pt = TestClient.visiblePoint(level, p, pos);
        double ax = pt != null ? pt[0] : pos.getX() + 0.5;
        double ay = pt != null ? pt[1] : pos.getY() + 0.5;
        double az = pt != null ? pt[2] : pos.getZ() + 0.5;
        if (digTicks > 1) Look.holdSteady(); // beim Abbauen die Maus ruhig halten
        boolean aimed = Look.aim(p, ax, ay, az, 4.0f);
        boolean onTarget = false;
        HitResult hr = mc.hitResult;
        if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK) {
            BlockPos hp = bh.getBlockPos();
            // Gras, Blumen o.ae. davor duerfen gleich mit weg
            onTarget = hp.equals(pos) || passable(level, hp);
        }
        kAttack = aimed && onTarget;
        digging = kAttack;
        if (pt == null && digTicks > 60) replan(true); // weder sichtbar noch erreichbar
    }

    private static void selectTool(LocalPlayer p, BlockState bs, float hardness) {
        var inv = p.getInventory();
        int cur = inv.getSelectedSlot();
        int best = cur;
        double bestT = ticksWith(inv.getItem(cur), bs, hardness);
        for (int i = 0; i < 9; i++) {
            double t = ticksWith(inv.getItem(i), bs, hardness);
            if (t < bestT - 0.5) {
                best = i;
                bestT = t;
            }
        }
        if (best != cur) inv.setSelectedSlot(best);
    }

    /** Geschaetzte Ticks zum Abbauen mit dem besten Werkzeug der Hotbar. */
    static double toolTicks(LocalPlayer p, BlockState bs, float hardness) {
        if (hardness < 0.0f) return 1e6;
        if (hardness == 0.0f) return 1.0;
        if (p == null) return hardness * 30.0;
        var inv = p.getInventory();
        double best = Double.MAX_VALUE;
        for (int i = 0; i < 9; i++) best = Math.min(best, ticksWith(inv.getItem(i), bs, hardness));
        return best;
    }

    private static double ticksWith(ItemStack st, BlockState bs, float hardness) {
        if (hardness <= 0.0f) return 1.0;
        if (!toolOk.test(st)) return 1e6;
        float speed = st.getDestroySpeed(bs);
        boolean right = !bs.requiresCorrectToolForDrops() || st.isCorrectToolForDrops(bs);
        return Math.ceil(hardness * (right ? 30.0 : 100.0) / Math.max(0.1f, speed));
    }

    // ---------- Laufen und Zielen ----------

    /**
     * Zum Punkt laufen (Blick in Laufrichtung). precise: dort anhalten statt darueber hinaus zu laufen;
     * mayRun: Sprinten erlaubt.
     */
    private static void drive(LocalPlayer p, double tx, double tz, boolean precise, boolean mayRun) {
        double dx = tx - p.getX();
        double dz = tz - p.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.12) return;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        Look.turn(p, yaw, WALK_PITCH, 35.0f, 8.0f, 0.0f);
        if (precise && dist < 0.3) return;
        float err = Math.abs(Mth.wrapDegrees(yaw - p.getYRot()));
        kFwd = err < 60.0f;
        kSprint = kFwd && mayRun && sprint && err < 15.0f && (dist > 2.5 || !precise);
        // Sprinten endet im Spiel erst beim Loslassen: vor einem genauen Halt selbst beenden
        if (!kSprint && precise && dist < 2.0 && p.isSprinting()) p.setSprinting(false);
    }

    private static int centerTap;

    /**
     * Langsam in die Mitte eines Blocks schieben, ohne sich umzudrehen. true = ist dort.
     * Menschlich: kurze Tipper auf die Tasten statt geduckt zu schleichen (das sah nach Bot aus).
     */
    private static boolean center(LocalPlayer p, double cx, double cz, double tol) {
        double dx = cx - p.getX();
        double dz = cz - p.getZ();
        if (Math.abs(dx) <= tol && Math.abs(dz) <= tol) {
            centerTap = 0;
            return true;
        }
        if (Look.human) {
            // 1 Tick druecken, 2 Ticks ausrollen lassen: kleine, gleichmaessige Schritte
            if (centerTap++ % 3 == 0) push(p, dx, dz, 0.05);
            return false;
        }
        kSneak = true;
        push(p, dx, dz, 0.05);
        return false;
    }

    /** In Weltrichtung (vx, vz) gehen, egal wohin er schaut: W/A/S/D passend zum Blick waehlen. */
    private static void push(LocalPlayer p, double vx, double vz, double dead) {
        double yaw = Math.toRadians(p.getYRot());
        double fwd = -vx * Math.sin(yaw) + vz * Math.cos(yaw);
        double left = vx * Math.cos(yaw) + vz * Math.sin(yaw);
        kFwd = fwd > dead;
        kBack = fwd < -dead;
        kLeft = left > dead;
        kRight = left < -dead;
    }

    /** Blickrichtung (Yaw) fuer die Weltrichtung (dx, dz). */    /** Stillstehen (im Wasser oben bleiben). */
    private static void idle(LocalPlayer p) {
        kJump = p.isInWater();
    }
    /** Blick weich drehen (schnell, aber ohne Ruck). true = innerhalb der Toleranz. */    /** Weitester Wegpunkt, der in gerader Linie frei erreichbar ist (gleiche Hoehe, ohne Aktionen). */
    private static int farthestVisible(Level level, Pathfinder.Mover m, LocalPlayer p, int y) {
        int end = index;
        int max = Math.min(path.size(), index + LOOKAHEAD);
        while (end < max) {
            Step s = path.get(end);
            if ((s.move != Move.WALK && s.move != Move.DIAGONAL) || s.fy != y || s.ty != y || s.hasActions()) break;
            if (closedDoor(level.getBlockState(new BlockPos(s.tx, s.ty, s.tz)))) break;
            end++;
        }
        for (int k = end - 1; k > index; k--) {
            Step s = path.get(k);
            if (lineClear(m, p.getX(), p.getZ(), s.tx + 0.5, s.tz + 0.5, y)) return k;
        }
        return end > index ? index : -1;
    }

    /** Kann der Spieler (0,6 breit) geradeaus von a nach b laufen, ohne anzustossen oder zu fallen? */
    private static boolean lineClear(Pathfinder.Mover m, double ax, double az, double bx, double bz, int y) {
        double dx = bx - ax;
        double dz = bz - az;
        double len = Math.sqrt(dx * dx + dz * dz);
        int n = Math.max(1, (int) Math.ceil(len / 0.25));
        for (int i = 0; i <= n; i++) {
            double x = ax + dx * i / n;
            double z = az + dz * i / n;
            if (!m.floor(Mth.floor(x), y, Mth.floor(z))) return false;
            for (int c = 0; c < 4; c++) {
                int qx = Mth.floor(x + ((c & 1) == 0 ? -0.31 : 0.31));
                int qz = Mth.floor(z + ((c & 2) == 0 ? -0.31 : 0.31));
                if (!m.open(qx, y, qz, true) || !m.open(qx, y + 1, qz, false)) return false;
            }
        }
        return true;
    }

    /**
     * Muss am Ende der Strecke genau angehalten werden? Ja, wenn danach eine Aktion kommt oder wenn
     * man beim Weiterrutschen in Laufrichtung keinen sicheren Boden haette (Kante, Lava, Loch).
     */
    private static boolean needsStop(Pathfinder.Mover m, int i, double hx, double hz) {
        if (i < 0 || i >= path.size()) return true;
        Step a = path.get(i);
        Step b;
        if (i + 1 < path.size()) {
            b = path.get(i + 1);
        } else if (!pathComplete && next != null && !next.isEmpty()) {
            b = next.get(0); // Teilweg: der Anschluss ist schon geplant
        } else {
            return true;
        }
        if (b.place != null || b.move == Move.PILLAR || b.move == Move.DOWN || !b.breaks.isEmpty()) return true;
        double bx = b.tx - b.fx;
        double bz = b.tz - b.fz;
        double bl = Math.sqrt(bx * bx + bz * bz);
        if (bl > 0 && (hx * bx + hz * bz) / bl > 0.7) return false; // geht in die gleiche Richtung weiter
        int ox = Mth.floor(a.tx + 0.5 + hx * 0.9);
        int oz = Mth.floor(a.tz + 0.5 + hz * 0.9);
        return !(m.floor(ox, a.ty, oz) && m.open(ox, a.ty, oz, true) && m.open(ox, a.ty + 1, oz, false));
    }

    // ---------- Hilfen ----------

    private static boolean passable(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    private static boolean solid(Level level, BlockPos pos) {
        return !passable(level, pos);
    }

    /** Oberkante des Blocks bei (x,y,z) als Weltkoordinate (leer: y). */
    private static double topOf(Level level, int x, int y, int z) {
        BlockPos bp = new BlockPos(x, y, z);
        VoxelShape sh = level.getBlockState(bp).getCollisionShape(level, bp);
        return sh.isEmpty() ? y : y + sh.max(Direction.Axis.Y);
    }

    private static Direction dir(int dx, int dz) {
        if (dx > 0) return Direction.EAST;
        if (dx < 0) return Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static void clearKeys() {
        kFwd = false;
        kBack = false;
        kLeft = false;
        kRight = false;
        kJump = false;
        kSprint = false;
        kSneak = false;
        kAttack = false;
    }

    private static void applyKeys(Minecraft mc) {
        var o = mc.options;
        Look.setKey(o.keyUp, kFwd);
        Look.setKey(o.keyDown, kBack);
        Look.setKey(o.keyLeft, kLeft);
        Look.setKey(o.keyRight, kRight);
        Look.setKey(o.keyJump, kJump);
        Look.setKey(o.keySprint, kSprint);
        Look.setSneak(o.keyShift, kSneak);
        Look.setKey(o.keyAttack, kAttack);
    }

    /** Taste setzen; klappt auch, wenn Schleichen/Sprinten im Spiel auf "Umschalten" steht. */    // ---------- Anzeige ----------

    /** Weg in der Welt zeichnen: Linie, abzubauende (rot) und zu setzende (gruen) Bloecke. */
    static void render(GuiGraphicsExtractor g) {
        if (!active || !showPath || path == null) return;
        int color = Theme.accent() | 0xFF000000;
        int faded = (color & 0x00FFFFFF) | 0x80000000;
        int shown = 0;
        for (int i = Math.max(0, index - 1); i < path.size() && shown < 300; i++, shown++) {
            Step s = path.get(i);
            Render3D.worldLine(g, s.fx + 0.5, s.fy + 0.05, s.fz + 0.5, s.tx + 0.5, s.ty + 0.05, s.tz + 0.5,
                    i < index ? faded : color);
        }
        if (next != null) {
            for (int i = 0; i < next.size() && shown < 300; i++, shown++) {
                Step s = next.get(i);
                Render3D.worldLine(g, s.fx + 0.5, s.fy + 0.05, s.fz + 0.5, s.tx + 0.5, s.ty + 0.05, s.tz + 0.5,
                        faded);
            }
        }
        for (int i = index; i < Math.min(path.size(), index + 8); i++) {
            Step s = path.get(i);
            for (int[] b : s.breaks) {
                Render3D.outline(g, new AABB(b[0], b[1], b[2], b[0] + 1, b[1] + 1, b[2] + 1), 0xFFFF5A5A, false);
            }
            if (s.place != null) {
                int[] q = s.place;
                Render3D.outline(g, new AABB(q[0], q[1], q[2], q[0] + 1, q[1] + 1, q[2] + 1), 0xFF5AFF8C, false);
            }
            if (s.move == Move.PILLAR) {
                Render3D.outline(g, new AABB(s.fx, s.fy, s.fz, s.fx + 1, s.fy + 1, s.fz + 1), 0xFF5AFF8C, false);
            }
        }
    }
}
