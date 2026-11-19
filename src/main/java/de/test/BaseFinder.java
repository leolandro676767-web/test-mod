package de.test;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Base Finder: Spuren, dass hier lange jemand war. Pflanzen und Kristalle wachsen nur, solange ein Spieler in der
 * Naehe ist (der Chunk geladen ist) - voll ausgewachsenes Zuckerrohr, fertige Amethyst-Cluster in Geoden und
 * Seetang bis zur Wasseroberflaeche sind deshalb ein Hinweis auf eine Base in der Naehe.
 */
public final class BaseFinder {
    private BaseFinder() {
    }

    // =====================================================================================
    // Einstellungen (werden mit dem Praefix "bf." gespeichert)
    // =====================================================================================

    public static boolean enabled = false;
    public static int range = 6;               // Chunks
    public static boolean cane = true;
    public static boolean amethyst = true;
    public static boolean kelp = true;
    public static boolean boxes = true;
    public static boolean panel = true;
    public static int corner = 3;              // InfoHud.CORNERS
    public static boolean notify = true;
    public static int threshold = 12;          // so viele Spuren = "hier waren wohl Spieler"

    private static final int C_CANE = 0xFF5CFF7A;
    private static final int C_AMETHYST = 0xFFC77DFF;
    private static final int C_KELP = 0xFF3DD6C6;
    private static final int MAX_DRAW = 150;   // je Art hoechstens so viele Kaesten

    private static final ChunkScan SCAN = new ChunkScan(4000);
    private static final List<BlockPos> raw = new ArrayList<>();
    private static final List<AABB> caneFound = new ArrayList<>();
    private static final List<AABB> amethystFound = new ArrayList<>();
    private static final List<AABB> kelpFound = new ArrayList<>();
    private static int sortTick;
    private static int buds;                   // noch nicht fertige Amethyst-Knospen im Umkreis
    private static BlockPos notifiedAt;

