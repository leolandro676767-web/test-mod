package de.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** Spieler-Radar. */
public final class HudMods {
    private HudMods() {
    }

    // =====================================================================================
    // Einstellungen (werden mit dem Praefix "hm." gespeichert)
    // =====================================================================================

    // --- Spieler-Radar ---
    public static boolean radar = false;
    public static int radarCorner = 1;         // InfoHud.CORNERS
    public static int radarMax = 8;
    public static int radarRange = 0;          // Bloecke, 0 = alle, die geladen sind
    public static boolean radarHealth = true;
    public static boolean radarArrow = true;

    static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("test", "hud_mods"),
                Safe.hud("Anzeige", HudMods::render));
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.hud.isHidden()) return;
        float old = Ui.alpha;
        Ui.alpha = 1f;
        try {
            if (radar) Safe.run("Spieler-Radar", () -> drawRadar(g, mc));
        } finally {
            Ui.alpha = old;
        }
    }

    // =====================================================================================
    // Spieler-Radar
    // =====================================================================================

    private static void drawRadar(GuiGraphicsExtractor g, Minecraft mc) {
        LocalPlayer self = mc.player;
        List<Player> list = new ArrayList<>();
        for (Player pl : mc.level.players()) {
            if (pl == self || pl.isSpectator() || PlayerMods.isFake(pl)) continue;
            if (radarRange > 0 && self.distanceTo(pl) > radarRange) continue;
            list.add(pl);
        }
        list.sort((a, b) -> Double.compare(self.distanceToSqr(a), self.distanceToSqr(b)));
        int n = Math.min(list.size(), Math.max(1, radarMax));
        var font = mc.font;
        String title = Lang.t("Spieler in der Nähe") + " (" + list.size() + ")";
        int w = font.width(title) + 12;
        String[] left = new String[n];
        String[] right = new String[n];
        for (int i = 0; i < n; i++) {
            Player pl = list.get(i);
            left[i] = pl.getName().getString(); // Namen nie uebersetzen
            StringBuilder r = new StringBuilder();
            r.append(Math.round(self.distanceTo(pl))).append('m');
            if (radarHealth) {
                float hp = (pl.getHealth() + pl.getAbsorptionAmount()) / 2f;
                r.append("  ").append(String.format(Locale.ROOT, "%.1f", hp).replace(".0", "")).append(" ❤");
            }
            right[i] = r.toString();
            w = Math.max(w, 8 + (radarArrow ? 10 : 0) + font.width(left[i]) + 10 + font.width(right[i]) + 8);
        }
        int h = 16 + n * 11 + (list.isEmpty() ? 11 : 0) + 4;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        boolean rightSide = radarCorner == 1 || radarCorner == 3;
        boolean bottom = radarCorner >= 2;
        int off = Extras.infoOffset(mc, radarCorner);
        if (off == 0 && radarCorner == 1) off = Extras.baseTop(mc, 1); // unter die Trank-Symbole
        int x = rightSide ? sw - 4 - w : 4;
        int y = bottom ? sh - 4 - h - off : 4 + off;
        Ui.roundRect(g, x, y, w, h, 4, 0xB0101018);
        Ui.fill(g, x + 2, y, x + w - 2, y + 1, 0xFF000000 | Theme.accent());
        Ui.text(g, font, title, x + 6, y + 4, 0xFFFFFFFF, true);
        if (list.isEmpty()) {
            Ui.text(g, font, Lang.t("niemand"), x + 6, y + 16, 0xFF9AA0B0, true);
            return;
        }
        for (int i = 0; i < n; i++) {
            Player pl = list.get(i);
            int ly = y + 16 + i * 11;
            int tx = x + 6;
            if (radarArrow) {
                drawArrow(g, tx + 3, ly + 4, angleTo(self, pl), 0xFFDDE3F0);
                tx += 10;
            }
            int col = Friends.is(pl) ? Friends.color() : 0xFFFFFFFF;
            Ui.text(g, font, left[i], tx, ly, col, true);
            Ui.text(g, font, right[i], x + w - 6 - font.width(right[i]), ly, 0xFFFF8A9A, true);
        }
    }

    /** Richtung zum anderen Spieler, bezogen auf deine Blickrichtung (Grad, 0 = geradeaus). */
    private static float angleTo(LocalPlayer self, Entity other) {
        double dx = other.getX() - self.getX();
        double dz = other.getZ() - self.getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        return Mth.wrapDegrees(yaw - self.getYRot());
    }

    /** Kleiner Pfeil aus Punkten (3x3), der in die Richtung zeigt. */
    private static void drawArrow(GuiGraphicsExtractor g, int cx, int cy, float angle, int color) {
        double a = Math.toRadians(angle);
        double sx = Math.sin(a);
        double sy = -Math.cos(a);
        for (int i = -3; i <= 3; i++) {
            int px = (int) Math.round(cx + sx * i);
            int py = (int) Math.round(cy + sy * i);
            Ui.fill(g, px, py, px + 1, py + 1, color);
        }
        // Spitze
        double bx = cx + sx * 3;
        double by = cy + sy * 3;
        for (int k = -1; k <= 1; k += 2) {
            double ra = a + Math.PI + k * 0.6;
            for (int i = 1; i <= 2; i++) {
                int px = (int) Math.round(bx + Math.sin(ra) * i);
                int py = (int) Math.round(by - Math.cos(ra) * i);
                Ui.fill(g, px, py, px + 1, py + 1, color);
            }
        }
    }

    // =====================================================================================
    // Einstellungen
    // =====================================================================================

    static Screen screen(String module, Screen parent) {
        return switch (module) {
            case "Spieler-Radar" -> new ModuleScreen(parent, module, f -> {
                f.description("Liste der Spieler in deiner Nähe - mit Entfernung, Leben und einem Pfeil in ihre "
                        + "Richtung.");
                f.choice("Ecke", InfoHud.CORNERS, () -> radarCorner, v -> radarCorner = v, 1);
                f.slider("Höchstens (Spieler)", 1, 20, () -> radarMax, v -> radarMax = v, 8);
                f.slider("Bis (Blöcke, 0 = alle)", 0, 500, () -> radarRange, v -> radarRange = v, 0);
                f.toggle("Leben zeigen", () -> radarHealth, v -> radarHealth = v, true);
                f.toggle("Pfeil in ihre Richtung", () -> radarArrow, v -> radarArrow = v, true);
                f.note("Man sieht nur Spieler, die dein Spiel geladen hat (meist bis etwa 100 Blöcke). Freunde "
                        + "stehen in ihrer Farbe da.");
                f.footer(() -> radar, v -> radar = v);
            });
            default -> null;
        };
    }
}
