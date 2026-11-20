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
    public static int rangeChunks = 6;   // Spieler, Mobs, Items (in Chunks, 1 Chunk = 16 Bloecke)
    public static int blockChunks = 6;   // Bloecke (in Chunks)

    /** Leer = alle Mobs; sonst nur diese Typen (auch Nicht-Mobs wie Boote, wenn gewaehlt). */
    public static final Set<EntityType<?>> mobTypes = new LinkedHashSet<>();
    /** Leer = alle Items am Boden; sonst nur diese. */
    public static final Set<Item> itemTypes = new LinkedHashSet<>();
    /** Bloecke, die hervorgehoben werden (leer = keine). */
    public static final Set<Block> blockTypes = new LinkedHashSet<>();

    public static final int COLOR_PLAYER = 0xFFFF4D4D;
    public static final int COLOR_MOB = 0xFFFFB13B;
    public static final int COLOR_ITEM = 0xFF4DD2FF;
    public static final int COLOR_BLOCK = 0xFF39FF14;

    /** Eigene Farben als 0xRRGGBB, -1 = Standardfarbe. */
    public static int colPlayer = -1;
    public static int colMob = -1;
    public static int colItem = -1;
    public static int colBlock = -1;

    private static int pick(int custom, int def) {
        return custom >= 0 ? 0xFF000000 | custom : def;
    }

    public static int playerColor() {
        return pick(colPlayer, COLOR_PLAYER);
    }

    public static int mobColor() {
        return pick(colMob, COLOR_MOB);
    }

    public static int itemColor() {
        return pick(colItem, COLOR_ITEM);
    }

    public static int blockColor() {
        return pick(colBlock, COLOR_BLOCK);
    }

    private static final int MAX_TARGETS = 250;
    private static final int MAX_BLOCKS = 400;
    private static final int SCAN_INTERVAL = 10;

    private static final List<BlockPos> foundBlocks = new ArrayList<>();
    private static final ChunkScan SCAN = new ChunkScan(MAX_BLOCKS);

    public static void register() {
        HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("test", "esp"), Safe.hud("ESP", Esp::render));
    }

    /** Fuer das Menue: Stand der Suche (und ob der Server Erze versteckt). */
    static String blockStatus() {
        return enabled && blocks && !blockTypes.isEmpty() ? SCAN.status(foundBlocks) : Lang.t("Sucht, sobald es an ist.");
    }

    /** Nach Aenderungen an der Blockauswahl sofort neu suchen. */
    public static void rescan() {
        SCAN.restart();
    }

    // ---------- Bloecke suchen (im Tick, nicht pro Frame) ----------

    public static void tick(Minecraft mc) {
        if (!enabled || !blocks || blockTypes.isEmpty() || mc.player == null || mc.level == null) {
            SCAN.clear(foundBlocks);
            return;
        }
        // Chunk fuer Chunk, neue Chunks und Blockaenderungen sofort (siehe ChunkScan)
        int r = blockChunks * 16;
        SCAN.tick(mc.level, mc.player.blockPosition(), r, r, r, true, st -> blockTypes.contains(st.getBlock()),
                blockTypes.hashCode() * 31 + blockTypes.size(), foundBlocks);
    }

    // ---------- Zeichnen (ueber Render3D) ----------

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled) return;
        if (!Render3D.begin(mc, delta.getGameTimeDeltaPartialTick(true))) return;
        Player self = mc.player;
        float oldAlpha = Ui.alpha;
        Ui.alpha = 1f;
        int drawn = 0;
        try {
            if (blocks && !blockTypes.isEmpty() && RenderMods.perfMode) {
                // Leistungs-Modus: zusammenhaengende Bloecke als ein Kasten, ohne Linien und Namen
                Render3D.blocks(g, foundBlocks, foundBlocks,
                        p -> blockTypes.contains(mc.level.getBlockState(p).getBlock()) ? blockColor() : 0, 1);
            } else if (blocks && !blockTypes.isEmpty()) {
                for (BlockPos pos : foundBlocks) {
                    if (drawn >= MAX_TARGETS) break;
                    Block b = mc.level.getBlockState(pos).getBlock();
                    if (!blockTypes.contains(b)) continue; // inzwischen abgebaut
                    AABB bb = new AABB(pos.getX(), pos.getY(), pos.getZ(),
                            pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
                    drawOne(g, mc, self, bb, blockColor(), b.getName().getString());
                    drawn++;
                }
            }
            if (players || mobs || items) {
                double radius = rangeChunks * 16.0;
                AABB area = self.getBoundingBox().inflate(radius);
                for (Entity e : mc.level.getEntities(self, area)) {
                    if (drawn >= MAX_TARGETS) break;
                    if (!e.isAlive()) continue;
                    int color;
                    String name;
                    if (e instanceof Player) {
                        if (!players) continue;
                        color = Friends.espColor(e, playerColor());
                        name = e.getName().getString();
                    } else if (e instanceof ItemEntity ie) {
                        if (!items) continue;
                        if (!itemTypes.isEmpty() && !itemTypes.contains(ie.getItem().getItem())) continue;
                        color = itemColor();
                        int n = ie.getItem().getCount();
                        name = ie.getItem().getHoverName().getString() + (n > 1 ? " x" + n : "");
                    } else if (e instanceof LivingEntity) {
                        if (!mobs) continue;
                        if (!mobTypes.isEmpty() && !mobTypes.contains(e.getType())) continue;
                        color = mobColor();
                        name = e.getName().getString();
                    } else {
                        // Andere Objekte (Boote, Loren, ...) nur, wenn ausdruecklich gewaehlt
                        if (!mobs || !mobTypes.contains(e.getType())) continue;
                        color = mobColor();
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
        if (box) Render3D.outline(g, bb, color, false);
        if (labels) {
            double dist = self.getEyePosition().distanceTo(bb.getCenter());
            Render3D.label(g, mc, new Vec3(bb.getCenter().x, bb.maxY, bb.getCenter().z),
                    name + "  " + Math.round(dist) + "m", color, -12);
        }
    }
}
