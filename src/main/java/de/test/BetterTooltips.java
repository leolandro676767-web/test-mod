package de.test;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

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
            lines.add(Component.literal("Haltbarkeit: " + left + "/" + max + " (" + pct + "%)").withStyle(c));
        }
        if (RenderMods.tipFood) {
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food != null) {
                lines.add(Component.literal("N\u00e4hrwert: " + food.nutrition() + "  S\u00e4ttigung: "
                        + String.format("%.1f", food.saturation())).withStyle(ChatFormatting.GOLD));
            }
        }
        if (RenderMods.tipShulker) {
            ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
            if (contents == null) return;
            int shown = 0;
            for (var template : contents.nonEmptyItems()) {
                ItemStack st = template.create();
                if (shown++ >= RenderMods.tipShulkerLines) {
                    lines.add(Component.literal("  ...").withStyle(ChatFormatting.DARK_GRAY));
                    break;
                }
                lines.add(Component.literal("  " + st.getCount() + "x ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(st.getHoverName().copy().withStyle(ChatFormatting.AQUA)));
            }
        }
    }
}
