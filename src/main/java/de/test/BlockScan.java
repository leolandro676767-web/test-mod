package de.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;

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
