package de.test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * Schnelle Block-Suche fuer die ESPs, die nur nach dem Blocktyp gehen (Block ESP, Erz-Wallhack, Bloecke im
 * ESP-Modul, Lager-ESP).
 * <ul>
 * <li>Chunk fuer Chunk, die naechsten zuerst.</li>
 * <li>Ganze 16x16x16-Abschnitte, in denen der Block gar nicht vorkommt, werden uebersprungen (Minecraft fuehrt
 * pro Abschnitt eine Liste der vorkommenden Bloecke, die "Palette") - deshalb kostet das fast nichts.</li>
 * <li>Neu geladene Chunks werden sofort durchsucht, einzelne Blockaenderungen (z.B. wenn ein Anti-Xray Erze
 * aufdeckt) sofort uebernommen - nicht erst beim naechsten kompletten Durchlauf.</li>
 * </ul>
 * Die Treffer werden pro Chunk gemerkt; die Ausgabe sind die naechsten {@code maxResults} um den Spieler.
 */
final class ChunkScan {
    /** Gesucht wird nach dem Blockzustand allein (ohne Nachbarn). */
    interface StateTest extends Predicate<BlockState> {
    }

    private static final long BUDGET_NS = 2_000_000L;     // so viel Zeit pro Tick fuer das Durchsuchen
    private static final int PER_CHUNK = 4096;            // mehr Treffer pro Chunk lohnen sich nicht
    private static final List<ChunkScan> ALL = new ArrayList<>();
    private static final Map<Long, Integer> STAMP = new HashMap<>(); // Chunk neu vom Server gekommen
    private static int stampCounter;
    private static boolean blockHookSeen;   // kommen Blockaenderungen ueber den Mixin an?

    private static final class Entry {
        final int cx;
        final int cz;
        LevelChunk chunk;
        int stamp;
        int scannedAt;
        long[] hits = new long[8];   // Positionen gepackt (spart Speicher bei sehr haeufigen Bloecken)
        int size;

        Entry(int cx, int cz) {
            this.cx = cx;
            this.cz = cz;
        }

        void add(long v) {
            if (size == hits.length) hits = Arrays.copyOf(hits, size * 2);
            hits[size++] = v;
        }

        boolean remove(long v) {
            for (int i = 0; i < size; i++) {
                if (hits[i] == v) {
                    hits[i] = hits[--size];
                    return true;
                }
            }
            return false;
        }

        void clear() {
            size = 0;
            if (hits.length > 512) hits = new long[8];
        }

        long sum() {
            long h = size;
            for (int i = 0; i < size; i++) h = h * 31 + hits[i];
            return h;
        }
    }

    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static int px(long p) {
        return (int) (p >> 38);
    }

    static int py(long p) {
        return (int) (p << 52 >> 52);
    }

    static int pz(long p) {
        return (int) (p << 26 >> 38);
    }

    private final int maxResults;
    private final Map<Long, Entry> entries = new HashMap<>();
    private StateTest test;
    private Object testKey;
    private ClientLevel level;
    private int now;
    private boolean dirty = true;
    private BlockPos lastCenter;
    private long lastParams = Long.MIN_VALUE;
    private long[] todo = new long[64];
    private final ArrayList<Entry> near = new ArrayList<>();
    private long[] nearD = new long[64];
    private int loadedCount;    // geladene Chunks im Umkreis
    private int scannedCount;   // davon schon durchsucht
    private int totalHits;      // Treffer im Umkreis (vor dem Kuerzen auf maxResults)
    private int statusTick = -1000;
    private String statusText = "";

    ChunkScan(int maxResults) {
        this.maxResults = maxResults;
        ALL.add(this);
    }

    static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    // =====================================================================================
    // Von den Mixins: Chunk neu geladen / ein Block hat sich geaendert
    // =====================================================================================

    /** Der Server hat den ganzen Chunk (neu) geschickt. */
    static void chunkChanged(int cx, int cz) {
        if (STAMP.size() > 50_000) STAMP.clear(); // nur ein Merkzettel - leeren heisst nur: einmal neu suchen
        STAMP.put(key(cx, cz), ++stampCounter);
    }

