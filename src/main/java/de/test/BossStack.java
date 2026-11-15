package de.test;

import java.util.Map;
import java.util.UUID;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Boss Stack: merkt sich die Bossleisten selbst (vom ClientPacketListenerMixin) und zeichnet
 * gleiche Leisten zusammengefasst, mit Anzahl. Schalter: {@link RenderMods#bossStack}.
 */
public final class BossStack {
    private BossStack() {
    }

    /** Eine Bossleiste, wie der Server sie gemeldet hat. */
    private static final class Boss {
        volatile String name = "";
        volatile float progress = 1f;
        volatile int color = 0xFFB25DFF;
    }

    private static final Map<UUID, Boss> BOSSES = new java.util.concurrent.ConcurrentHashMap<>();

    private static int bossColor(Object color) {
        return switch (String.valueOf(color).toUpperCase(java.util.Locale.ROOT)) {
            case "PINK" -> 0xFFEC00B8;
            case "BLUE" -> 0xFF00B7EC;
            case "RED" -> 0xFFEC3500;
            case "GREEN" -> 0xFF1DEC00;
            case "YELLOW" -> 0xFFE9EC00;
            case "WHITE" -> 0xFFECECEC;
            default -> 0xFF7B00EC;
        };
    }

    /**
     * Boss Stack (vom ClientPacketListenerMixin): merkt sich die Bossleisten selbst. true = Minecraft
     * soll das Paket nicht mehr verarbeiten (dann zeichnen nur wir, gleiche Leisten zusammengefasst).
     */
    public static boolean onPacket(net.minecraft.network.protocol.game.ClientboundBossEventPacket packet) {
        if (!RenderMods.bossStack) return false;
        try {
            packet.dispatch(new net.minecraft.network.protocol.game.ClientboundBossEventPacket.Handler() {
                public void add(UUID id, Component name, float progress, net.minecraft.world.BossEvent.BossBarColor color,
                        net.minecraft.world.BossEvent.BossBarOverlay overlay, boolean darken, boolean music, boolean fog) {
                    Boss b = new Boss();
                    b.name = name.getString();
                    b.progress = progress;
                    b.color = bossColor(color);
                    BOSSES.put(id, b);
                }

                public void remove(UUID id) {
                    BOSSES.remove(id);
                }

                public void updateProgress(UUID id, float progress) {
                    Boss b = BOSSES.get(id);
                    if (b != null) b.progress = progress;
                }

                public void updateName(UUID id, Component name) {
                    Boss b = BOSSES.get(id);
                    if (b != null) b.name = name.getString();
                }

                public void updateStyle(UUID id, net.minecraft.world.BossEvent.BossBarColor color,
                        net.minecraft.world.BossEvent.BossBarOverlay overlay) {
                    Boss b = BOSSES.get(id);
                    if (b != null) b.color = bossColor(color);
                }
            });
        } catch (RuntimeException e) {
            return false;
        }
        return true;
    }

    /** Zeichnet die gemerkten Bossleisten: gleiche Namen als eine Leiste mit Anzahl. */
    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            BOSSES.clear();
            return;
        }
        if (!RenderMods.bossStack || BOSSES.isEmpty()) return;
        // nach Namen gruppieren: {Anzahl, Summe Fortschritt, niedrigster Fortschritt, Farbe}
        Map<String, float[]> groups = new java.util.LinkedHashMap<>();
        for (Boss b : BOSSES.values()) {
            float[] v = groups.computeIfAbsent(b.name, k -> new float[] {0f, 0f, 1f, b.color});
            v[0]++;
            v[1] += b.progress;
            v[2] = Math.min(v[2], b.progress);
        }
        int sw = mc.getWindow().getGuiScaledWidth();
        int w = 182;
        int x = (sw - w) / 2;
        int y = 12;
        float old = Ui.alpha;
        Ui.alpha = 1f;
        int shown = 0;
        for (var e : groups.entrySet()) {
            if (shown++ >= 6) break;
            float[] v = e.getValue();
            int n = Math.round(v[0]);
            int color = (int) v[3] | 0xFF000000;
            String label = n > 1 ? e.getKey() + "  x" + n : e.getKey();
            Ui.text(g, mc.font, label, (sw - mc.font.width(label)) / 2, y - 9, 0xFFFFFFFF, true);
            float avg = Math.max(0f, Math.min(1f, v[1] / Math.max(1f, v[0])));
            Ui.roundRect(g, x - 1, y - 1, w + 2, 7, 3, 0xC0000000);
            Ui.roundRect(g, x, y, w, 5, 2, Ui.lerp(color, 0xFF000000, 0.7f));
            int fw = Math.round(w * avg);
            if (fw > 0) Ui.roundGradientH(g, x, y, Math.max(4, fw), 5, 2, Ui.lerp(color, 0xFF000000, 0.25f), color, true, true);
            if (n > 1) {
                // bei mehreren: Strich beim schwaechsten Boss
                int mx = x + Math.round(w * Math.max(0f, Math.min(1f, v[2])));
                Ui.fill(g, Math.min(x + w - 1, mx), y - 1, Math.min(x + w, mx + 1), y + 6, 0xFFFFFFFF);
            }
            y += 19;
        }
        Ui.alpha = old;
    }
}
