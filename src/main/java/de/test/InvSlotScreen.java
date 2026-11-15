package de.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Auswahl fuer einen Platz der Inventar-Aufteilung: eine Art, ein haeufiges Item oder ein beliebiges Item.
 * (Minecrafts Item-Klasse wird hier voll ausgeschrieben, weil "Item" in Form schon ein eigener Name ist.)
 */
public class InvSlotScreen extends Form {
    private final int slot;

    public InvSlotScreen(Screen parent, int slot) {
        super(Component.literal(InvLayoutScreen.slotName(slot)), parent);
        this.slot = slot;
    }

    @Override
    protected void build() {
        String cur = InvLayout.RULES[slot];
        description("Was soll hier liegen? Bei Werkzeugen nimmt er immer das beste,\n"
                + "bei Essen das nahrhafteste, sonst den größten Stapel.");
        section("Werkzeuge, Blöcke und Essen");
        for (String[] c : InvLayout.CATS) {
            wideButton(c[1], c[0].equals(cur), () -> choose(c[0]), true);
        }
        section("Gegenstände");
        for (String[] q : InvLayout.QUICK) {
            String rule = "item:" + q[0];
            wideButton(q[1], rule.equals(cur), () -> choose(rule), true);
        }
        section("Sonstiges");
        boolean other = cur != null && cur.startsWith("item:") && !isQuick(cur);
        wideButton(other ? "Anderes Item: " + InvLayout.name(cur) : "Anderes Item auswählen", other,
                this::pickItem, true);
        wideButton("Leer lassen (egal, was hier liegt)", cur == null, () -> choose(null), true);
        footer(null, null);
    }

    private static boolean isQuick(String rule) {
        for (String[] q : InvLayout.QUICK) {
            if (rule.equals("item:" + q[0])) return true;
        }
        return false;
    }

    private void choose(String rule) {
        InvLayout.RULES[slot] = rule;
        TestConfig.save();
        onClose();
    }

    /** Beliebiges Item aus der Liste aller Items (genau eins je Platz). */
    private void pickItem() {
        PickerScreen.Selection one = new PickerScreen.Selection() {
            @Override
            public boolean contains(Object value) {
                return value instanceof net.minecraft.world.item.Item it
                        && ("item:" + InvLayout.id(it)).equals(InvLayout.RULES[slot]);
            }

            @Override
            public void add(Object value) {
                if (value instanceof net.minecraft.world.item.Item it) {
                    InvLayout.RULES[slot] = "item:" + InvLayout.id(it);
                    TestConfig.save();
                }
            }

            @Override
            public void remove(Object value) {
                if (contains(value)) {
                    InvLayout.RULES[slot] = null;
                    TestConfig.save();
                }
            }

            @Override
            public List<Object> values() {
                List<Object> out = new ArrayList<>();
                net.minecraft.world.item.Item it = InvLayout.ruleItem(InvLayout.RULES[slot]);
                if (it != null) out.add(it);
                return out;
            }
        };
        this.minecraft.gui.setScreen(new PickerScreen(this.parent,
                InvLayoutScreen.slotName(slot) + ": Item wählen", PickerScreen.Kind.ITEM, one, null, null));
    }
}
