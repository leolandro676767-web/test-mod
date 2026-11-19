package de.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Better Tab: eigene Spielerliste mit Ping in Millisekunden, solange die Tab-Taste gehalten wird.
 * Minecrafts eigene Liste wird dabei unterdrueckt. Schalter: {@link RenderMods#betterTab}.
 */
public final class BetterTab {
    private BetterTab() {
    }

    /** Jeden Frame (aus RenderMods.render): Tab-Taste lesen und Minecrafts Liste unterdruecken. */
    static void frame(Minecraft mc) {
        tabHeld = false;
        if (!RenderMods.betterTab) return;
        long win = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        if (win != 0L && CameraMods.down(mc.options.keyPlayerList, win)) {
            tabHeld = true;
            mc.options.keyPlayerList.setDown(false);
        }
    }

    private static boolean tabHeld;

    /** Eigene Spielerliste mit Ping in Millisekunden, solange die Tab-Taste gehalten wird. */
    static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!RenderMods.betterTab || !tabHeld || mc.player == null || mc.gui.screen() != null) return;
        var conn = mc.getConnection();
        if (conn == null) return;
        List<String> names = new ArrayList<>();
        Map<String, Integer> pings = new HashMap<>();
        for (var info : conn.getOnlinePlayers()) {
            String n = info.getProfile().name();
            names.add(n);
            pings.put(n, info.getLatency());
        }
        if (names.isEmpty()) return;
        names.sort(String.CASE_INSENSITIVE_ORDER);
        boolean friendsFirst = Friends.enabled && Friends.showTab;
        if (friendsFirst) {
            // Freunde oben, sonst alphabetisch
            names.sort((a, b) -> Boolean.compare(Friends.listed(b), Friends.listed(a)));
        }
        String me = mc.player.getGameProfile().name();

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int rowH = 11;
        int maxRows = Math.max(5, (sh - 80) / rowH);
        int cols = Math.max(1, (names.size() + maxRows - 1) / maxRows);
        int rows = (names.size() + cols - 1) / cols;
        int nameW = 0;
        for (String n : names) nameW = Math.max(nameW, mc.font.width(n));
        int pingW = mc.font.width("0000 ms");
        int colW = 6 + nameW + 14 + pingW + 6;
        while (cols > 1 && cols * colW + 12 > sw) cols--; // sehr volle Server: lieber weniger Spalten
        rows = Math.min(maxRows, (names.size() + cols - 1) / cols);
        int w = cols * colW;
        int h = Ui.HEADER_H + rows * rowH + 6;
        int x = (sw - w) / 2;
        int y = 12;

        float old = Ui.alpha;
        Ui.alpha = 1f;
        Ui.panel(g, mc.font, x, y, w, h, "Spieler", 0);
        String count = Integer.toString(names.size());
        Ui.text(g, mc.font, count, x + w - 10 - mc.font.width(count), y + 7, 0xD0FFFFFF, true);
        for (int i = 0; i < names.size() && i < rows * cols; i++) {
            String n = names.get(i);
            int cx = x + (i / rows) * colW;
            int cy = y + Ui.HEADER_H + 4 + (i % rows) * rowH;
            int ping = pings.getOrDefault(n, 0);
            int pc = ping <= 0 ? Ui.MUTED : ping < 80 ? 0xFF7CF29A : ping < 160 ? 0xFFF2E27C : ping < 300 ? 0xFFFFB13B
                    : 0xFFFF6B6B;
            String pt = ping <= 0 ? "?" : ping + " ms";
            int nc = n.equals(me) ? Theme.accent() : friendsFirst && Friends.listed(n) ? Friends.color() : Ui.TEXT;
            g.text(mc.font, n, cx + 6, cy, Ui.mul(nc), true); // Namen nie uebersetzen
            Ui.text(g, mc.font, pt, cx + colW - 6 - mc.font.width(pt), cy, pc, true);
        }
        Ui.alpha = old;
    }
}

/** Fuegt dem Tooltip Zusatzinfos hinzu: Shulker-Inhalt, Haltbarkeit, Naehrwert. */
final class BetterTooltips {
    private BetterTooltips() {
    }

    static void append(ItemStack stack, List<Component> lines) {
        if (RenderMods.tipDurability && stack.isDamageableItem()) {
            int max = stack.getMaxDamage();
            int left = max - stack.getDamageValue();
            int pct = left * 100 / Math.max(1, max);
            ChatFormatting c = pct > 50 ? ChatFormatting.GREEN : pct > 20 ? ChatFormatting.YELLOW : ChatFormatting.RED;
            lines.add(Lang.literal("Haltbarkeit: " + left + "/" + max + " (" + pct + "%)").withStyle(c));
        }
        if (RenderMods.tipFood) {
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food != null) {
                lines.add(Lang.literal("N\u00e4hrwert: " + food.nutrition() + "  S\u00e4ttigung: "
                        + String.format("%.1f", food.saturation())).withStyle(ChatFormatting.GOLD));
            }
        }
        if (RenderMods.tipShulker) {
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
            if (contents == null) return;
            int shown = 0;
            int more = 0;
            for (var template : contents.nonEmptyItems()) {
                if (shown >= RenderMods.tipShulkerLines) {
                    more++; // passt nicht mehr in die Liste: nur mitzaehlen
                    continue;
                }
                ItemStack st = template.create();
                shown++;
                lines.add(Component.literal("  " + st.getCount() + "x ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(st.getHoverName().copy().withStyle(ChatFormatting.AQUA)));
            }
            if (more > 0) {
                lines.add(Lang.literal("  + " + more + " weitere").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }
}
