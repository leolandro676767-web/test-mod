package de.test;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Items einsammeln - fuer alle Bots, die etwas abbauen (Bloecke suchen, Bereich abbauen, Tunnel Miner,
 * Debris Miner).
 * <p>
 * Eingesammelt werden nur "eigene" Items: solche, die kurz nach einem eigenen Abbau direkt an diesem Block
 * auftauchen. Nicht, was andere Spieler fallen lassen, und nicht, was ein Bot selbst weggeworfen hat (z.B.
 * Gestein) - sonst hebt er es gleich wieder auf.
 */
public final class Pickup {
    private Pickup() {
    }

    /** Was ein Bot einsammeln moechte. */
    static final class Want {
        int radius = 8;            // Bloecke um den Spieler (waagerecht)
        int minAge = 10;           // so viele Ticks muss das Item schon liegen (erst landen lassen)
        int dirX;                  // != 0 / dirZ != 0: nur, was neben oder hinter ihm liegt
        int dirZ;                  //   (was voraus liegt, nimmt er beim Weitergehen sowieso mit)
        double minY = -1.0e9;      // nur Items zwischen diesen Hoehen
        double maxY = 1.0e9;
        BiPredicate<ItemStack, Block> ok = (st, source) -> true; // source = Block, aus dem es kam
    }

    private record Break(double x, double y, double z, int tick, Block block) {
    }

    private record Throw(Item item, double x, double y, double z, int tick) {
    }

    private static final class Seen {
        final int first;
        final Block source;        // null = kein eigenes Item
        int last;

        Seen(int first, Block source) {
            this.first = first;
            this.source = source;
            this.last = first;
        }
    }

    private static final int BREAK_TICKS = 60;             // so lange nach dem Abbau zaehlt ein neues Item
    private static final double BREAK_DIST2 = 2.8 * 2.8;   // 3x3-Spitzhacke: auch die Ecken drumherum
    private static final int THROW_TICKS = 40;
    private static final double THROW_DIST2 = 7.0 * 7.0;
    private static final double SCAN = 24.0;

    private static int now;
    private static int lastBreak = -100000;
    private static final ArrayDeque<Break> breaks = new ArrayDeque<>();
    private static final ArrayDeque<Throw> throwsDone = new ArrayDeque<>();
    private static final Map<Integer, Seen> seen = new HashMap<>();
    private static final Set<Integer> ignored = new HashSet<>();

    // beim Einsammeln
    private static int itemId = -1;
    private static BlockPos goal;
    private static int itemTicks;
    private static int waitTicks;
    private static int arrivedTicks;
    private static int sessionTicks;
    private static boolean walking;        // der Walker laeuft gerade fuer uns
    static int collected;                  // fuer die Anzeige / Tests

    // =====================================================================================
    // Merken: Abbau, Wegwerfen, neue Items
    // =====================================================================================

    /** Vom Mixin (MultiPlayerGameMode.destroyBlock): man hat gerade selbst einen Block abgebaut. */
    public static void noteBreak(BlockPos pos, Block block) {
        breaks.addLast(new Break(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, now, block));
        lastBreak = now;
        while (breaks.size() > 512) breaks.removeFirst();
    }

    /** Man wirft gerade etwas weg: die Items davon spaeter nicht wieder einsammeln. */
    static void noteThrow(LocalPlayer p, Item item) {
        if (p == null || item == null) return;
        throwsDone.addLast(new Throw(item, p.getX(), p.getY() + 1.5, p.getZ(), now));
        while (throwsDone.size() > 64) throwsDone.removeFirst();
    }

