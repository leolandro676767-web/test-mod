package de.test;

import java.util.List;

import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.item.ItemStack;

/**
 * Misc-Module (Kategorie "Misc"). "Offhand Crash" ist bewusst nicht enthalten - das ist ein
 * Absturz-Exploit gegen andere Clients/Server. Discord Presence, Notebot und Swarm brauchen
 * zusaetzliche Bibliotheken bzw. mehrere Konten und sind nur als Schalter vorhanden.
 */
public final class MiscMods {
    private MiscMods() {
    }

    // ---------- Schalter + Einstellungen ----------

    public static boolean antiPacketKick = false;
    public static int antiPacketKickMax = 20;       // max. ausgehende Pakete pro Tick

    public static boolean autoReconnect = false;
    public static int autoReconnectDelay = 5;       // Sekunden

    public static boolean betterBeacons = false;

    public static boolean betterChat = false;
    public static boolean betterChatDedup = true;   // gleiche Nachricht buendeln (x2, x3)
    public static boolean betterChatTime = false;   // Uhrzeit davor
    public static int betterChatWindow = 10;        // Sekunden, in denen Wiederholungen ausgeblendet werden

    public static boolean bookBot = false;          // einmalig: schreibt das Buch und schaltet sich aus
    public static String bookBotText = "1912 Client";
    public static int bookBotPages = 50;
    public static boolean bookBotSign = false;
    public static String bookBotTitle = "1912";

    public static boolean discordPresence = false;

    public static boolean inventoryTweaks = false;
    public static boolean sortHotbar = false;

    public static boolean messageAura = false;
    public static int messageAuraRange = 8;
    public static int messageAuraDelay = 60;        // Sekunden pro Spieler
    public static String messageAuraText = "Hi %name%!";

    public static boolean notebot = false;

    public static boolean notifier = false;
    public static boolean notifyDurability = true;
    public static int notifyDurabilityAt = 10;      // Prozent
    public static boolean notifyTotem = true;

    public static boolean packetCanceller = false;
    public static boolean cancelSwing = false;
    public static boolean cancelInteract = false;
    public static boolean cancelAction = false;
    public static boolean cancelMove = false;

    public static boolean serverSpoof = false;
    public static String serverSpoofBrand = "vanilla";

    public static boolean soundBlocker = false;

    public static boolean spam = false;
    public static int spamDelay = 5;                // Sekunden
    public static String spamText = "1912 Client";

    public static boolean swarm = false;

    // ---------- Zustand ----------

    private static int packetsThisTick = 0;
    private static int reconnectTicks = 0;
    private static ServerData lastServer = null;
    private static int spamTicks = 0;
    private static boolean durabilityWarned = false;
    private static boolean lastHadTotem = false;
    private static String lastChat = "";
    private static long lastChatTime = 0;
    private static final java.util.Map<String, Integer> auraCooldown = new java.util.HashMap<>();

    /** Better Chat: dieselbe Nachricht innerhalb des Zeitfensters nur einmal zeigen. */
    private static boolean allowRepeat(Component message) {
        if (!betterChat || !betterChatDedup) return true;
        String text = message.getString();
        long now = System.currentTimeMillis();
        boolean repeat = text.equals(lastChat) && now - lastChatTime < betterChatWindow * 1000L;
        lastChat = text;
        lastChatTime = now;
        return !repeat;
    }

