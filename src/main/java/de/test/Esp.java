package de.test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ESP: zeigt Spieler, Mobs, Items am Boden und gewaehlte Bloecke durch Waende.
 * Gezeichnet wird auf dem HUD: die 3D-Positionen werden selbst auf den Bildschirm projiziert.
 */
public final class Esp {
    private Esp() {
    }

    // ---------- Einstellungen (werden in der Config gespeichert) ----------

    public static boolean enabled = false;
    public static boolean players = true;
    public static boolean mobs = true;
    public static boolean items = false;
    public static boolean blocks = false;
    public static boolean box = true;
    public static boolean tracers = false;
    public static boolean labels = true;
    public static int radius = 64;       // Spieler, Mobs, Items
    public static int blockRadius = 24;  // Bloecke

    /** Leer = alle Mobs; sonst nur diese Typen (auch Nicht-Mobs wie Boote, wenn gewaehlt). */
    public static final Set<EntityType<?>> mobTypes = new LinkedHashSet<>();
    /** Leer = alle Items am Boden; sonst nur diese. */
    public static final Set<Item> itemTypes = new LinkedHashSet<>();
    /** Bloecke, die hervorgehoben werden (leer = keine). */
    public static final Set<Block> blockTypes = new LinkedHashSet<>();

    public static final int COLOR_PLAYER = 0xFFFF4D4D;
    public static final int COLOR_MOB = 0xFFFFB13B;
    public static final int COLOR_ITEM = 0xFF4DD2FF;
    public static final int COLOR_BLOCK = 0xFF5BFF7A;

    private static final int MAX_TARGETS = 250;
    private static final int MAX_BLOCKS = 400;
    private static final int SCAN_INTERVAL = 10;

    private static final List<BlockPos> foundBlocks = new ArrayList<>();
    private static int scanCooldown = 0;

    public static void register() {
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("test", "esp"), Esp::render);
    }

    /** Nach Aenderungen an der Blockauswahl sofort neu suchen. */
    public static void rescan() {
        scanCooldown = 0;
    }

    // ---------- Bloecke suchen (im Tick, nicht pro Frame) ----------

    public static void tick(Minecraft mc) {
        if (!enabled || !blocks || blockTypes.isEmpty() || mc.player == null || mc.level == null) {
            if (!foundBlocks.isEmpty()) foundBlocks.clear();
            return;
        }
        if (--scanCooldown > 0) return;
        scanCooldown = SCAN_INTERVAL;

        Level level = mc.level;
        BlockPos c = mc.player.blockPosition();
        int r = blockRadius;
        int r2 = r * r;
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (blockTypes.contains(level.getBlockState(m).getBlock())) {
                        found.add(m.immutable());
                    }
                }
            }
        }
        found.sort((a, b) -> Double.compare(a.distSqr(c), b.distSqr(c)));
        foundBlocks.clear();
        foundBlocks.addAll(found.size() > MAX_BLOCKS ? found.subList(0, MAX_BLOCKS) : found);
    }

    // ---------- Zeichnen (ueber Render3D) ----------

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.options.hideGui) return;
        if (!Render3D.begin(mc, delta.getGameTimeDeltaPartialTick(true))) return;
        Player self = mc.player;
        float oldAlpha = Ui.alpha;
        Ui.alpha = 1f;
        int drawn = 0;
        try {
            if (blocks && !blockTypes.isEmpty()) {
                for (BlockPos pos : foundBlocks) {
                    if (drawn >= MAX_TARGETS) break;
                    Block b = mc.level.getBlockState(pos).getBlock();
                    if (!blockTypes.contains(b)) continue; // inzwischen abgebaut
                    AABB bb = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                            pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
                    drawOne(g, mc, self, bb, COLOR_BLOCK, b.getName().getString());
                    drawn++;
                }
            }
            if (players || mobs || items) {
                AABB area = self.getBoundingBox().inflate(radius);
                for (Entity e : mc.level.getEntities(self, area)) {
                    if (drawn >= MAX_TARGETS) break;
                    if (!e.isAlive()) continue;
                    int color;
                    String name;
                    if (e instanceof Player) {
                        if (!players) continue;
                        color = COLOR_PLAYER;
                        name = e.getName().getString();
                    } else if (e instanceof ItemEntity ie) {
                        if (!items) continue;
                        if (!itemTypes.isEmpty() && !itemTypes.contains(ie.getItem().getItem())) continue;
                        color = COLOR_ITEM;
                        int n = ie.getItem().getCount();
                        name = ie.getItem().getHoverName().getString() + (n > 1 ? " x" + n : "");
                    } else if (e instanceof LivingEntity) {
                        if (!mobs) continue;
                        if (!mobTypes.isEmpty() && !mobTypes.contains(e.getType())) continue;
                        color = COLOR_MOB;
                        name = e.getName().getString();
                    } else {
                        // Andere Objekte (Boote, Loren, ...) nur, wenn ausdruecklich gewaehlt
                        if (!mobs || !mobTypes.contains(e.getType())) continue;
                        color = COLOR_MOB;
                        name = e.getName().getString();
                    }
                    AABB bb = Render3D.smoothBox(e);
                    if (self.getEyePosition().distanceTo(bb.getCenter()) > radius) continue;
                    drawOne(g, mc, self, bb, color, name);
                    drawn++;
                }
            }
        } finally {
            Ui.alpha = oldAlpha;
        }
    }

    private static void drawOne(GuiGraphicsExtractor g, Minecraft mc, Player self, AABB bb, int color, String name) {
        if (tracers) Render3D.tracer(g, bb.getCenter(), (color & 0x00FFFFFF) | 0xB0000000);
        if (box) Render3D.box(g, bb, color, false);
        if (labels) {
            double dist = self.getEyePosition().distanceTo(bb.getCenter());
            Render3D.label(g, mc, new Vec3(bb.getCenter().x, bb.maxY, bb.getCenter().z),
                    name + "  " + Math.round(dist) + "m", color, -12);
        }
    }
}
