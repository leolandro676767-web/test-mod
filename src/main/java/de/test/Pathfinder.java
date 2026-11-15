package de.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Wegsuche wie bei Baritone (A*), ohne Minecraft-Klassen - dadurch auch ausserhalb des Spiels
 * testbar. Ein Knoten ist die Position der Fuesse. Bewegungen haben Kosten in Ticks; Abbauen und
 * Bauen sind teure Notloesungen, damit der Weg lieber aussen herum, ueber Stufen und durch Luecken
 * fuehrt, als sich durchzugraben.
 * <p>Die Suche laeuft in Haeppchen ({@link Search#step}), damit das Spiel nie haengt. Ist das Ziel zu
 * weit weg, liefert sie einen Teilweg bis zum naechstgelegenen Punkt; von dort wird weitergeplant.
 */
final class Pathfinder {
    private Pathfinder() {
    }

    // ---------- Welt (wird im Spiel aus den Bloecken gelesen, im Test aus einem Raster) ----------

    /** Block wurde nicht geladen (oder liegt ausserhalb der Welt). */
    static final int UNLOADED = 1;
    /** Kein Hindernis: Luft, Gras, Blumen, Fackeln ... */
    static final int PASS = 2;
    /** Wasser ohne festen Block. */
    static final int WATER = 4;
    /** Man kann darauf stehen (Oberkante zwischen halber und voller Hoehe). */
    static final int STAND = 8;
    /** Gefaehrlich zum Hineinlaufen: Lava, Feuer, Kaktus, Beerenstrauch, Spinnennetz, Pulverschnee. */
    static final int AVOID = 16;
    /** Gefaehrlich zum Draufstellen: Magma, Lagerfeuer. */
    static final int AVOID_FLOOR = 32;
    /** Darf abgebaut werden. */
    static final int BREAKABLE = 64;
    /** Faellt herunter, wenn darunter Platz ist (Sand, Kies). */
    static final int FALLING = 128;
    /** Enthaelt Fluessigkeit (Wasser oder Lava, auch in Bloecken). */
    static final int FLUID = 256;

    interface World {
        int cell(int x, int y, int z);

        /** Geschaetzte Ticks zum Abbauen mit dem besten Werkzeug (nur fuer BREAKABLE gefragt). */
        double breakTicks(int x, int y, int z);
    }

    // ---------- Einstellungen ----------

    static final class Options {
        /** Ziel ist ein Block zum Abbauen: man muss direkt daneben (Fuesse oder Kopf), darauf oder darunter stehen. */
        boolean mineGoal = false;
        /** Genau auf dem Zielfeld ankommen (nicht nur daneben). */
        boolean exactGoal = false;
        boolean allowBreak = true;
        boolean allowPlace = true;
        boolean placeAvailable = true; // Baubloecke in der Hotbar
        boolean allowParkour = true;
        int maxFall = 3;
        /** Grundkosten fuer einen abgebauten Block (Ticks); hoch = lieber aussen herum. */
        double breakPenalty = 40.0;
        int maxNodes = 60_000;
        /** Kanten, die beim Abfahren gescheitert sind (werden gemieden). */
        Set<Long> blockedEdges = Collections.emptySet();
    }

    // ---------- Kosten (in Ticks) ----------

    static final double INF = 1e9;
    static final double WALK = 20.0 / 4.317;        // 4.63 Ticks je Block
    static final double DIAG = WALK * Math.sqrt(2);
    static final double JUMP = 2.5;
    static final double WATER_EXTRA = WALK * 1.5;
    static final double PLACE = 20.0;
    static final double PILLAR = 30.0;
    /** Maximale Fallhoehe, wenn unten Wasser ist (Wasser faengt den Fall ab). */
    static final int MAX_WATER_FALL = 23;

    /**
     * Ist der Knoten (Fuesse bei x,y,z) am Ziel? Normal: auf dem Zielfeld oder direkt daneben, hoechstens
     * einen Block hoeher/tiefer. mineGoal: der Zielblock beruehrt das Feld der Fuesse oder des Kopfes -
     * dann hat er sicher eine freie Seite zum Spieler und ist in Reichweite. (Nicht darauf stehen: sonst
     * baut man sich den Boden unter den Fuessen weg.)
     */
    static boolean atGoal(int x, int y, int z, int gx, int gy, int gz, boolean mineGoal) {
        int dx = x - gx;
        int dy = y - gy;
        int dz = z - gz;
        if (!mineGoal) return dx * dx + dz * dz <= 1 && Math.abs(dy) <= 1;
        int side = Math.abs(dx) + Math.abs(dz);
        if (side == 1) return dy == 0 || dy == -1;    // neben Fuessen oder Kopf
        if (side == 0) return dy == -2;               // direkt darunter (Block ueber dem Kopf)
        return false;
    }

    /** Wie oben; exact = genau auf dem Zielfeld stehen. */
    static boolean atGoal(int x, int y, int z, int gx, int gy, int gz, boolean mineGoal, boolean exact) {
        if (exact && !mineGoal) return x == gx && y == gy && z == gz;
        return atGoal(x, y, z, gx, gy, gz, mineGoal);
    }

    static double fallCost(int n) {
        return n <= 0 ? 0 : 1.0 + 2.5 * n;
    }

    // ---------- Bewegungen ----------

    enum Move {
        WALK, DIAGONAL, ASCEND, DESCEND, FALL, PARKOUR, PILLAR, DOWN
    }

    /** Ein Schritt des Wegs: von (fx,fy,fz) nach (tx,ty,tz), dazu abzubauende/zu setzende Bloecke. */
    static final class Step {
        final int fx, fy, fz, tx, ty, tz;
        final Move move;
        /** Abzubauende Bloecke {x,y,z}, von oben nach unten. */
        final List<int[]> breaks = new ArrayList<>();
        /** Zu setzender Block {x,y,z} (Bruecke) oder null. */
        int[] place;

        Step(int fx, int fy, int fz, int tx, int ty, int tz, Move move) {
            this.fx = fx;
            this.fy = fy;
            this.fz = fz;
            this.tx = tx;
            this.ty = ty;
            this.tz = tz;
            this.move = move;
        }

        boolean hasActions() {
            return !breaks.isEmpty() || place != null || move == Move.PILLAR || move == Move.DOWN;
        }

        @Override
        public String toString() {
            return move + " " + fx + "," + fy + "," + fz + " -> " + tx + "," + ty + "," + tz
                    + (breaks.isEmpty() ? "" : " break" + breaks.size()) + (place == null ? "" : " place");
        }
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static long edgeKey(int fx, int fy, int fz, int tx, int ty, int tz) {
        return key(fx, fy, fz) * 31 + key(tx, ty, tz);
    }

    private static final int[][] CARDINAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] DIAGONALS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /**
     * Bewertet alle Schritte, die von einem Knoten aus moeglich sind. out bekommt die Ziele und
     * Kosten; mit detail != null werden fuer genau einen Schritt die Aktionen eingetragen.
     */
    static final class Mover {
        final World w;
        final Options o;
        /** Abbauen aus dem Wasser heraus dauert viel laenger. */
        private double breakFactor = 1.0;

        Mover(World w, Options o) {
            this.w = w;
            this.o = o;
        }

        private boolean has(int x, int y, int z, int flag) {
            return (w.cell(x, y, z) & flag) != 0;
        }

        /** Kann man bei (x,y,z) stehen? Fester Block darunter - oder an der Wasseroberflaeche schwimmen. */
        boolean floor(int x, int y, int z) {
            int c = w.cell(x, y - 1, z);
            if ((c & STAND) != 0 && (c & (AVOID_FLOOR | AVOID | UNLOADED)) == 0) return true;
            return swim(x, y, z);
        }

        /** Fester Boden (nicht nur schwimmend): nur davon aus kann man bauen und springen. */
        boolean solidFloor(int x, int y, int z) {
            int c = w.cell(x, y - 1, z);
            return (c & STAND) != 0 && (c & (AVOID_FLOOR | AVOID | UNLOADED)) == 0;
        }

        /** Schwimmen an der Oberflaeche: Fuesse im Wasser, Kopf in der Luft. */
        boolean swim(int x, int y, int z) {
            return (w.cell(x, y, z) & WATER) != 0 && (w.cell(x, y + 1, z) & PASS) != 0;
        }

        /** Laesst sich ein Block abbauen, ohne Fluessigkeit oder fallenden Sand freizulegen? */
        boolean canBreak(int x, int y, int z) {
            if (!o.allowBreak) return false;
            int c = w.cell(x, y, z);
            if ((c & BREAKABLE) == 0 || (c & (AVOID | UNLOADED)) != 0) return false;
            if (has(x, y + 1, z, FALLING | FLUID)) return false;
            return !has(x + 1, y, z, FLUID) && !has(x - 1, y, z, FLUID)
                    && !has(x, y, z + 1, FLUID) && !has(x, y, z - 1, FLUID);
        }

        double breakCost(int x, int y, int z) {
            return o.breakPenalty + w.breakTicks(x, y, z) * breakFactor;
        }

        /**
         * Kosten, um durch einen Block zu gehen (0 = frei). feet: Fusshoehe (dort ist Wasser erlaubt).
         * breaks != null: abzubauenden Block eintragen.
         */
        double clear(int x, int y, int z, boolean feet, boolean mayBreak, List<int[]> breaks) {
            int c = w.cell(x, y, z);
            if ((c & (UNLOADED | AVOID)) != 0) return INF;
            if ((c & PASS) != 0) return 0;
            if ((c & WATER) != 0) return feet ? WATER_EXTRA : INF;
            if (mayBreak && canBreak(x, y, z)) {
                if (breaks != null) breaks.add(new int[] {x, y, z});
                return breakCost(x, y, z);
            }
            return INF;
        }

        /** Nur frei (ohne Abbauen), Wasser auf Fusshoehe erlaubt. */
        boolean open(int x, int y, int z, boolean feet) {
            int c = w.cell(x, y, z);
            if ((c & (UNLOADED | AVOID)) != 0) return false;
            return (c & PASS) != 0 || (feet && (c & WATER) != 0);
        }

        interface Sink {
            void add(int x, int y, int z, Move m, double cost);
        }

        /**
         * Alle Nachbarn eines Knotens. ownFloor: Boden unter dem Knoten ist fest (auch wenn er erst
         * gesetzt wird, z.B. nach einer Saeule).
         */
        void neighbours(int x, int y, int z, boolean ownFloor, Sink out) {
            boolean inWater = has(x, y, z, WATER);
            breakFactor = inWater ? 5.0 : 1.0;
            for (int[] d : CARDINAL) {
                int dx = d[0];
                int dz = d[1];
                int tx = x + dx;
                int tz = z + dz;
                // geradeaus
                double c = walk(x, y, z, dx, dz, ownFloor, null);
                if (c < INF) out.add(tx, y, tz, Move.WALK, c);
                // eine Stufe hoch
                c = ascend(x, y, z, dx, dz, null);
                if (c < INF) out.add(tx, y + 1, tz, Move.ASCEND, c);
                // eine Stufe runter mit Abbauen (ohne Abbauen deckt FALL das ab)
                c = descendBreak(x, y, z, dx, dz, null);
                if (c < INF) out.add(tx, y - 1, tz, Move.DESCEND, c);
                // herunterfallen
                int n = fallDepth(x, y, z, dx, dz);
                if (n > 0) out.add(tx, y - n, tz, Move.FALL, WALK + fallCost(n) + (inWater ? WATER_EXTRA : 0));
                // ueber eine Luecke springen
                if (o.allowParkour && !inWater && ownFloor) {
                    for (int gap = 1; gap <= 2; gap++) {
                        c = parkour(x, y, z, dx, dz, gap);
                        if (c < INF) out.add(x + dx * (gap + 1), y, z + dz * (gap + 1), Move.PARKOUR, c);
                    }
                }
            }
            for (int[] d : DIAGONALS) {
                double c = diagonal(x, y, z, d[0], d[1]);
                if (c < INF) out.add(x + d[0], y, z + d[1], Move.DIAGONAL, c);
                int n = fallDepth(x, y, z, d[0], d[1]);
                if (n > 0 && corners(x, y, z, d[0], d[1])) {
                    out.add(x + d[0], y - n, z + d[1], Move.FALL, DIAG + fallCost(n));
                }
            }
            double c = pillar(x, y, z, ownFloor, null);
            if (c < INF) out.add(x, y + 1, z, Move.PILLAR, c);
            c = down(x, y, z, null);
            if (c < INF) out.add(x, y - 1, z, Move.DOWN, c);
        }

        double walk(int x, int y, int z, int dx, int dz, boolean ownFloor, Step detail) {
            int tx = x + dx;
            int tz = z + dz;
            List<int[]> br = detail != null ? detail.breaks : null;
            double cost = WALK;
            cost += clear(tx, y + 1, tz, false, true, br);
            if (cost >= INF) return INF;
            cost += clear(tx, y, tz, true, true, br);
            if (cost >= INF) return INF;
            if (!floor(tx, y, tz)) {
                // Luecke: Bruecke bauen (gegen den eigenen Boden)
                int f = w.cell(tx, y - 1, tz);
                boolean replaceable = (f & (PASS | WATER)) != 0 && (f & (AVOID | UNLOADED)) == 0;
                if (!o.allowPlace || !o.placeAvailable || !ownFloor || !replaceable) return INF;
                // nur wenn darunter nicht gleich die Tiefe/Lava wartet, falls etwas schiefgeht
                if (has(tx, y - 2, tz, AVOID)) return INF;
                if (detail != null) detail.place = new int[] {tx, y - 1, tz};
                cost += PLACE;
            }
            return cost;
        }

        boolean corners(int x, int y, int z, int dx, int dz) {
            return open(x + dx, y, z, true) && open(x + dx, y + 1, z, false)
                    && open(x, y, z + dz, true) && open(x, y + 1, z + dz, false);
        }

        double diagonal(int x, int y, int z, int dx, int dz) {
            int tx = x + dx;
            int tz = z + dz;
            if (!floor(tx, y, tz) || !open(tx, y, tz, true) || !open(tx, y + 1, tz, false)) return INF;
            if (!corners(x, y, z, dx, dz)) return INF;
            return DIAG + (has(tx, y, tz, WATER) ? WATER_EXTRA : 0);
        }

        double ascend(int x, int y, int z, int dx, int dz, Step detail) {
            int tx = x + dx;
            int tz = z + dz;
            int step = w.cell(tx, y, tz);
            if ((step & STAND) == 0 || (step & (AVOID_FLOOR | AVOID | UNLOADED)) != 0) return INF;
            List<int[]> br = detail != null ? detail.breaks : null;
            double cost = WALK + JUMP;
            cost += clear(x, y + 2, z, false, true, br);      // Platz zum Springen ueber dem Kopf
            if (cost >= INF) return INF;
            cost += clear(tx, y + 2, tz, false, true, br);    // Kopf oben
            if (cost >= INF) return INF;
            cost += clear(tx, y + 1, tz, true, true, br);     // Fuesse oben
            return cost;
        }

        double descendBreak(int x, int y, int z, int dx, int dz, Step detail) {
            int tx = x + dx;
            int tz = z + dz;
            if (!o.allowBreak) return INF;
            if (!open(tx, y - 1, tz, true) || !floor(tx, y - 1, tz)) return INF;
            List<int[]> br = new ArrayList<>();
            double cost = WALK + fallCost(1);
            cost += clear(tx, y + 1, tz, false, true, br);
            if (cost >= INF) return INF;
            cost += clear(tx, y, tz, true, true, br);
            if (cost >= INF || br.isEmpty()) return INF; // ohne Abbauen ist es ein normaler Fall
            if (detail != null) detail.breaks.addAll(br);
            return cost;
        }

        /** Wie tief faellt man, wenn man in diese Richtung von der Kante laeuft? 0 = kein Fall. */
        int fallDepth(int x, int y, int z, int dx, int dz) {
            int tx = x + dx;
            int tz = z + dz;
            if (!open(tx, y + 1, tz, false) || !open(tx, y, tz, true)) return 0;
            if (floor(tx, y, tz)) return 0; // kein Fall, normaler Schritt
            int max = Math.max(o.maxFall, MAX_WATER_FALL);
            for (int n = 1; n <= max; n++) {
                int c = w.cell(tx, y - n, tz);
                if ((c & (UNLOADED | AVOID)) != 0) return 0;
                if ((c & (PASS | WATER)) == 0) return 0; // fester Block auf Fusshoehe: das waere kein Fall
                if (n <= o.maxFall ? floor(tx, y - n, tz) : swim(tx, y - n, tz)) return n;
            }
            return 0;
        }

        double parkour(int x, int y, int z, int dx, int dz, int gap) {
            if (!open(x, y + 2, z, false)) return INF;
            for (int i = 1; i <= gap; i++) {
                int mx = x + dx * i;
                int mz = z + dz * i;
                if (!open(mx, y, mz, false) || !open(mx, y + 1, mz, false) || !open(mx, y + 2, mz, false)) return INF;
                if (floor(mx, y, mz)) return INF; // keine Luecke: normal laufen
                // Faellt man doch hinein, muss man sicher unten ankommen
                boolean safe = false;
                for (int n = 1; n <= o.maxFall + 1; n++) {
                    int c = w.cell(mx, y - n, mz);
                    if ((c & (UNLOADED | AVOID)) != 0) return INF;
                    if ((c & STAND) != 0) {
                        safe = (c & AVOID_FLOOR) == 0;
                        break;
                    }
                    if ((c & (PASS | WATER)) == 0) return INF;
                }
                if (!safe) return INF;
            }
            int tx = x + dx * (gap + 1);
            int tz = z + dz * (gap + 1);
            if (!floor(tx, y, tz) || !open(tx, y, tz, false) || !open(tx, y + 1, tz, false)) return INF;
            if (!open(tx, y + 2, tz, false)) return INF;
            return WALK * (gap + 1) + JUMP + gap * 2.0;
        }

        double pillar(int x, int y, int z, boolean ownFloor, Step detail) {
            if (!o.allowPlace || !o.placeAvailable || !ownFloor) return INF;
            if (!has(x, y, z, PASS)) return INF;
            double cost = PILLAR + clear(x, y + 2, z, false, true, detail != null ? detail.breaks : null);
            return cost;
        }

        double down(int x, int y, int z, Step detail) {
            if (!o.allowBreak || !canBreak(x, y - 1, z)) return INF;
            if (!floor(x, y - 1, z)) return INF; // darunter muss wieder fester Boden kommen
            if (detail != null) detail.breaks.add(new int[] {x, y - 1, z});
            return breakCost(x, y - 1, z) + fallCost(1);
        }

        /** Kosten eines bekannten Schritts neu berechnen (pruefen, ob er noch geht); fuellt die Aktionen. */
        double evaluate(Step s, boolean ownFloor) {
            int dx = Integer.signum(s.tx - s.fx);
            int dz = Integer.signum(s.tz - s.fz);
            breakFactor = has(s.fx, s.fy, s.fz, WATER) ? 5.0 : 1.0;
            s.breaks.clear();
            s.place = null;
            return switch (s.move) {
                case WALK -> walk(s.fx, s.fy, s.fz, dx, dz, ownFloor, s);
                case DIAGONAL -> diagonal(s.fx, s.fy, s.fz, dx, dz);
                case ASCEND -> ascend(s.fx, s.fy, s.fz, dx, dz, s);
                case DESCEND -> descendBreak(s.fx, s.fy, s.fz, dx, dz, s);
                case FALL -> {
                    int n = fallDepth(s.fx, s.fy, s.fz, dx, dz);
                    yield n == s.fy - s.ty && (dx == 0 || dz == 0 || corners(s.fx, s.fy, s.fz, dx, dz))
                            ? WALK + fallCost(n) : INF;
                }
                case PARKOUR -> parkour(s.fx, s.fy, s.fz, dx, dz,
                        Math.max(Math.abs(s.tx - s.fx), Math.abs(s.tz - s.fz)) - 1);
                case PILLAR -> pillar(s.fx, s.fy, s.fz, ownFloor, s);
                case DOWN -> down(s.fx, s.fy, s.fz, s);
            };
        }
    }

    // ---------- Suche ----------

    private static final class Node implements Comparable<Node> {
        final int x, y, z;
        double g;
        double f;
        double h;
        Node parent;
        Move move;
        boolean closed;
        boolean ownFloor; // Boden wird durch den Weg selbst gesetzt (Bruecke/Saeule)

        Node(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public int compareTo(Node o) {
            int c = Double.compare(f, o.f);
            return c != 0 ? c : Double.compare(h, o.h);
        }
    }

    /** Eine laufende Suche; {@link #step} so lange aufrufen, bis sie fertig ist. */
    static final class Search {
        private static final double WEIGHT = 1.25;

        final int sx, sy, sz, gx, gy, gz;
        private final Mover mover;
        private final Options opt;
        private final PriorityQueue<Node> open = new PriorityQueue<>();
        private final HashMap<Long, Node> nodes = new HashMap<>();
        private Node best;
        private int bestAt;
        private int expanded;
        private boolean done;
        private boolean reachedGoal;
        private List<Step> result;

        Search(World w, Options opt, int sx, int sy, int sz, int gx, int gy, int gz, boolean startFloorPlaced) {
            this.mover = new Mover(w, opt);
            this.opt = opt;
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
            this.gx = gx;
            this.gy = gy;
            this.gz = gz;
            Node s = new Node(sx, sy, sz);
            s.h = heuristic(sx, sy, sz);
            s.f = s.h * WEIGHT;
            s.ownFloor = startFloorPlaced || mover.solidFloor(sx, sy, sz);
            nodes.put(key(sx, sy, sz), s);
            open.add(s);
            best = s;
        }

        boolean isGoal(int x, int y, int z) {
            return atGoal(x, y, z, gx, gy, gz, opt.mineGoal, opt.exactGoal);
        }

        double heuristic(int x, int y, int z) {
            int dx = Math.abs(x - gx);
            int dz = Math.abs(z - gz);
            int dy = gy - y;
            double horiz = Math.min(dx, dz) * DIAG + Math.abs(dx - dz) * WALK;
            double vert = dy > 0 ? dy * JUMP : -dy * 1.0;
            return horiz + vert;
        }

        boolean done() {
            return done;
        }

        boolean reachedGoal() {
            return reachedGoal;
        }

        List<Step> result() {
            return result;
        }

        int expanded() {
            return expanded;
        }

        /** So viele Knoten wurden untersucht, ohne dem Ziel naeher zu kommen. */
        int sinceBest() {
            return expanded - bestAt;
        }

        /** Bis zu budget Knoten untersuchen. true = fertig (dann result() abfragen; leer = kein Weg). */
        boolean step(int budget) {
            if (done) return true;
            int limit = expanded + budget;
            while (!open.isEmpty() && expanded < limit) {
                Node n = open.poll();
                if (n.closed) continue;
                n.closed = true;
                expanded++;
                if (isGoal(n.x, n.y, n.z)) {
                    finish(n, true);
                    return true;
                }
                if (n.h < best.h || (n.h == best.h && n.g < best.g)) {
                    if (n.h < best.h - 0.5) bestAt = expanded;
                    best = n;
                }
                if (expanded >= opt.maxNodes) break;
                final Node from = n;
                mover.neighbours(n.x, n.y, n.z, n.ownFloor, (x, y, z, m, cost) -> {
                    if (opt.blockedEdges.contains(edgeKey(from.x, from.y, from.z, x, y, z))) return;
                    double g = from.g + cost;
                    long k = key(x, y, z);
                    Node t = nodes.get(k);
                    if (t == null) {
                        t = new Node(x, y, z);
                        t.h = heuristic(x, y, z);
                        nodes.put(k, t);
                    } else if (t.closed || g >= t.g) {
                        return;
                    }
                    t.g = g;
                    t.f = g + t.h * WEIGHT;
                    t.parent = from;
                    t.move = m;
                    // Saeule und Bruecke setzen den Boden selbst; schwimmend gibt es keinen festen Boden
                    t.ownFloor = m == Move.PILLAR || (m == Move.WALK && !mover.floor(x, y, z))
                            || mover.solidFloor(x, y, z);
                    open.add(t);
                });
            }
            if (open.isEmpty() || expanded >= opt.maxNodes) {
                giveUp();
                return true;
            }
            return false;
        }

        /** Suche jetzt beenden (Zeit um): bester Teilweg oder leer. Gibt immer true zurueck. */
        boolean finishNow() {
            if (!done) giveUp();
            return true;
        }

        /** Ziel nicht erreicht: Teilweg zum naechstgelegenen Punkt (falls er wirklich naeher ist). */
        private void giveUp() {
            Node start = nodes.get(key(sx, sy, sz));
            if (best != start && best.h < start.h - WALK * 2) {
                finish(best, false);
            } else {
                result = new ArrayList<>();
                done = true;
            }
        }

        private void finish(Node end, boolean goal) {
            List<Step> steps = new ArrayList<>();
            for (Node n = end; n.parent != null; n = n.parent) {
                Node p = n.parent;
                Step s = new Step(p.x, p.y, p.z, n.x, n.y, n.z, n.move);
                mover.evaluate(s, p.ownFloor); // Aktionen (Abbauen/Bauen) eintragen
                steps.add(s);
            }
            Collections.reverse(steps);
            result = steps;
            reachedGoal = goal;
            done = true;
        }
    }
}