    public static void register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || allowRepeat(message));
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, time) -> allowRepeat(message));
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            if (betterChat && betterChatTime && !overlay) {
                String t = java.time.LocalTime.now().toString().substring(0, 5);
                return Component.literal("§7[" + t + "] §r").append(message);
            }
            return message;
        });
    }

    /** Server Spoof: die Client-Marke beim Verbinden austauschen (vom ConnectionMixin). */
    public static Packet<?> transform(Packet<?> packet) {
        if (serverSpoof && packet instanceof net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket cp
                && cp.payload() instanceof net.minecraft.network.protocol.common.custom.BrandPayload) {
            String brand = serverSpoofBrand == null || serverSpoofBrand.isBlank() ? "vanilla" : serverSpoofBrand.strip();
            return new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                    new net.minecraft.network.protocol.common.custom.BrandPayload(brand));
        }
        return packet;
    }

    /** Book Bot: Buch und Feder in der Hand beschreiben (und optional signieren). */
    private static void runBookBot(Minecraft mc) {
        var p = mc.player;
        if (!p.getMainHandItem().is(net.minecraft.world.item.Items.WRITABLE_BOOK)) {
            p.sendSystemMessage(Component.literal("\u00a7c1912: Book Bot - erst Buch und Feder in die Hand nehmen"));
            return;
        }
        String text = bookBotText == null || bookBotText.isBlank() ? "1912 Client" : bookBotText;
        if (text.length() > 1024) text = text.substring(0, 1024);
        java.util.List<String> pages = new java.util.ArrayList<>();
        for (int i = 0; i < Math.max(1, Math.min(100, bookBotPages)); i++) pages.add(text);
        java.util.Optional<String> title = java.util.Optional.empty();
        if (bookBotSign) {
            String t = bookBotTitle == null || bookBotTitle.isBlank() ? "1912" : bookBotTitle.strip();
            title = java.util.Optional.of(t.length() > 32 ? t.substring(0, 32) : t);
        }
        int slot = p.getInventory().getSelectedSlot();
        mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundEditBookPacket(slot, pages, title));
        p.sendSystemMessage(Component.literal("\u00a7a1912: Book Bot - " + pages.size() + " Seiten geschrieben"
                + (bookBotSign ? " und signiert" : "")));
    }

    /** Vom ConnectionMixin ueber PlayerMods.onSend. true = Paket nicht senden. */
    public static boolean onSend(Packet<?> packet) {
        if (packetCanceller) {
            if (cancelSwing && packet instanceof ServerboundSwingPacket) return true;
            if (cancelInteract && packet instanceof ServerboundInteractPacket) return true;
            if (cancelAction && packet instanceof ServerboundPlayerActionPacket) return true;
            if (cancelMove && packet instanceof ServerboundMovePlayerPacket) return true;
        }
        if (antiPacketKick) {
            if (packetsThisTick >= antiPacketKickMax && packet instanceof ServerboundMovePlayerPacket) return true;
            packetsThisTick++;
        }
        return false;
    }

    // ---------- Tick ----------

    public static void tick(Minecraft mc) {
        packetsThisTick = 0;
        if (mc.getCurrentServer() != null) lastServer = mc.getCurrentServer();

        if (autoReconnect) runAutoReconnect(mc);
        else reconnectTicks = 0;

        if (mc.player == null) return;

        if (spam && mc.gui.screen() == null) {
            if (++spamTicks >= spamDelay * 20) {
                spamTicks = 0;
                sendChat(mc, spamText);
            }
        }
        if (notifier) runNotifier(mc);
        if (bookBot) {
            bookBot = false;
            if (mc.getConnection() != null) runBookBot(mc);
        }
        if (messageAura) runMessageAura(mc);
        if (inventoryTweaks) {
            while (TestClient.sortKey != null && TestClient.sortKey.consumeClick()) sortInventory(mc);
        }
    }

    private static void sendChat(Minecraft mc, String text) {
        if (mc.getConnection() == null || text == null || text.isBlank()) return;
        String msg = text.strip();
        if (msg.startsWith("/")) {
            mc.getConnection().sendCommand(msg.substring(1));
        } else {
            mc.getConnection().sendChat(msg);
        }
    }

    private static void runAutoReconnect(Minecraft mc) {
        Screen current = mc.gui.screen();
        if (!(current instanceof DisconnectedScreen)) {
            reconnectTicks = 0;
            return;
        }
        if (lastServer == null) return;
        if (++reconnectTicks < autoReconnectDelay * 20) return;
        reconnectTicks = 0;
        try {
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                    current, mc, net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(lastServer.ip),
                    lastServer, false, null);
        } catch (RuntimeException e) {
            System.err.println("[1912] Auto Reconnect fehlgeschlagen: " + e);
            autoReconnect = false;
        }
    }

    private static void runNotifier(Minecraft mc) {
        var p = mc.player;
        if (notifyDurability) {
            ItemStack main = p.getMainHandItem();
            if (main.isDamageableItem()) {
                int left = main.getMaxDamage() - main.getDamageValue();
                int pct = left * 100 / main.getMaxDamage();
                if (pct <= notifyDurabilityAt && !durabilityWarned) {
                    durabilityWarned = true;
                    p.sendSystemMessage(Component.literal("§e1912: Werkzeug fast kaputt (" + pct + "%)"));
                } else if (pct > notifyDurabilityAt) {
                    durabilityWarned = false;
                }
            }
        }
        if (notifyTotem) {
            boolean hasTotem = p.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)
                    || p.getMainHandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
            if (lastHadTotem && !hasTotem) {
                p.sendSystemMessage(Component.literal("§c1912: Totem verbraucht!"));
            }
            lastHadTotem = hasTotem;
        }
    }

    private static void runMessageAura(Minecraft mc) {
        for (var other : mc.level.players()) {
            if (other == mc.player) continue;
            if (other.distanceTo(mc.player) > messageAuraRange) continue;
            String name = other.getName().getString();
            int cd = auraCooldown.getOrDefault(name, 0);
            if (cd > 0) {
                auraCooldown.put(name, cd - 1);
                continue;
            }
            auraCooldown.put(name, messageAuraDelay * 20);
            sendChat(mc, messageAuraText.replace("%name%", name));
            return; // einer pro Tick
        }
    }

    private static void sortInventory(Minecraft mc) {
        // Einfaches Sortieren: gleiche Items im Hauptinventar zusammenlegen
        if (mc.player == null || mc.gameMode == null) return;
        var inv = mc.player.getInventory();
        int start = sortHotbar ? 0 : 9;
        for (int i = start; i < 36; i++) {
            ItemStack a = inv.getItem(i);
            if (a.isEmpty() || !a.isStackable() || a.getCount() >= a.getMaxStackSize()) continue;
            for (int j = i + 1; j < 36; j++) {
                ItemStack b = inv.getItem(j);
                if (!b.isEmpty() && ItemStack.isSameItemSameComponents(a, b)) {
                    PlayerMods.publicClick(mc.player, j, i);
                }
            }
        }
    }

    // ---------- Menues ----------

    public static Screen screen(String module, Screen parent) {
        return switch (module) {
            case "Anti Packet Kick" -> new ModuleScreen(parent, module, f -> {
                f.description("Begrenzt ausgehende Pakete, um „too many\npackets“-Kicks zu vermeiden.");
                f.section("Allgemein");
                f.slider("Max. Pakete/Tick", 5, 60, () -> antiPacketKickMax, v -> antiPacketKickMax = v, 20);
                f.footer(() -> antiPacketKick, v -> antiPacketKick = v);
            });
            case "Auto Reconnect" -> new ModuleScreen(parent, module, f -> {
                f.description("Verbindet nach einem Rauswurf automatisch\nneu zum letzten Server.");
                f.section("Allgemein");
                f.slider("Verzögerung (s)", 1, 60, () -> autoReconnectDelay, v -> autoReconnectDelay = v, 5);
                f.footer(() -> autoReconnect, v -> autoReconnect = v);
            });
            case "Better Beacons" -> flag(parent, module, "Leuchtfeuer-Strahl überall sichtbar.\n(Braucht Mixin - evtl. wirkungslos.)",
                    () -> betterBeacons, v -> betterBeacons = v);
            case "Better Chat" -> new ModuleScreen(parent, module, f -> {
                f.description("Verbessert den Chat.");
                f.section("Allgemein");
                f.toggle("Uhrzeit davor", () -> betterChatTime, v -> betterChatTime = v, false);
                f.toggle("Wiederholungen ausblenden", () -> betterChatDedup, v -> betterChatDedup = v, true);
                f.slider("Zeitfenster (s)", 1, 60, () -> betterChatWindow, v -> betterChatWindow = v, 10);
                f.footer(() -> betterChat, v -> betterChat = v);
            });
            case "Book Bot" -> new ModuleScreen(parent, module, f -> {
                f.description("Beschreibt Buch und Feder in deiner Hand\nund schaltet sich danach wieder aus.");
                f.section("Inhalt");
                f.text("Text je Seite", () -> bookBotText, v -> bookBotText = v, "1912 Client");
                f.slider("Seiten", 1, 100, () -> bookBotPages, v -> bookBotPages = v, 50);
                f.section("Signieren");
                f.toggle("Buch signieren", () -> bookBotSign, v -> bookBotSign = v, false);
                f.text("Titel", () -> bookBotTitle, v -> bookBotTitle = v, "1912");
                f.footer(() -> bookBot, v -> bookBot = v);
            });
            case "Discord Presence" -> flag(parent, module, "Zeigt den Status in Discord an.\n(Braucht extra Bibliothek - nicht umgesetzt.)",
                    () -> discordPresence, v -> discordPresence = v);
            case "Inventory Tweaks" -> new ModuleScreen(parent, module, f -> {
                f.description("Legt gleiche Items auf Tastendruck zusammen\n(Taste in \u201eTasten \u00e4ndern\u201c).");
                f.section("Allgemein");
                f.toggle("Hotbar einbeziehen", () -> sortHotbar, v -> sortHotbar = v, false);
                f.footer(() -> inventoryTweaks, v -> inventoryTweaks = v);
            });
            case "Message Aura" -> new ModuleScreen(parent, module, f -> {
                f.description("Schreibt Spielern in der Nähe automatisch.\n%name% wird ersetzt.");
                f.section("Allgemein");
                f.text("Text", () -> messageAuraText, v -> messageAuraText = v, "Hi %name%!");
                f.slider("Reichweite", 2, 32, () -> messageAuraRange, v -> messageAuraRange = v, 8);
                f.slider("Pause je Spieler (s)", 5, 300, () -> messageAuraDelay, v -> messageAuraDelay = v, 60);
                f.footer(() -> messageAura, v -> messageAura = v);
            });
            case "Notebot" -> flag(parent, module, "Spielt Lieder auf Notenblöcken.\n(Braucht Lieddateien - nicht umgesetzt.)",
                    () -> notebot, v -> notebot = v);
            case "Notifier" -> new ModuleScreen(parent, module, f -> {
                f.description("Meldet wichtige Ereignisse im Chat.");
                f.section("Allgemein");
                f.toggle("Haltbarkeit", () -> notifyDurability, v -> notifyDurability = v, true);
                f.slider("Warnen bei (%)", 1, 50, () -> notifyDurabilityAt, v -> notifyDurabilityAt = v, 10);
                f.toggle("Totem verbraucht", () -> notifyTotem, v -> notifyTotem = v, true);
                f.footer(() -> notifier, v -> notifier = v);
            });
            case "Packet Canceller" -> new ModuleScreen(parent, module, f -> {
                f.description("Blockiert gewählte ausgehende Pakete.\nVorsicht: kann dich vom Server trennen.");
                f.section("Blockieren");
                f.toggle("Schlagen (Swing)", () -> cancelSwing, v -> cancelSwing = v, false);
                f.toggle("Interagieren", () -> cancelInteract, v -> cancelInteract = v, false);
                f.toggle("Block-Aktion", () -> cancelAction, v -> cancelAction = v, false);
                f.toggle("Bewegung", () -> cancelMove, v -> cancelMove = v, false);
                f.footer(() -> packetCanceller, v -> packetCanceller = v);
            });
            case "Server Spoof" -> new ModuleScreen(parent, module, f -> {
                f.description("Meldet dem Server beim Verbinden eine\nandere Client-Marke (z.B. vanilla).\nGilt ab der n\u00e4chsten Verbindung.");
                f.section("Allgemein");
                f.text("Marke", () -> serverSpoofBrand, v -> serverSpoofBrand = v, "vanilla");
                f.footer(() -> serverSpoof, v -> serverSpoof = v);
            });
            case "Sound Blocker" -> flag(parent, module, "Stummt gewählte Geräusche.\n(Braucht Mixin - evtl. wirkungslos.)",
                    () -> soundBlocker, v -> soundBlocker = v);
            case "Spam" -> new ModuleScreen(parent, module, f -> {
                f.description("Schreibt wiederholt eine Nachricht.\nBeginnt mit / für Befehle.");
                f.section("Allgemein");
                f.text("Text", () -> spamText, v -> spamText = v, "1912 Client");
                f.slider("Abstand (s)", 1, 60, () -> spamDelay, v -> spamDelay = v, 5);
                f.footer(() -> spam, v -> spam = v);
            });
            case "Swarm" -> flag(parent, module, "Steuert mehrere Konten gleichzeitig.\n(Nicht umgesetzt.)",
                    () -> swarm, v -> swarm = v);
            default -> null;
        };
    }

    private static Screen flag(Screen parent, String module, String desc,
                               java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> set) {
        return new ModuleScreen(parent, module, f -> {
            f.description(desc);
            f.footer(get, set);
        });
    }
}
