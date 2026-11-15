package de.test;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Inventar sortieren nach eigener Aufteilung: Fuer jeden Platz (Hotbar, Inventar, zweite Hand) legt man fest,
 * was dort liegen soll - eine Art (bestes Schwert, Spitzhacke, Baubloecke, Essen ...) oder ein bestimmtes Item.
 * Beim Sortieren holt er fuer jeden Platz den besten passenden Stapel und tauscht ihn dorthin.
 */
final class InvLayout {
    private InvLayout() {
    }

    /** Inventar-Index der zweiten Hand. */
    static final int OFFHAND = 40;
    /** Regel je Inventar-Platz (0-8 Hotbar, 9-35 Inventar, 40 zweite Hand); null = egal. */
    static final String[] RULES = new String[41];
    /** Vor dem Sortieren gleiche Stapel zusammenlegen. */
    static boolean mergeStacks = true;

    /** Arten: Regel, Name, Symbol. Werkzeuge: es wird immer das beste genommen. */
    static final String[][] CATS = {
        {"cat:sword", "Schwert", "minecraft:diamond_sword"},
        {"cat:pickaxe", "Spitzhacke", "minecraft:diamond_pickaxe"},
        {"cat:axe", "Axt", "minecraft:diamond_axe"},
        {"cat:shovel", "Schaufel", "minecraft:diamond_shovel"},
        {"cat:hoe", "Hacke", "minecraft:diamond_hoe"},
        {"cat:blocks", "Baublöcke (Bruchstein, Erde, Stein)", "minecraft:cobblestone"},
        {"cat:anyblock", "Irgendein Block", "minecraft:grass_block"},
        {"cat:food", "Essen", "minecraft:cooked_beef"},
    };

    /** Haeufige Items zum schnellen Auswaehlen. */
    static final String[][] QUICK = {
        {"minecraft:ender_pearl", "Enderperlen"},
        {"minecraft:water_bucket", "Wassereimer"},
        {"minecraft:lava_bucket", "Lavaeimer"},
        {"minecraft:golden_apple", "Goldene Äpfel"},
        {"minecraft:totem_of_undying", "Totem der Unsterblichkeit"},
        {"minecraft:shield", "Schild"},
        {"minecraft:bow", "Bogen"},
        {"minecraft:crossbow", "Armbrust"},
        {"minecraft:arrow", "Pfeile"},
        {"minecraft:firework_rocket", "Feuerwerksraketen"},
        {"minecraft:flint_and_steel", "Feuerzeug"},
    };

    /** Reihenfolge beim Sortieren: zuerst die Hotbar, dann zweite Hand, dann das restliche Inventar. */
    private static final int[] ORDER = new int[37];
    private static final int[] ALL = new int[37];

    static {
        int n = 0;
        for (int i = 0; i < 9; i++) ORDER[n++] = i;
        ORDER[n++] = OFFHAND;
        for (int i = 9; i < 36; i++) ORDER[n++] = i;
        for (int i = 0; i < 36; i++) ALL[i] = i;
        ALL[36] = OFFHAND;
    }

    private static final String[] TOOL_ORDER = {"netherite", "diamond", "iron", "copper", "stone", "golden", "wooden"};
    private static final Map<String, ItemStack> ICONS = new HashMap<>();

    // ---------- Regeln ----------

    static String id(Item it) {
        return BuiltInRegistries.ITEM.getKey(it).toString();
    }

    private static String path(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
    }

    static Item item(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return Items.AIR;
        return BuiltInRegistries.ITEM.getValue(key);
    }

    /** Das Item einer "item:"-Regel, sonst null. */
    static Item ruleItem(String rule) {
        return rule != null && rule.startsWith("item:") ? item(rule.substring(5)) : null;
    }

    /** Lesbarer Name einer Regel. */
    static String name(String rule) {
        if (rule == null) return Lang.t("egal");
        for (String[] c : CATS) if (c[0].equals(rule)) return Lang.t(c[1]);
        if (rule.startsWith("item:")) {
            String id = rule.substring(5);
            for (String[] q : QUICK) if (q[0].equals(id)) return Lang.t(q[1]);
            Item it = item(id);
            return it == Items.AIR ? id : icon(rule).getHoverName().getString();
        }
        return rule;
    }

    /** Symbol einer Regel (fuer die Anzeige im Raster). */
    static ItemStack icon(String rule) {
        if (rule == null) return ItemStack.EMPTY;
        return ICONS.computeIfAbsent(rule, r -> {
            String id = r.startsWith("item:") ? r.substring(5) : null;
            if (id == null) {
                for (String[] c : CATS) if (c[0].equals(r)) id = c[2];
            }
            Item it = id != null ? item(id) : Items.AIR;
            return it == Items.AIR ? ItemStack.EMPTY : it.getDefaultInstance();
        });
    }