    /** Ein einzelner Block hat sich geaendert: in allen Suchen gleich nachtragen oder entfernen. */
    static void blockChanged(ClientLevel level, BlockPos pos) {
        blockHookSeen = true;
        if (ALL.isEmpty()) return;
        long k = key(pos.getX() >> 4, pos.getZ() >> 4);
        BlockState state = null;
        for (ChunkScan s : ALL) {
            if (s.level != level || s.test == null) continue;
            Entry e = s.entries.get(k);
            if (e == null) continue;
            if (state == null) state = level.getBlockState(pos);
            long v = pack(pos.getX(), pos.getY(), pos.getZ());
            boolean had = e.remove(v);
            boolean has = s.test.test(state);
            if (has) e.add(v);
            if (had || has) s.dirty = true;
        }
    }

    // =====================================================================================
    // Suchen
    // =====================================================================================

    /** Aus: alles vergessen. */
    void clear(List<BlockPos> out) {
        entries.clear();
        test = null;
        testKey = null;
        level = null;
        lastCenter = null;
        dirty = true;
        out.clear();
    }

    /** Neu anfangen (z.B. nachdem die gesuchten Bloecke geaendert wurden). */
    void restart() {
        entries.clear();
        dirty = true;
    }

    /**
     * Jeden Tick. Waagerecht {@code radius} Bloecke; senkrecht {@code down}/{@code up} Bloecke um die Mitte;
     * {@code sphere}: nur innerhalb der Kugel mit {@code radius}. {@code testKey} aendert sich, wenn sich die
     * Auswahl aendert (dann wird alles neu durchsucht).
     */
    void tick(ClientLevel lvl, BlockPos center, int radius, int down, int up, boolean sphere, StateTest t,
            Object key, List<BlockPos> out) {
        now++;
        if (lvl != level || testKey == null || !testKey.equals(key)) {
            entries.clear();
            level = lvl;
            testKey = key;
            dirty = true;
        }
        test = t;
        long params = ((long) radius << 42) ^ ((long) down << 22) ^ ((long) up << 2) ^ (sphere ? 1 : 0);
        if (params != lastParams) {
            lastParams = params;
            dirty = true; // z.B. Reichweite geaendert: Ergebnis neu zusammenstellen
        }
        int ccx = center.getX() >> 4;
        int ccz = center.getZ() >> 4;
        int cr = (Math.max(0, radius) + 15) >> 4;
        // Sicherheitsnetz: jeder Chunk ab und zu komplett neu - die nahen oft (dort deckt z.B. ein Anti-Xray
        // Erze auf). Kommen Blockaenderungen ueber den Mixin an, reicht seltener.
        int nearRescan = blockHookSeen ? 40 : 10;
        int midRescan = blockHookSeen ? 100 : 40;
        int farRescan = blockHookSeen ? 400 : 200;

        // 1. Welche Chunks muessen (neu) durchsucht werden? Nicht geladene vergessen.
        int n = 0;
        int newCount = 0;
        loadedCount = 0;
        for (int dx = -cr; dx <= cr; dx++) {
            for (int dz = -cr; dz <= cr; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > (cr + 1) * (cr + 1)) continue;
                int x = ccx + dx;
                int z = ccz + dz;
                long k = key(x, z);
                LevelChunk ch = lvl.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                Entry e = entries.get(k);
                if (ch == null) {
                    if (e != null) {
                        entries.remove(k);
                        dirty = true;
                    }
                    continue;
                }
                loadedCount++;
                int st = STAMP.getOrDefault(k, 0);
                int rescan = d2 <= 2 ? nearRescan : d2 <= 9 ? midRescan : farRescan;
                int prio;
                if (e == null || e.chunk != ch || e.stamp != st) {
                    prio = 0;                                                        // neu: sofort
                    newCount++;
                } else if (now - e.scannedAt > rescan) {
                    prio = 1;                                                        // alt: wenn Zeit ist
                } else {
                    continue;
                }
                if (n == todo.length) todo = Arrays.copyOf(todo, n * 2);
                // sortierbar: erst Prioritaet, dann Abstand, dann Position
                todo[n++] = ((long) prio << 62) | ((long) d2 << 40) | (((long) (dx + 512) & 0xFFFFF) << 20)
                        | ((long) (dz + 512) & 0xFFFFF);
            }
        }
        // weit entfernte Eintraege wegwerfen
        if (entries.size() > (2 * cr + 5) * (2 * cr + 5)) {
            for (Iterator<Entry> it = entries.values().iterator(); it.hasNext(); ) {
                Entry e = it.next();
                if (Math.abs(e.cx - ccx) > cr + 2 || Math.abs(e.cz - ccz) > cr + 2) {
                    it.remove();
                    dirty = true;
                }
            }
        }

