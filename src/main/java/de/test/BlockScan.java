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

    private final int maxResults;
    private final List<BlockPos> building = new ArrayList<>();
    private BlockPos center;
    private int r;
    private int yDown;
    private int yUp;
    private boolean sphere;
    private int x;        // naechste Scheibe (relativ zur Mitte)
    private int passKey;  // Einstellungen des laufenden Durchlaufs

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
        int key = radius * 31 * 31 + down * 31 + up + (sph ? 1 : 0);
        if (center == null || key != passKey) begin(c, radius, down, up, sph, key);
        int slices = 2 * r + 1;
        int per = Math.max(1, (slices + Math.max(1, ticksPerPass) - 1) / Math.max(1, ticksPerPass));
        int r2 = r * r;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int cap = maxResults * 4; // etwas mehr sammeln, danach die naechsten behalten
        for (int i = 0; i < per && x <= r; i++, x++) {
            if (building.size() >= cap) continue;
            for (int dy = -yDown; dy <= yUp; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (sphere && x * x + dy * dy + dz * dz > r2) continue;
                    m.set(center.getX() + x, center.getY() + dy, center.getZ() + dz);
                    if (test.ok(m)) {
                        building.add(m.immutable());
                        if (building.size() >= cap) break;
                    }
                }
                if (building.size() >= cap) break;
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

    private void begin(BlockPos c, int radius, int down, int up, boolean sph, int key) {
        center = c.immutable();
        r = Math.max(0, radius);
        yDown = Math.max(0, down);
        yUp = Math.max(0, up);
        sphere = sph;
        passKey = key;
        x = -r;
        building.clear();
    }
}
