package de.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Ueberleben: Auto Totem (Totem immer in der zweiten Hand) und Auto Armor (beste Ruestung anziehen).
 * Beide klicken nur im eigenen Inventar - nie, waehrend eine Kiste oder ein anderes Menue offen ist.
 */
public final class Survival {
    private Survival() {
    }

    // ---------- Auto Totem (gespeichert ueber TestConfig, Praefix "sv.") ----------

    public static boolean autoTotem = false;
    public static boolean totemAlways = true;      // immer ein Totem in der zweiten Hand
    public static int totemHealth = 10;            // sonst erst ab so wenig Leben (2 = 1 Herz)
    public static boolean totemHotbar = true;      // auch Totems aus der Hotbar nehmen
    public static boolean totemWarn = true;        // melden, wenn keine Totems mehr da sind
    public static int totemDelay = 2;              // Ticks zwischen zwei Versuchen

    // ---------- Auto Armor ----------

    public static boolean autoArmor = false;
    public static boolean armorKeepElytra = true;  // angezogene Elytra nicht gegen einen Brustpanzer tauschen
    public static boolean armorEnchanted = true;   // verzauberte Teile vorziehen
    public static int armorDelay = 5;

    private static int totemWait;
    private static boolean warned;
    private static int armorWait;

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
        EquipmentSlot.FEET};
    private static final String[] SUFFIX = {"_helmet", "_chestplate", "_leggings", "_boots"};
    private static final String[] MATERIALS = {"netherite_", "diamond_", "iron_", "chainmail_", "copper_", "golden_",
        "leather_"};

    /** Jeden Tick (vor den Tasten). */
    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || !p.isAlive()) return;
        // nur im eigenen Inventar klicken (ohne Menue oder mit offenem Inventar)
        if (p.containerMenu != p.inventoryMenu) return;
        if (autoTotem) runTotem(p);
        if (autoArmor) runArmor(p);
    }

    /** Inventar-Platz (0-8 Hotbar, 9-35 Rucksack) als Slot im Inventar-Menue. */
    private static int menuSlot(int inv) {
        return inv < 9 ? 36 + inv : inv;
    }

    // ---------- Auto Totem ----------

    private static void runTotem(LocalPlayer p) {
        if (totemWait > 0) {
            totemWait--;
            return;
        }
        if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            warned = false;
            return;
        }
        boolean low = p.getHealth() + p.getAbsorptionAmount() <= totemHealth;
        if (!totemAlways && !low) return;
        // isst/benutzt gerade etwas mit der zweiten Hand: nur unterbrechen, wenn es knapp wird
        if (!low && p.isUsingItem() && p.getUsedItemHand() == InteractionHand.OFF_HAND) return;
        int slot = -1;
        for (int i = 9; i < 36 && slot < 0; i++) {
            if (p.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) slot = i;
        }
        for (int i = 0; i < 9 && slot < 0 && totemHotbar; i++) {
            if (p.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) slot = i;
        }
        if (slot < 0) {
            if (totemWarn && !warned) {
                warned = true;
                p.sendSystemMessage(Lang.literal("§c1912: Auto Totem - keine Totems mehr!"));
            }
            return;
        }
        PlayerMods.click(p, menuSlot(slot), 40, "SWAP"); // 40 = zweite Hand
        totemWait = Math.max(1, totemDelay);
    }

    /** Totems im Inventar und in der zweiten Hand (fuer die Info-Anzeige). */
    static int totems(LocalPlayer p) {
        int n = p.getOffhandItem().is(Items.TOTEM_OF_UNDYING) ? p.getOffhandItem().getCount() : 0;
        for (int i = 0; i < 36; i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (st.is(Items.TOTEM_OF_UNDYING)) n += st.getCount();
        }
        return n;
    }

    // ---------- Auto Armor ----------

    /**
     * Wert eines Ruestungsteils fuer den Platz k (0 Kopf .. 3 Fuesse); -1 = passt nicht.
     * Material zuerst, dann verzaubert, dann die restliche Haltbarkeit.
     */
    static int armorScore(ItemStack st, int k) {
        if (st.isEmpty()) return -1;
        String id = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        int rank;
        if (k == 0 && id.equals("turtle_helmet")) {
            rank = 5; // so stark wie Eisen
        } else {
            if (!id.endsWith(SUFFIX[k])) return -1;
            rank = 0;
            for (int i = 0; i < MATERIALS.length; i++) {
                if (id.startsWith(MATERIALS[i])) {
                    rank = MATERIALS.length - i;
                    break;
                }
            }
        }
        int score = rank * 1000;
        if (armorEnchanted && st.isEnchanted()) score += 500;
        int max = st.getMaxDamage();
        if (max > 0) score += (max - st.getDamageValue()) * 100 / max;
        return score;
    }

    private static void runArmor(LocalPlayer p) {
        if (armorWait > 0) {
            armorWait--;
            return;
        }
        for (int k = 0; k < 4; k++) {
            ItemStack worn = p.getItemBySlot(SLOTS[k]);
            if (k == 1 && armorKeepElytra && worn.is(Items.ELYTRA)) continue;
            int wornScore = armorScore(worn, k);
            // etwas anderes auf dem Kopf (Kuerbis, Kopf) oder eine Elytra: nicht anfassen
            if (!worn.isEmpty() && wornScore < 0 && !(k == 1 && worn.is(Items.ELYTRA))) continue;
            int best = -1;
            int bestScore = wornScore;
            for (int i = 0; i < 36; i++) {
                int s = armorScore(p.getInventory().getItem(i), k);
                if (s > bestScore) {
                    bestScore = s;
                    best = i;
                }
            }
            if (best < 0) continue;
            // aufnehmen, auf den Ruestungsplatz legen (das alte Teil haengt dann am Mauszeiger), zuruecklegen
            PlayerMods.click(p, menuSlot(best), 0, "PICKUP");
            PlayerMods.click(p, 5 + k, 0, "PICKUP");
            PlayerMods.click(p, menuSlot(best), 0, "PICKUP");
            armorWait = Math.max(1, armorDelay);
            return; // ein Teil pro Durchgang
        }
    }

    // ---------- Menues ----------

    static Screen totemScreen(Screen parent) {
        return new Form(Component.literal("Auto Totem"), parent) {
            @Override
            protected void build() {
                description("Hält immer ein Totem der Unsterblichkeit in der zweiten Hand. Nach einem verbrauchten "
                        + "Totem kommt sofort das nächste nach.");
                section("Totem");
                toggle("Immer in der zweiten Hand", () -> totemAlways, v -> totemAlways = v, true);
                slider("Sonst ab Leben ≤ (2 = 1 Herz)", 2, 20, () -> totemHealth, v -> totemHealth = v, 10);
                toggle("Auch aus der Hotbar nehmen", () -> totemHotbar, v -> totemHotbar = v, true);
                toggle("Melden, wenn keine Totems mehr da sind", () -> totemWarn, v -> totemWarn = v, true);
                slider("Verzögerung (Ticks)", 1, 20, () -> totemDelay, v -> totemDelay = v, 2);
                note("Ohne „Immer in der zweiten Hand“ bleibt dein Gegenstand in der zweiten Hand (z.B. Schild), "
                        + "bis das Leben unter die Grenze fällt. In Kisten und anderen Menüs tauscht er nicht.");
                footer(() -> autoTotem, v -> autoTotem = v);
            }
        };
    }

    static Screen armorScreen(Screen parent) {
        return new Form(Component.literal("Auto Armor"), parent) {
            @Override
            protected void build() {
                description("Zieht automatisch die beste Rüstung aus deinem Inventar an.");
                section("Rüstung");
                toggle("Angezogene Elytra behalten", () -> armorKeepElytra, v -> armorKeepElytra = v, true);
                toggle("Verzauberte Teile vorziehen", () -> armorEnchanted, v -> armorEnchanted = v, true);
                slider("Verzögerung (Ticks)", 1, 40, () -> armorDelay, v -> armorDelay = v, 5);
                note("Reihenfolge: Netherit, Diamant, Eisen (und Schildkrötenpanzer), Kette, Kupfer, Gold, Leder. "
                        + "Bei gleichem Material gewinnt das verzauberte, dann das weniger abgenutzte Teil. "
                        + "Kürbisse und Köpfe auf dem Kopf bleiben, wo sie sind.");
                footer(() -> autoArmor, v -> autoArmor = v);
            }
        };
    }
}
