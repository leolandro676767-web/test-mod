package de.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Ueberleben: Auto Totem (Totem immer in der zweiten Hand) und Auto Armor (beste Ruestung anziehen).
 * Auto Totem klickt auch, waehrend eine Kiste offen ist (im Menue der Kiste); Auto Armor nur im eigenen Inventar.
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

    // Tausch per Taste fuer die zweite Hand (wenn der Server Inventar-Klicks ablehnt)
    private static boolean keyMode;
    private static int keyStage;         // 1, 2 = Totem in der Hotbar ausgewaehlt, warten, bis der Server es weiss
    private static int keyRestore = -1;
    private static int keyPending;       // nach der Taste: so lange auf die Antwort des Servers warten
    private static int popGrace;         // kurz nach einem Totem zaehlen Taeusche nicht als "abgelehnt"
    private static boolean warnedHotbar;
    private static int swapsInWindow;    // Taeusche ohne Totem-Ausloesung in den letzten 10 Sekunden
    private static int windowTicks;

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
        EquipmentSlot.FEET};
    private static final String[] SUFFIX = {"_helmet", "_chestplate", "_leggings", "_boots"};
    private static final String[] MATERIALS = {"netherite_", "diamond_", "iron_", "chainmail_", "copper_", "golden_",
        "leather_"};

    /**
     * Zu Beginn jedes Ticks - wie ein echter Tastendruck: vor dem Bewegungs-Paket des Ticks (manche Anti-Cheats
     * mögen Inventar-Pakete danach nicht).
     */
    static void totemTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || !p.isAlive()) return;
        if (autoTotem) runTotem(mc, p);
    }

    /** Am Ende jedes Ticks. */
    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.gameMode == null || !p.isAlive()) return;
        // Auto Armor nur im eigenen Inventar (ohne Menue oder mit offenem Inventar)
        if (autoArmor && p.containerMenu == p.inventoryMenu) runArmor(p);
    }

    /** Inventar-Platz (0-8 Hotbar, 9-35 Rucksack) als Slot im Inventar-Menue. */
    private static int menuSlot(int inv) {
        return inv < 9 ? 36 + inv : inv;
    }

    // ---------- Auto Totem ----------

    private static void runTotem(Minecraft mc, LocalPlayer p) {
        if (keyStage > 0) {
            keyStep(mc, p);
            return;
        }
        if (keyPending > 0) {
            // die Taste zeigt erst nach der Antwort des Servers etwas an - bis dahin nicht noch einmal druecken,
            // sonst tauscht der zweite Druck das Totem wieder zurueck
            if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                keyPending = 0;
            } else {
                keyPending--;
                return;
            }
        }
        if (++windowTicks >= 200) {
            windowTicks = 0;
            swapsInWindow = 0;
        }
        if (popGrace > 0) popGrace--;
        if (totemWait > 0) {
            totemWait--;
            return;
        }
        if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            warned = false;
            warnedHotbar = false;
            return;
        }
        boolean low = p.getHealth() + p.getAbsorptionAmount() <= totemHealth;
        if (!totemAlways && !low) return;
        // isst/benutzt gerade etwas mit der zweiten Hand: nur unterbrechen, wenn es knapp wird
        if (!low && p.isUsingItem() && p.getUsedItemHand() == InteractionHand.OFF_HAND) return;
        swap(mc, p);
    }

    /**
     * Vom Totem-Ereignis (35): das eigene Totem ist gerade verbraucht worden. Sofort nachlegen, nicht erst im
     * naechsten Tick - sonst kann ein zweiter Treffer (z.B. zwei Kristalle kurz hintereinander) toedlich sein.
     */
    static void onTotemPop(LivingEntity e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!autoTotem || p == null || e != p || mc.gameMode == null) return;
        swapsInWindow = Math.max(0, swapsInWindow - 1); // dieser Tausch ist erklaert
        // Die Antwort des Servers auf das Totem (zweite Hand leer) kommt gleich noch hinterher und ueberschreibt
        // die Anzeige - der Server schickt danach von selbst alles neu. Bis dahin nicht als "abgelehnt" zaehlen.
        popGrace = 40;
        // Minecraft nimmt zuerst das Totem aus der Haupthand; dann ist das in der zweiten Hand noch da
        if (p.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) return;
        ItemStack off = p.getOffhandItem();
        if (!off.is(Items.TOTEM_OF_UNDYING)) {
            // die Anzeige ist noch nicht auf dem neuesten Stand (z.B. Tausch per Taste noch unterwegs):
            // nicht mehr auf die alte Antwort warten, der naechste Tick legt nach
            keyPending = 0;
            totemWait = 0;
            return;
        }
        if (keyStage > 0) {
            totemWait = 0;
            return; // ein Tausch per Taste laeuft schon
        }
        // Der Server hat das Totem schon entfernt, die Anzeige hier zeigt es aber noch: erst angleichen, damit
        // der Klick genau das tut, was auch auf dem Server passiert
        if (off.getCount() > 1) {
            off.shrink(1); // (nur mit Servern, auf denen Totems stapelbar sind) - es ist noch eins da
            return;
        }
        p.getInventory().setItem(Inventory.SLOT_OFFHAND, ItemStack.EMPTY);
        totemWait = 0;
        keyPending = 0;
        swap(mc, p);
        // ...und bis diese Antwort da ist, nicht gleich noch ein zweites Totem hinterherschieben
        if (totemWait > 0) totemWait = Math.max(totemWait, Math.min(10, pingTicks(mc, p) + 2));
    }

    /** Ping in Ticks (aus der Tab-Liste), 0 wenn unbekannt. */
    private static int pingTicks(Minecraft mc, LocalPlayer p) {
        var conn = mc.getConnection();
        if (conn == null) return 0;
        var info = conn.getPlayerInfo(p.getUUID());
        return info == null ? 0 : Math.max(0, info.getLatency()) / 50;
    }

    private static int findTotem(LocalPlayer p) {
        for (int i = 9; i < 36; i++) {
            if (p.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) return i;
        }
        for (int i = 0; i < 9 && totemHotbar; i++) {
            if (p.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    /** Ein Totem in die zweite Hand legen - auch, wenn gerade eine Kiste oder ein anderes Menue offen ist. */
    private static void swap(Minecraft mc, LocalPlayer p) {
        int slot = findTotem(p);
        if (slot < 0) {
            if (totemWarn && !warned) {
                warned = true;
                p.sendSystemMessage(Lang.literal("§c1912: Auto Totem - keine Totems mehr!"));
            }
            return;
        }
        if (keyMode) {
            int hot = -1;
            for (int i = 0; i < 9 && hot < 0; i++) {
                if (p.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) hot = i;
            }
            if (hot >= 0) {
                startKeySwap(mc, p, hot);
                return;
            }
            // nur im Rucksack: trotzdem klicken (vielleicht klappt es ja doch), aber seltener
            if (!warnedHotbar) {
                warnedHotbar = true;
                p.sendSystemMessage(Lang.literal("§e1912: " + Lang.t("Auto Totem: Leg Totems in die Hotbar – "
                        + "aus dem Inventar nimmt der Server sie nicht an.")));
            }
        }
        // im gerade offenen Menue den Platz suchen, der auf diesen Inventar-Platz zeigt (eigenes Inventar: 9-44)
        var menu = p.containerMenu;
        int menuSlot = -1;
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            if (s.container == p.getInventory() && s.getContainerSlot() == slot) {
                menuSlot = i;
                break;
            }
        }
        if (menuSlot < 0 && menu == p.inventoryMenu) menuSlot = menuSlot(slot);
        if (menuSlot < 0) return;
        if (!PlayerMods.clickIn(p, menu.containerId, menuSlot, 40, "SWAP")) { // 40 = zweite Hand
            if (!keyMode) switchToKeys(p, "Auto Totem: Inventar-Klick geht hier nicht");
            return;
        }
        totemWait = keyMode ? 20 : Math.max(1, totemDelay);
        // immer wieder tauschen muessen, ohne dass ein Totem ausgeloest wurde: der Server lehnt den Klick ab
        if (popGrace == 0 && ++swapsInWindow >= 4 && !keyMode) {
            switchToKeys(p, "Auto Totem: Der Server nimmt den Inventar-Klick nicht an");
        }
    }

    private static void switchToKeys(LocalPlayer p, String why) {
        keyMode = true;
        swapsInWindow = 0;
        p.sendSystemMessage(Lang.literal("§e1912: " + Lang.t(why) + Lang.t(" – nehme jetzt die Taste für die "
                + "zweite Hand (dafür müssen Totems in der Hotbar sein).")));
    }

    /** Wie die Taste F: Totem in der Hotbar auswaehlen, tauschen, wieder zurueck auf den alten Platz. */
    private static void startKeySwap(Minecraft mc, LocalPlayer p, int hot) {
        var inv = p.getInventory();
        int prev = inv.getSelectedSlot();
        inv.setSelectedSlot(hot);
        if (sendSelectedNow(mc)) {
            // der Server kennt den Platz jetzt: sofort tauschen (der alte Platz geht im naechsten Tick raus)
            sendSwapKey(mc);
            inv.setSelectedSlot(prev);
            keyPending = 40;
            return;
        }
        // sonst wie von Hand: Minecraft schickt den Platz im naechsten Tick, erst danach tauschen
        keyRestore = prev;
        keyStage = 1;
    }

    private static void keyStep(Minecraft mc, LocalPlayer p) {
        if (keyStage == 1) {
            keyStage = 2; // sicher sein, dass dazwischen ein ganzer Tick lag (und der Platz raus ist)
            return;
        }
        if (p.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) sendSwapKey(mc);
        p.getInventory().setSelectedSlot(keyRestore); // geht nach dem Tausch raus
        keyRestore = -1;
        keyStage = 0;
        keyPending = 40;
    }

    private static void sendSwapKey(Minecraft mc) {
        var conn = mc.getConnection();
        if (conn != null) {
            conn.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ZERO, Direction.DOWN));
        }
    }

    private static java.lang.reflect.Method sendCarried;
    private static boolean sendCarriedLoaded;

    /** Schickt den gewaehlten Hotbar-Platz sofort an den Server (Minecraft macht das sonst erst im naechsten Tick). */
    private static boolean sendSelectedNow(Minecraft mc) {
        if (!sendCarriedLoaded) {
            sendCarriedLoaded = true;
            try {
                sendCarried = MultiPlayerGameMode.class.getDeclaredMethod("ensureHasSentCarriedItem");
                sendCarried.setAccessible(true);
            } catch (ReflectiveOperationException | RuntimeException e) {
                System.err.println("[1912] Auto Totem: Hotbar-Platz kann nicht sofort geschickt werden: " + e);
            }
        }
        if (sendCarried == null || mc.gameMode == null) return false;
        try {
            sendCarried.invoke(mc.gameMode);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static int totemCountNow() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null ? 0 : totems(p);
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
                note(() -> autoTotem ? Lang.t("An") + "  ·  " + Lang.t("Totems: ") + totemCountNow()
                        : Lang.t("Aus – erst unten einschalten!"));
                section("Totem");
                toggle("Immer in der zweiten Hand", () -> totemAlways, v -> totemAlways = v, true);
                slider("Sonst ab Leben ≤ (2 = 1 Herz)", 2, 20, () -> totemHealth, v -> totemHealth = v, 10);
                toggle("Auch aus der Hotbar nehmen", () -> totemHotbar, v -> totemHotbar = v, true);
                toggle("Melden, wenn keine Totems mehr da sind", () -> totemWarn, v -> totemWarn = v, true);
                slider("Verzögerung (Ticks)", 1, 20, () -> totemDelay, v -> totemDelay = v, 2);
                note("Ohne „Immer in der zweiten Hand“ bleibt dein Gegenstand in der zweiten Hand (z.B. Schild), "
                        + "bis das Leben unter die Grenze fällt. Nach einem verbrauchten Totem legt er sofort nach, "
                        + "nicht erst im nächsten Tick – auch, wenn gerade eine Kiste offen ist.");
                note("Nimmt der Server den Tausch im Inventar nicht an (manche Anti-Cheats), schreibt er das in den "
                        + "Chat und nimmt stattdessen die Taste für die zweite Hand – dafür müssen Totems in der "
                        + "Hotbar liegen. Tipp: Leg immer ein paar Totems in die Hotbar.");
                note("Wogegen kein Totem hilft: Schaden durch die Leere und /kill. Und zwei tödliche Treffer "
                        + "schneller hintereinander als dein Ping (z.B. zwei Kristalle bei 150 ms) – das nächste "
                        + "Totem ist dann noch auf dem Weg zum Server.");
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