    /** Passt der Stapel zur Regel? */
    static boolean matches(String rule, ItemStack st) {
        if (rule == null || st == null || st.isEmpty()) return false;
        switch (rule) {
            case "cat:sword":
                return path(st).endsWith("_sword");
            case "cat:pickaxe":
                return path(st).endsWith("_pickaxe");
            case "cat:axe":
                return path(st).endsWith("_axe");
            case "cat:shovel":
                return path(st).endsWith("_shovel");
            case "cat:hoe":
                return path(st).endsWith("_hoe");
            case "cat:blocks":
                return st.getItem() instanceof BlockItem bi && TestClient.isBuildBlock(bi.getBlock());
            case "cat:anyblock":
                return st.getItem() instanceof BlockItem;
            case "cat:food":
                return st.get(DataComponents.FOOD) != null;
            default:
                return rule.startsWith("item:") && id(st.getItem()).equals(rule.substring(5));
        }
    }

    /** Wie gut passt der Stapel (hoeher = besser): Werkzeuge nach Material, Essen nach Naehrwert, sonst Menge. */
    static double rank(String rule, ItemStack st) {
        if (rule.equals("cat:sword") || rule.equals("cat:pickaxe") || rule.equals("cat:axe")
                || rule.equals("cat:shovel") || rule.equals("cat:hoe")) {
            String p = path(st);
            double r = 0;
            for (int i = 0; i < TOOL_ORDER.length; i++) {
                if (p.startsWith(TOOL_ORDER[i])) {
                    r = (TOOL_ORDER.length - i) * 10.0;
                    break;
                }
            }
            if (st.isEnchanted()) r += 5;
            int max = st.getMaxDamage();
            if (max > 0) r += 2.0 * (max - st.getDamageValue()) / max;
            return r;
        }
        if (rule.equals("cat:food")) {
            FoodProperties food = st.get(DataComponents.FOOD);
            return (food != null ? food.nutrition() * 100.0 : 0) + st.getCount();
        }
        return st.getCount();
    }

    // ---------- Aufteilung festlegen ----------

    /** Uebernimmt, was gerade im Inventar liegt, als Aufteilung. */
    static void fromInventory(LocalPlayer p) {
        if (p == null) return;
        Inventory inv = p.getInventory();
        for (int slot : ALL) RULES[slot] = guess(inv.getItem(slot));
    }

    private static String guess(ItemStack st) {
        if (st.isEmpty()) return null;
        String id = id(st.getItem());
        for (String[] q : QUICK) if (q[0].equals(id)) return "item:" + id;
        for (int i = 0; i < 5; i++) if (matches(CATS[i][0], st)) return CATS[i][0];
        if (matches("cat:blocks", st)) return "cat:blocks";
        if (matches("cat:food", st)) return "cat:food";
        return "item:" + id;
    }

    /** Ein guter Startpunkt: Werkzeuge vorne, Bloecke, Wasser, Perlen, Aepfel, Essen. */
    static void suggestion() {
        java.util.Arrays.fill(RULES, null);
        RULES[0] = "cat:sword";
        RULES[1] = "cat:pickaxe";
        RULES[2] = "cat:axe";
        RULES[3] = "cat:shovel";
        RULES[4] = "cat:blocks";
        RULES[5] = "item:minecraft:water_bucket";
        RULES[6] = "item:minecraft:ender_pearl";
        RULES[7] = "item:minecraft:golden_apple";
        RULES[8] = "cat:food";
    }

    static boolean isEmpty() {
        for (String r : RULES) if (r != null) return false;
        return true;
    }

    // ---------- Speichern ----------

    static void load(Properties p) {
        java.util.Arrays.fill(RULES, null);
        mergeStacks = Boolean.parseBoolean(p.getProperty("invMerge", "true"));
        String s = p.getProperty("invLayout", "");
        for (String part : s.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            try {
                int slot = Integer.parseInt(part.substring(0, eq).trim());
                String rule = part.substring(eq + 1).trim();
                if (slot >= 0 && slot < RULES.length && (slot < 36 || slot == OFFHAND) && !rule.isEmpty()) {
                    RULES[slot] = rule;
                }
            } catch (NumberFormatException e) {
                // kaputter Eintrag: ignorieren
            }
        }
    }