    /** Jeden Tick (vor den Bots): neue Items in der Naehe einordnen. */
    static void tick(Minecraft mc) {
        now++;
        LocalPlayer p = mc.player;
        Level level = mc.level;
        if (p == null || level == null) {
            clearAll();
            return;
        }
        while (!breaks.isEmpty() && now - breaks.peekFirst().tick() > BREAK_TICKS) breaks.removeFirst();
        while (!throwsDone.isEmpty() && now - throwsDone.peekFirst().tick() > THROW_TICKS) throwsDone.removeFirst();
        if ((now & 255) == 0) forgetOld();
        boolean bot = TestClient.areaActive || DebrisMiner.isRunning() || TunnelMiner.isRunning();
        if (!bot && now - lastBreak > BREAK_TICKS) return;
        for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(SCAN, SCAN, SCAN))) {
            if (!(e instanceof ItemEntity ie)) continue;
            Seen s = seen.get(ie.getId());
            if (s == null) {
                seen.put(ie.getId(), classify(ie));
            } else {
                s.last = now;
            }
        }
    }

    /** Neues Item: eigenes (kam aus einem eigenen Abbau), weggeworfenes oder fremdes? */
    private static Seen classify(ItemEntity ie) {
        double x = ie.getX();
        double y = ie.getY();
        double z = ie.getZ();
        Item item = ie.getItem().getItem();
        for (Throw t : throwsDone) {
            if (t.item() == item && d2(x, y, z, t.x(), t.y(), t.z()) <= THROW_DIST2) return new Seen(now, null);
        }
        Break best = null;
        double bd = BREAK_DIST2;
        for (Break b : breaks) {
            double d = d2(x, y, z, b.x(), b.y(), b.z());
            if (d <= bd) {
                bd = d;
                best = b;
            }
        }
        return new Seen(now, best == null ? null : best.block());
    }

    private static double d2(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static void forgetOld() {
        for (Iterator<Map.Entry<Integer, Seen>> it = seen.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Seen> en = it.next();
            if (now - en.getValue().last > 1200) {
                ignored.remove(en.getKey());
                it.remove();
            }
        }
    }

    private static void clearAll() {
        breaks.clear();
        throwsDone.clear();
        seen.clear();
        ignored.clear();
        itemId = -1;
        goal = null;
        walking = false;
    }

    /** Ein eigenes Item (aus einem eigenen Abbau), das nicht schon aufgegeben wurde? */
    static boolean isOwn(ItemEntity ie) {
        Seen s = seen.get(ie.getId());
        return s != null && s.source != null && !ignored.contains(ie.getId());
    }

    /** Dieses Item nicht mehr holen (z.B. unerreichbar). */
    static void ignore(ItemEntity ie) {
        ignored.add(ie.getId());
    }

    /** Passt das Item noch ins Inventar (freier Platz oder ein Stapel, der nicht voll ist)? */
    static boolean canTake(LocalPlayer p, ItemStack st) {
        var inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) return true;
            if (s.is(st.getItem()) && s.getCount() < s.getMaxStackSize()) return true;
        }
        return false;
    }

    // =====================================================================================
    // Einsammeln
    // =====================================================================================

    /** Liegt etwas zum Einsammeln herum? */
    static boolean has(Minecraft mc, LocalPlayer p, Want w) {
        return next(mc, p, w) != null;
    }

    /** Vor dem ersten collect(): neu anfangen. */
    static void begin() {
        sessionTicks = 0;
        itemId = -1;
        goal = null;
        walking = false;
    }

    /** Aufhoeren (z.B. Bot gestoppt): laeuft der Walker fuer uns, anhalten. */
    static void stop(Minecraft mc) {
        if (walking) Walker.stop(mc);
        walking = false;
        goal = null;
        itemId = -1;
    }

    /** Ein Tick Einsammeln. true = noch dabei; false = nichts (mehr) da, der Bot macht weiter. */
    static boolean collect(Minecraft mc, Level level, LocalPlayer p, Want w) {
        if (++sessionTicks > 20 * 60) {
            // eine Minute ohne Ende: alles, was noch da liegt, aufgeben
            for (int i = 0; i < 64; i++) {
                ItemEntity ie = next(mc, p, w);
                if (ie == null) break;
                ignored.add(ie.getId());
            }
            stop(mc);
            return false;
        }
        ItemEntity ie = next(mc, p, w);
        if (ie == null) {
            stop(mc);
            return false;
        }
        if (ie.getId() != itemId) {
            itemId = ie.getId();
            itemTicks = 0;
            waitTicks = 0;
            arrivedTicks = 0;
            goal = null;
        }
        if (++itemTicks > 20 * 20) {
            skip(mc, ie);
            return true;
        }
        if (inPickupRange(p, ie)) {
            // gleich hebt Minecraft es auf - stehen bleiben
            if (walking) {
                Walker.stop(mc);
                walking = false;
            }
            if (++waitTicks > 40) skip(mc, ie);
            return true;
        }
        waitTicks = 0;
        BlockPos spot = standSpot(level, p, ie);
        if (!walking || !spot.equals(goal)) {
            if (walking && Walker.inCriticalMove(p)) {
                tickWalker(mc, level, p, ie); // nicht mitten im Sprung oder beim Hochbauen umplanen
                return true;
            }
            if (!walking && spot.equals(goal)) {
                // angekommen, aber nicht aufgehoben (liegt doch zu weit weg?)
                if (++arrivedTicks > 40) skip(mc, ie);
                return true;
            }
            Walker.startDigExact(spot.getX(), spot.getY(), spot.getZ());
            goal = spot;
            walking = true;
        }
        tickWalker(mc, level, p, ie);
        return true;
    }

    private static void tickWalker(Minecraft mc, Level level, LocalPlayer p, ItemEntity ie) {
        String r;
        try {
            r = Walker.tick(mc, level, p);
        } catch (RuntimeException | LinkageError e) {
            Safe.report("Einsammeln", e);
            r = "Fehler";
        }
        if (r == null) return;
        Walker.stop(mc);
        walking = false;
        if (!Walker.ARRIVED.equals(r)) skip(mc, ie); // kein Weg dorthin
    }

    private static void skip(Minecraft mc, ItemEntity ie) {
        ignored.add(ie.getId());
        itemId = -1;
        goal = null;
        if (walking) {
            Walker.stop(mc);
            walking = false;
        }
    }

    /** Naechstes eigenes Item, das noch geholt werden soll, sonst null. */
    private static ItemEntity next(Minecraft mc, LocalPlayer p, Want w) {
        if (mc.level == null) return null;
        double r = Math.max(2, w.radius);
        ItemEntity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(r, Math.max(3.0, r / 2.0), r))) {
            if (!(e instanceof ItemEntity ie) || !ie.isAlive()) continue;
            Seen s = seen.get(ie.getId());
            if (s == null || s.source == null || ignored.contains(ie.getId())) continue;
            if (now - s.first < w.minAge) continue;
            if (ie.getY() < w.minY || ie.getY() > w.maxY) continue;
            double dx = ie.getX() - p.getX();
            double dz = ie.getZ() - p.getZ();
            if (dx * dx + dz * dz > r * r) continue;
            if ((w.dirX != 0 || w.dirZ != 0) && dx * w.dirX + dz * w.dirZ > 0.5) continue;
            ItemStack st = ie.getItem();
            if (st.isEmpty() || !canTake(p, st) || !w.ok.test(st, s.source)) continue;
            double dy = ie.getY() - p.getY();
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bd) {
                bd = d;
                best = ie;
            }
        }
        return best;
    }

    /** Hebt Minecraft das Item schon auf, wenn er hier stehen bleibt? */
    private static boolean inPickupRange(LocalPlayer p, ItemEntity ie) {
        return p.getBoundingBox().inflate(1.0, 0.5, 1.0).intersects(ie.getBoundingBox());
    }

    /**
     * Wo er stehen muss, um das Item aufzuheben: im selben Feld, bis zu zwei Bloecke darunter (Item auf einer
     * Kante) oder direkt daneben. Sonst das Feld des Items selbst (der Walker graebt sich hin).
     */
    private static BlockPos standSpot(Level level, LocalPlayer p, ItemEntity ie) {
        BlockPos ip = BlockPos.containing(ie.getX(), ie.getY() + 0.1, ie.getZ());
        int[][] cols = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        BlockPos best = null;
        double bd = Double.MAX_VALUE;
        for (int dy = 0; dy >= -2; dy--) {
            for (int[] c : cols) {
                BlockPos s = ip.offset(c[0], dy, c[1]);
                double rel = ie.getY() - s.getY();
                if (rel < -0.6 || rel > 2.2) continue; // so hoch reicht das Aufheben nicht
                double ox = ie.getX() - (s.getX() + 0.5);
                double oz = ie.getZ() - (s.getZ() + 0.5);
                if (Math.abs(ox) > 1.35 || Math.abs(oz) > 1.35) continue;
                if (!standable(level, s)) continue;
                double dx = s.getX() + 0.5 - p.getX();
                double dz = s.getZ() + 0.5 - p.getZ();
                double dyy = s.getY() - p.getY();
                double d = dx * dx + dyy * dyy + dz * dz + (c[0] != 0 || c[1] != 0 ? 1.5 : 0.0) - dy * 1.0;
                if (d < bd) {
                    bd = d;
                    best = s;
                }
            }
        }
        return best != null ? best : ip;
    }

    private static boolean standable(Level level, BlockPos s) {
        return passable(level, s) && passable(level, s.above()) && !passable(level, s.below())
                && level.getFluidState(s).isEmpty() && level.getFluidState(s.above()).isEmpty();
    }

    private static boolean passable(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