        // 2. Durchsuchen: die naechsten neuen zuerst, mit Zeitlimit (mindestens einer pro Tick)
        int newDone = 0;
        if (n > 0) {
            Arrays.sort(todo, 0, n);
            long deadline = System.nanoTime() + BUDGET_NS;
            for (int i = 0; i < n; i++) {
                if (i > 0 && System.nanoTime() > deadline) break;
                if ((todo[i] >>> 62) == 0) newDone++;
                int x = ccx + (int) ((todo[i] >>> 20) & 0xFFFFF) - 512;
                int z = ccz + (int) (todo[i] & 0xFFFFF) - 512;
                LevelChunk ch = lvl.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                if (ch == null) continue;
                long k = key(x, z);
                Entry e = entries.get(k);
                if (e == null) {
                    e = new Entry(x, z);
                    entries.put(k, e);
                }
                e.chunk = ch;
                e.stamp = STAMP.getOrDefault(k, 0);
                e.scannedAt = now;
                long before = e.sum();
                scanChunk(ch, e, center.getY());
                if (e.sum() != before) dirty = true;
            }
        }

        scannedCount = loadedCount - (newCount - newDone);

        // 3. Ergebnis: die naechsten Treffer (nur wenn sich etwas geaendert hat oder er sich bewegt hat)
        if (dirty || lastCenter == null || !lastCenter.equals(center)) {
            dirty = false;
            lastCenter = center.immutable();
            collect(center, radius, down, up, sphere, out);
        }
    }

    private void scanChunk(LevelChunk ch, Entry e, int centerY) {
        e.clear();
        LevelChunkSection[] secs = ch.getSections();
        int n = secs.length;
        if (n == 0) return;
        // welche Abschnitte kommen ueberhaupt in Frage? (Palette: kommt der Block darin vor?)
        boolean[] rel = new boolean[n];
        boolean any = false;
        for (int i = 0; i < n; i++) {
            LevelChunkSection s = secs[i];
            rel[i] = s != null && !s.hasOnlyAir() && s.maybeHas(test);
            any |= rel[i];
        }
        if (!any) return;
        int minY = ch.getSectionYFromSectionIndex(0) << 4;
        int maxY = minY + n * 16 - 1;
        int cy = Math.max(minY, Math.min(maxY, centerY));
        // Schicht fuer Schicht, von der Hoehe des Spielers aus nach oben und unten: bei sehr haeufigen Bloecken
        // (mehr als PER_CHUNK) bleiben so die naechsten uebrig
        for (int k = 0; ; k++) {
            int up = cy + k;
            int down = cy - k;
            boolean inUp = up <= maxY;
            boolean inDown = k > 0 && down >= minY;
            if (!inUp && !inDown) break;
            if (inUp && !scanLayer(secs, rel, minY, up, e)) return;
            if (inDown && !scanLayer(secs, rel, minY, down, e)) return;
        }
    }

    /** Eine Schicht (16x16) einer Hoehe; false = genug Treffer fuer diesen Chunk. */
    private boolean scanLayer(LevelChunkSection[] secs, boolean[] rel, int minY, int y, Entry e) {
        int i = (y - minY) >> 4;
        if (!rel[i]) return true;
        LevelChunkSection s = secs[i];
        int ly = (y - minY) & 15;
        int bx = e.cx << 4;
        int bz = e.cz << 4;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                if (test.test(s.getBlockState(x, ly, z))) {
                    e.add(pack(bx + x, y, bz + z));
                    if (e.size >= PER_CHUNK) return false;
                }
            }
        }
        return true;
    }

    /** Die naechsten maxResults Treffer innerhalb des Bereichs, sortiert (naechste zuerst). */
    private void collect(BlockPos c, int radius, int down, int up, boolean sphere, List<BlockPos> out) {
        int cxw = c.getX();
        int cyw = c.getY();
        int czw = c.getZ();
        long r2 = (long) radius * radius;
        // Chunks nach ihrem kleinsten Abstand sortieren - weiter weg muss oft gar nicht mehr geschaut werden
        near.clear();
        if (nearD.length < entries.size()) nearD = new long[Math.max(64, entries.size() * 2)];
        for (Entry e : entries.values()) {
            if (e.size == 0) continue;
            long dx = Math.max(0, Math.max((e.cx << 4) - cxw, cxw - ((e.cx << 4) + 15)));
            long dz = Math.max(0, Math.max((e.cz << 4) - czw, czw - ((e.cz << 4) + 15)));
            long d2 = dx * dx + dz * dz;
            if (d2 > r2) continue;
            near.add(e);
        }
        near.sort((a, b) -> Long.compare(minD2(a, cxw, czw), minD2(b, cxw, czw)));
        totalHits = 0;
        for (Entry e : near) totalHits += e.size;
        PriorityQueue<long[]> best = new PriorityQueue<>((a, b) -> Long.compare(b[0], a[0])); // groesster oben
        for (Entry e : near) {
            if (best.size() >= maxResults && minD2(e, cxw, czw) > best.peek()[0]) break;
            for (int i = 0; i < e.size; i++) {
                long p = e.hits[i];
                long dx = px(p) - cxw;
                long dy = py(p) - cyw;
                long dz = pz(p) - czw;
                if (dy < -down || dy > up) continue;
                long h2 = dx * dx + dz * dz;
                long d2 = h2 + dy * dy;
                if (sphere ? d2 > r2 : h2 > r2) continue;
                if (best.size() < maxResults) {
                    best.add(new long[] {d2, p});
                } else if (d2 < best.peek()[0]) {
                    best.poll();
                    best.add(new long[] {d2, p});
                }
            }
        }
        long[][] arr = best.toArray(new long[0][]);
        Arrays.sort(arr, (a, b) -> Long.compare(a[0], b[0]));
        out.clear();
        for (long[] a : arr) out.add(new BlockPos(px(a[1]), py(a[1]), pz(a[1])));
    }

    private static long minD2(Entry e, int cx, int cz) {
        long dx = Math.max(0, Math.max((e.cx << 4) - cx, cx - ((e.cx << 4) + 15)));
        long dz = Math.max(0, Math.max((e.cz << 4) - cz, cz - ((e.cz << 4) + 15)));
        return dx * dx + dz * dz;
    }

    // =====================================================================================
    // Anzeige im Menue
    // =====================================================================================

    /**
     * Wie weit die Suche ist - und ob der Server Erze versteckt (Anti-Xray). Wird pro Bild abgefragt, rechnet
     * aber nur alle 10 Ticks neu.
     */
    String status(List<BlockPos> found) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lvl = mc.level;
        if (lvl == null || level == null) return Lang.t("Sucht, sobald es an ist.");
        int tick = now;
        if (Math.abs(tick - statusTick) < 10) return statusText;
        statusTick = tick;
        String s = Lang.t("Durchsucht: ") + scannedCount + Lang.t(" von ") + loadedCount
                + Lang.t(" geladenen Chunks  ·  gefunden: ") + found.size();
        String hint = antiXray(lvl, found);
        statusText = hint == null ? s : s + "  ·  " + hint;
        return statusText;
    }

    private String antiXray(ClientLevel lvl, List<BlockPos> found) {
        int ores = 0;
        int enclosed = 0;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (BlockPos p : found) {
            Block b = lvl.getBlockState(p).getBlock();
            if (!isOre(b)) continue;
            ores++;
            boolean closed = true;
            for (Direction d : Direction.values()) {
                m.setWithOffset(p, d);
                if (!lvl.getBlockState(m).canOcclude()) {
                    closed = false;
                    break;
                }
            }
            if (closed) enclosed++;
        }
        if (ores >= 10 && enclosed == 0) {
            return Lang.t("Alle gefundenen Erze liegen frei – der Server versteckt wohl die übrigen (Anti-Xray). "
                    + "Die schickt er erst, wenn du direkt daneben bist; früher kann sie kein Client anzeigen.");
        }
        if (ores >= 10 && scannedCount >= 9 && totalHits > scannedCount * 40) {
            return Lang.t("Ungewöhnlich viele Erze – das sind wohl falsche Erze vom Anti-Xray des Servers.");
        }
        return null;
    }

    private static boolean isOre(Block b) {
        if (b == Blocks.ANCIENT_DEBRIS) return true;
        return BuiltInRegistries.BLOCK.getKey(b).getPath().endsWith("_ore");
    }
}