    static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("test", "base_finder"),
                Safe.hud("Base Finder", BaseFinder::render));
    }

    static void reset() {
        SCAN.clear(raw);
        caneFound.clear();
        amethystFound.clear();
        kelpFound.clear();
        notifiedAt = null;
    }

    private static boolean bud(Block b) {
        return b == Blocks.SMALL_AMETHYST_BUD || b == Blocks.MEDIUM_AMETHYST_BUD || b == Blocks.LARGE_AMETHYST_BUD;
    }

    private static String id(Block b) {
        return BuiltInRegistries.BLOCK.getKey(b).getPath();
    }

    /** Was die Suche ueberhaupt anschaut (Nachbarn prueft sie nicht - das passiert danach). */
    private static boolean candidate(BlockState s) {
        Block b = s.getBlock();
        return cane && b == Blocks.SUGAR_CANE || kelp && b == Blocks.KELP
                || amethyst && (b == Blocks.AMETHYST_CLUSTER || bud(b));
    }

    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        ClientLevel level = mc.level;
        if (!enabled || p == null || level == null) {
            if (!raw.isEmpty() || !caneFound.isEmpty()) reset();
            return;
        }
        int r = Math.max(1, range) * 16;
        int key = (cane ? 1 : 0) | (amethyst ? 2 : 0) | (kelp ? 4 : 0);
        SCAN.tick(level, p.blockPosition(), r, 400, 400, false, BaseFinder::candidate, key, raw);
        if (++sortTick % 10 != 0) return;
        // Treffer pruefen und einteilen (alle halbe Sekunde reicht)
        caneFound.clear();
        amethystFound.clear();
        kelpFound.clear();
        buds = 0;
        for (BlockPos pos : raw) {
            BlockState s = level.getBlockState(pos);
            Block b = s.getBlock();
            if (bud(b)) {
                buds++;
            } else if (b == Blocks.AMETHYST_CLUSTER) {
                amethystFound.add(new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0,
                        pos.getZ() + 1.0));
            } else if (b == Blocks.SUGAR_CANE) {
                // nur die Spitze einer Stange, die 3 Bloecke hoch ist (hoeher waechst Zuckerrohr nicht)
                if (level.getBlockState(pos.above()).is(Blocks.SUGAR_CANE)) continue;
                if (!level.getBlockState(pos.below()).is(Blocks.SUGAR_CANE)
                        || !level.getBlockState(pos.below(2)).is(Blocks.SUGAR_CANE)) {
                    continue;
                }
                caneFound.add(new AABB(pos.getX() + 0.2, pos.getY() - 2.0, pos.getZ() + 0.2, pos.getX() + 0.8,
                        pos.getY() + 1.0, pos.getZ() + 0.8));
            } else if (b == Blocks.KELP) {
                // Seetang, der bis an die Oberflaeche gewachsen ist (und nicht nur in flachem Wasser steht)
                if (!level.getBlockState(pos.above()).getFluidState().isEmpty()) continue;
                int len = 1;
                while (len < 30 && id(level.getBlockState(pos.below(len)).getBlock()).equals("kelp_plant")) len++;
                if (len < 6) continue;
                kelpFound.add(new AABB(pos.getX() + 0.15, pos.getY() - len + 1.0, pos.getZ() + 0.15,
                        pos.getX() + 0.85, pos.getY() + 1.0, pos.getZ() + 0.85));
            }
        }
        int score = score();
        if (notify && score >= Math.max(1, threshold)) {
            BlockPos here = p.blockPosition();
            if (notifiedAt == null || notifiedAt.distSqr(here) > (double) r * r) {
                notifiedAt = here;
                p.sendSystemMessage(Lang.literal("§d1912: " + Lang.t("Base Finder: viele Spuren in der Nähe")
                        + " (" + summary() + ") " + Lang.t("bei ") + here.getX() + " " + here.getZ()));
            }
        }
    }

    /**
     * Wie viele Spuren. Auch frische Geoden haben schon ein paar fertige Cluster - die zaehlen erst, wenn mehr
     * Cluster als Knospen da sind (dann ist dort lange etwas gewachsen).
     */
    private static int score() {
        int a = amethystFound.size() > buds ? amethystFound.size() : 0;
        return caneFound.size() + a + kelpFound.size();
    }

    private static String summary() {
        List<String> parts = new ArrayList<>();
        if (cane) parts.add(caneFound.size() + " " + Lang.t("Zuckerrohr"));
        if (amethyst) parts.add(amethystFound.size() + " " + Lang.t("Amethyst"));
        if (kelp) parts.add(kelpFound.size() + " " + Lang.t("Seetang"));
        return String.join(", ", parts);
    }

    /** Fuer das Menue: wie weit die Suche ist. */
    static String status() {
        return enabled ? SCAN.status(raw) : Lang.t("Sucht, sobald es an ist.");
    }

    // =====================================================================================
    // Anzeige
    // =====================================================================================

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.player == null || mc.gui.hud.isHidden()) return;
        float old = Ui.alpha;
        Ui.alpha = 1f;
        try {
            if (boxes && Render3D.begin(mc, delta.getGameTimeDeltaPartialTick(true))) {
                draw(g, caneFound, C_CANE);
                draw(g, amethystFound, C_AMETHYST);
                draw(g, kelpFound, C_KELP);
            }
            if (panel && mc.gui.screen() == null) drawPanel(g, mc);
        } finally {
            Ui.alpha = old;
        }
    }

    private static void draw(GuiGraphicsExtractor g, List<AABB> list, int color) {
        int n = Math.min(list.size(), MAX_DRAW); // die Liste ist nach Entfernung sortiert: die naechsten zuerst
        for (int i = 0; i < n; i++) Render3D.outline(g, list.get(i), color, false);
    }

    /** Das Feld mit den Zahlen und der Einschaetzung. */
    private static void drawPanel(GuiGraphicsExtractor g, Minecraft mc) {
        var font = mc.font;
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (cane) {
            lines.add(Lang.t("Zuckerrohr (3 hoch): ") + caneFound.size());
            colors.add(C_CANE);
        }
        if (amethyst) {
            lines.add(Lang.t("Amethyst-Cluster: ") + amethystFound.size() + Lang.t(" (Knospen: ") + buds + ")");
            colors.add(C_AMETHYST);
        }
        if (kelp) {
            lines.add(Lang.t("Seetang bis oben: ") + kelpFound.size());
            colors.add(C_KELP);
        }
        int score = score();
        int t = Math.max(1, threshold);
        String verdict = score >= t ? Lang.t("Hier waren wohl Spieler!")
                : score >= t / 2 ? Lang.t("Ein paar Spuren") : Lang.t("Kaum Spuren");
        int vColor = score >= t ? 0xFFFF6B81 : score >= t / 2 ? 0xFFFFD84D : 0xFF9AA0B0;
        String title = "Base Finder";
        int w = font.width(title) + 12;
        for (String l : lines) w = Math.max(w, font.width(l) + 20);
        w = Math.max(w, font.width(verdict) + 12);
        int h = 16 + lines.size() * 11 + 13;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        boolean right = corner == 1 || corner == 3;
        boolean bottom = corner >= 2;
        int off = Extras.infoOffset(mc, corner);
        if (off == 0 && corner == 1) off = Extras.baseTop(mc, 1);
        int x = right ? sw - 4 - w : 4;
        int y = bottom ? sh - 4 - h - off : 4 + off;
        Ui.roundRect(g, x, y, w, h, 4, 0xB0101018);
        Ui.fill(g, x + 2, y, x + w - 2, y + 1, vColor);
        Ui.text(g, font, title, x + 6, y + 4, 0xFFFFFFFF, true);
        for (int i = 0; i < lines.size(); i++) {
            int ly = y + 16 + i * 11;
            Ui.fill(g, x + 6, ly + 2, x + 11, ly + 7, colors.get(i));
            Ui.text(g, font, lines.get(i), x + 15, ly, 0xFFDDE3F0, true);
        }
        Ui.text(g, font, verdict, x + 6, y + 16 + lines.size() * 11 + 2, vColor, true);
    }

    // =====================================================================================
    // Einstellungen
    // =====================================================================================

    static Screen screen(Screen parent) {
        return new ModuleScreen(parent, "Base Finder", f -> {
            f.description("Zeigt Spuren, dass hier lange Spieler waren: Pflanzen und Kristalle wachsen nur, solange "
                    + "jemand in der Nähe ist. Viele davon = wahrscheinlich eine Base in der Nähe.");
            f.slider("Reichweite (Chunks)", 2, 12, () -> range, v -> range = v, 6);
            f.section("Spuren");
            f.toggle("Zuckerrohr, 3 Blöcke hoch (grün)", () -> cane, v -> cane = v, true);
            f.toggle("Amethyst-Cluster in Geoden (lila)", () -> amethyst, v -> amethyst = v, true);
            f.toggle("Seetang bis zur Oberfläche (türkis)", () -> kelp, v -> kelp = v, true);
            f.section("Anzeige");
            f.toggle("Kästen um die Spuren", () -> boxes, v -> boxes = v, true);
            f.toggle("Feld mit den Zahlen", () -> panel, v -> panel = v, true);
            f.choice("Ecke", InfoHud.CORNERS, () -> corner, v -> corner = v, 3);
            f.toggle("Im Chat melden", () -> notify, v -> notify = v, true);
            f.slider("Ab so vielen Spuren melden", 3, 60, () -> threshold, v -> threshold = v, 12);
            f.note("Das sind Hinweise, keine Beweise: Zuckerrohr kann auch von selbst 3 hoch stehen, und frische "
                    + "Geoden haben schon ein paar Cluster (die zählen erst, wenn es mehr Cluster als Knospen sind). Erst "
                    + "viele Spuren auf einmal sagen wirklich etwas.");
            f.note(BaseFinder::status);
            f.footer(() -> enabled, v -> enabled = v);
        });
    }
}
