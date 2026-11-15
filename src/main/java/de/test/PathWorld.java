package de.test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Liest die Bloecke der Welt fuer die Wegsuche und merkt sich jeden Block fuer die Dauer einer Suche.
 * Uebersetzt Minecraft-Bloecke in die einfachen Eigenschaften von {@link Pathfinder}.
 */
final class PathWorld implements Pathfinder.World {
    /** Dauert das Abbauen laenger (in Ticks), gilt der Block als nicht abbaubar (z.B. Obsidian ohne Diamant). */
    private static final double MAX_BREAK_TICKS = 400.0;

    private static Set<Block> danger;
    private static Set<Block> hotFloor;

    private final HashMap<Long, Integer> cache = new HashMap<>();
    /** Chunks, die bei dieser Suche nicht geladen waren: bleiben fuer die ganze Suche "ungeladen". */
    private final HashSet<Long> unloadedChunks = new HashSet<>();
    private final IdentityHashMap<BlockState, Double> times = new IdentityHashMap<>();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private Level level;
    private LocalPlayer player;
    private int refY;
    /** Die Suche ist an ungeladene Chunks gestossen (Ziel evtl. erst spaeter erreichbar). */
    boolean sawUnloaded;

    PathWorld(Level level, LocalPlayer player) {
        reset(level, player);
    }

    /** Fuer eine neue Suche bzw. einen neuen Tick: alles vergessen. */
    void reset(Level level, LocalPlayer player) {
        this.level = level;
        this.player = player;
        this.refY = player != null ? (int) Math.floor(player.getY()) : 0;
        cache.clear();
        unloadedChunks.clear();
        times.clear();
        sawUnloaded = false;
    }

    /** Hoehe, um die herum gesucht wird (Void-Luft weit darueber/darunter ist der Weltrand, kein Chunk). */
    void setRefY(int y) {
        refY = y;
    }

    private static void initSets() {
        if (danger != null) return;
        danger = Set.of(Blocks.FIRE, Blocks.SOUL_FIRE, Blocks.CACTUS, Blocks.SWEET_BERRY_BUSH, Blocks.COBWEB,
                Blocks.POWDER_SNOW, Blocks.WITHER_ROSE, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE,
                Blocks.LAVA_CAULDRON, Blocks.BUBBLE_COLUMN, Blocks.NETHER_PORTAL, Blocks.END_PORTAL,
                Blocks.END_GATEWAY, Blocks.TRIPWIRE);
        hotFloor = Set.of(Blocks.MAGMA_BLOCK, Blocks.POINTED_DRIPSTONE);
    }

    @Override
    public int cell(int x, int y, int z) {
        // Waehrend der Suche laedt das Spiel weiter Chunks nach. Damit die Suche ein stimmiges Bild
        // hat, bleibt ein Chunk, der einmal als ungeladen gesehen wurde, fuer diese Suche ungeladen.
        long chunk = ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xFFFFFFFFL);
        if (unloadedChunks.contains(chunk)) {
            sawUnloaded = true;
            return Pathfinder.UNLOADED;
        }
        long k = Pathfinder.key(x, y, z);
        Integer c = cache.get(k);
        if (c == null) {
            c = compute(x, y, z);
            cache.put(k, c);
        }
        if ((c & Pathfinder.UNLOADED) != 0) {
            sawUnloaded = true;
            if (Math.abs(y - refY) <= 48) unloadedChunks.add(chunk);
        }
        return c;
    }

    private int compute(int x, int y, int z) {
        if (level == null) return Pathfinder.UNLOADED;
        initSets();
        pos.set(x, y, z);
        BlockState bs = level.getBlockState(pos);
        // nicht geladene Chunks und alles ausserhalb der Welt liefern "Void-Luft"
        if (bs.is(Blocks.VOID_AIR)) return Pathfinder.UNLOADED;
        if (bs.isAir()) return Pathfinder.PASS;
        if (bs.is(Blocks.LAVA)) return Pathfinder.AVOID | Pathfinder.FLUID;

        Block b = bs.getBlock();
        boolean fluid = !bs.getFluidState().isEmpty();
        int c = fluid ? Pathfinder.FLUID : 0;
        if (danger.contains(b)) c |= Pathfinder.AVOID;
        if (hotFloor.contains(b)) c |= Pathfinder.AVOID_FLOOR;
        if (b instanceof FallingBlock) c |= Pathfinder.FALLING;
        // Tueren und Zauntore oeffnet der Walker selbst
        if (Walker.isOpenable(bs)) return c | Pathfinder.PASS;
        // Seerosen: man steht darauf (obwohl sie sehr flach sind)
        if (b == Blocks.LILY_PAD) return c | Pathfinder.STAND;

        VoxelShape shape = bs.getCollisionShape(level, pos);
        if (shape.isEmpty()) return c | (fluid ? Pathfinder.WATER : Pathfinder.PASS);
        double top = shape.max(Direction.Axis.Y);
        if (top < 0.49) {
            c |= Pathfinder.PASS;   // Teppich, duenner Schnee: einfach drueber laufen
        } else if (top <= 1.001) {
            c |= Pathfinder.STAND;  // volle Bloecke, Stufen, Truhen, Ackerboden ...
        }                           // hoeher (Zaun, Mauer): weder frei noch begehbar
        if ((c & Pathfinder.PASS) == 0 && !fluid && breakable(bs, b, x, y, z)) c |= Pathfinder.BREAKABLE;
        return c;
    }

    private boolean breakable(BlockState bs, Block b, int x, int y, int z) {
        if (bs.hasBlockEntity()) return false;                         // Kisten, Oefen, Schilder ...
        if (b == Blocks.ICE || b == Blocks.FROSTED_ICE) return false;  // wird zu Wasser
        if (TestClient.isPlaced(BlockPos.asLong(x, y, z))) return false; // eigene Bruecken und Saeulen
        float hardness = bs.getDestroySpeed(level, pos);
        if (hardness < 0.0f) return false;                             // Grundgestein, Barriere
        return ticksFor(bs, hardness) <= MAX_BREAK_TICKS;
    }

    private double ticksFor(BlockState bs, float hardness) {
        Double t = times.get(bs);
        if (t == null) {
            t = Walker.toolTicks(player, bs, hardness);
            times.put(bs, t);
        }
        return t;
    }

    @Override
    public double breakTicks(int x, int y, int z) {
        if (level == null) return MAX_BREAK_TICKS;
        pos.set(x, y, z);
        BlockState bs = level.getBlockState(pos);
        return ticksFor(bs, bs.getDestroySpeed(level, pos));
    }
}