/**
 * Block-Suche, die auf mehrere Ticks verteilt wird: Statt alle paar Ticks den ganzen Bereich auf einmal
 * zu pruefen (bei grossem Radius ein spuerbarer Ruckler), wird jeden Tick nur eine Scheibe geprueft.
 * Nach einem kompletten Durchlauf werden die Treffer (die naechsten zuerst) in die Ergebnisliste
 * geschrieben und der naechste Durchlauf beginnt an der aktuellen Spielerposition.
 */
final class BlockScan {
    @FunctionalInterface
    interface Test {
        boolean ok(BlockPos p);
    }

    /** Hoechstens so viel Zeit pro Tick und Suche - grosser Radius dauert dann laenger, ruckelt aber nicht. */
    private static final long BUDGET_NS = 1_500_000L;

    private final int maxResults;
    private final List<BlockPos> building = new ArrayList<>();
    private BlockPos center;
    private int r;
    private int yDown;
    private int yUp;
    private boolean sphere;
    private int x;        // naechste Zeile: Scheibe x ...
    private int dy;       // ... und Hoehe dy (relativ zur Mitte)
    private long maxD2;   // nach dem Kuerzen: weiter weg lohnt sich nicht mehr
    private long passKey; // Einstellungen des laufenden Durchlaufs