    static void save(Properties p) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < RULES.length; i++) {
            if (RULES[i] == null) continue;
            if (sb.length() > 0) sb.append(';');
            sb.append(i).append('=').append(RULES[i]);
        }
        p.setProperty("invLayout", sb.toString());
        p.setProperty("invMerge", Boolean.toString(mergeStacks));
    }

    // ---------- Sortieren ----------

    private static final int MAX_TICKS = 60;
    private static final int SWAPS_PER_TICK = 4;
    private static int ticksLeft;
    private static int moved;
    private static boolean merged;

    /** Taste "Inventar sortieren": startet das Sortieren (laeuft ueber ein paar Ticks). */
    static void request(Minecraft mc) {
        if (mc.player == null) return;
        if (isEmpty()) {
            Keybinds.message("Erst die Aufteilung festlegen", "Hauptmenü: Inventar sortieren, Punkte");
            return;
        }
        ticksLeft = MAX_TICKS;
        moved = 0;
        merged = !mergeStacks;
    }

    static boolean running() {
        return ticksLeft > 0;
    }

    static void tick(Minecraft mc) {
        if (ticksLeft <= 0) return;
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null) {
            ticksLeft = 0;
            return;
        }
        // nur im eigenen Inventar (keine Truhe offen) und mit leerem Mauszeiger
        if (p.containerMenu != p.inventoryMenu) {
            ticksLeft = 0;
            Keybinds.message("Inventar nicht sortiert", "Erst Truhe/Menü schließen");
            return;
        }
        ticksLeft--;
        if (!p.inventoryMenu.getCarried().isEmpty()) return;
        if (!merged) {
            merged = true;
            mergeStacks(p);
            return;
        }
        for (int n = 0; n < SWAPS_PER_TICK; n++) {
            int[] sw = nextSwap(p.getInventory());
            if (sw == null) {
                ticksLeft = 0;
                Keybinds.message(moved == 0 ? "Inventar ist schon sortiert" : "Inventar sortiert",
                        moved == 0 ? "" : moved + " Stapel verschoben");
                return;
            }
            swap(p, sw[0], sw[1]);
            moved++;
        }
        if (ticksLeft == 0) Keybinds.message("Inventar sortiert", "nicht alles ließ sich umräumen");
    }

    /**
     * Naechster noetiger Tausch {Zielplatz, Quellplatz} oder null, wenn alles passt. Die Plaetze werden der
     * Reihe nach festgelegt; ein festgelegter Platz gibt nichts mehr ab (zwei Schwert-Plaetze: bestes, zweitbestes).
     */
    private static int[] nextSwap(Inventory inv) {
        boolean[] done = new boolean[41];
        for (int slot : ORDER) {
            String rule = RULES[slot];
            if (rule == null) continue;
            int best = -1;
            double bestRank = -1;
            for (int s : ALL) {
                if (done[s]) continue;
                ItemStack st = inv.getItem(s);
                if (!matches(rule, st)) continue;
                double r = rank(rule, st) + (s == slot ? 0.001 : 0.0); // gleich gut: liegen lassen
                if (r > bestRank) {
                    bestRank = r;
                    best = s;
                }
            }
            if (best < 0) continue; // nichts Passendes da: Platz bleibt, wie er ist
            done[slot] = true;
            if (best != slot) return new int[] {slot, best};
        }
        return null;
    }

    /** Menue-Slot im Spieler-Inventar: Hotbar 36-44, Inventar 9-35, zweite Hand 45. */
    private static int menuSlot(int inv) {
        if (inv < 9) return 36 + inv;
        return inv == OFFHAND ? 45 : inv;
    }

    /** Tauscht zwei Plaetze (mit der Hotbar und der zweiten Hand in einem Klick, sonst ueber den Mauszeiger). */
    private static void swap(LocalPlayer p, int a, int b) {
        if (b < 9) {
            PlayerMods.click(p, menuSlot(a), b, "SWAP");
        } else if (a < 9) {
            PlayerMods.click(p, menuSlot(b), a, "SWAP");
        } else if (b == OFFHAND) {
            PlayerMods.click(p, menuSlot(a), OFFHAND, "SWAP");
        } else if (a == OFFHAND) {
            PlayerMods.click(p, menuSlot(b), OFFHAND, "SWAP");
        } else {
            PlayerMods.click(p, menuSlot(a), 0, "PICKUP");
            PlayerMods.click(p, menuSlot(b), 0, "PICKUP");
            if (!p.inventoryMenu.getCarried().isEmpty()) PlayerMods.click(p, menuSlot(a), 0, "PICKUP");
        }
    }

    /** Angefangene Stapel desselben Items zusammenlegen (nach vorne). */
    private static void mergeStacks(LocalPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            for (int j = i + 1; j < 36; j++) {
                ItemStack a = inv.getItem(i);
                if (a.isEmpty() || !a.isStackable() || a.getCount() >= a.getMaxStackSize()) break;
                ItemStack b = inv.getItem(j);
                if (b.isEmpty() || !ItemStack.isSameItemSameComponents(a, b)) continue;
                PlayerMods.publicClick(p, j, i);
            }
        }
    }
}