    BlockScan(int maxResults) {
        this.maxResults = maxResults;
    }

    /** Kugel mit Radius r um c. */
    void stepSphere(BlockPos c, int radius, int ticksPerPass, Test test, List<BlockPos> out) {
        step(c, radius, radius, radius, true, ticksPerPass, test, out);
    }

    /** Quader: waagerecht +-radius, senkrecht von -down bis +up. */
    void stepBox(BlockPos c, int radius, int down, int up, int ticksPerPass, Test test, List<BlockPos> out) {
        step(c, radius, down, up, false, ticksPerPass, test, out);
    }

    /** Modul aus: Ergebnisse leeren, beim Wiedereinschalten von vorn beginnen. */
    void clear(List<BlockPos> out) {
        center = null;
        building.clear();
        out.clear();
    }

    /** Neu anfangen (z.B. nachdem die gesuchten Bloecke geaendert wurden). */
    void restart() {
        center = null;
        building.clear();
    }

    private void step(BlockPos c, int radius, int down, int up, boolean sph, int ticksPerPass, Test test,
            List<BlockPos> out) {
        long key = ((long) radius << 40) ^ ((long) down << 24) ^ ((long) up << 8) ^ (sph ? 1L : 0L);
        if (center == null || key != passKey) begin(c, radius, down, up, sph, key);
        // Zeilen (eine Zeile = alle z bei festem x und dy) gleichmaessig auf ticksPerPass Ticks verteilen
        long rows = (long) (2 * r + 1) * (yDown + yUp + 1);
        long perTick = Math.max(1L, (rows + Math.max(1, ticksPerPass) - 1) / Math.max(1, ticksPerPass));
        long deadline = System.nanoTime() + BUDGET_NS;
        long r2 = (long) r * r;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int cap = maxResults * 4;
        for (long i = 0; i < perTick && x <= r; i++) {
            if (i > 0 && (i & 7) == 0 && System.nanoTime() > deadline) break;
            long xy = (long) x * x + (long) dy * dy;
            if (!(sphere && xy > r2) && xy <= maxD2) {
                for (int dz = -r; dz <= r; dz++) {
                    long d2 = xy + (long) dz * dz;
                    if (sphere && d2 > r2 || d2 > maxD2) continue;
                    m.set(center.getX() + x, center.getY() + dy, center.getZ() + dz);
                    if (test.ok(m)) {
                        building.add(m.immutable());
                        if (building.size() >= cap) trim();
                    }
                }
            }
            if (++dy > yUp) {
                dy = -yDown;
                x++;
            }
        }
        if (x > r) {
            BlockPos cc = center;
            building.sort((a, b) -> Double.compare(a.distSqr(cc), b.distSqr(cc)));
            out.clear();
            out.addAll(building.size() > maxResults ? building.subList(0, maxResults) : building);
            begin(c, radius, down, up, sph, key);
        }
    }

    /** Zu viele Treffer: nur die naechsten behalten; weiter entfernte muessen gar nicht mehr geprueft werden. */
    private void trim() {
        BlockPos cc = center;
        building.sort((a, b) -> Double.compare(a.distSqr(cc), b.distSqr(cc)));
        while (building.size() > maxResults) building.remove(building.size() - 1);
        BlockPos last = building.get(building.size() - 1);
        maxD2 = (long) last.distSqr(cc);
    }

    private void begin(BlockPos c, int radius, int down, int up, boolean sph, long key) {
        center = c.immutable();
        r = Math.max(0, radius);
        yDown = Math.max(0, down);
        yUp = Math.max(0, up);
        sphere = sph;
        passKey = key;
        x = -r;
        dy = -yDown;
        maxD2 = Long.MAX_VALUE;
        building.clear();
    }
}
