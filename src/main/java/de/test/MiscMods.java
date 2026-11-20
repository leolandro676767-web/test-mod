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
 * Absturz-Exploit gegen andere Clients/Server. Discord Presence, Notebot und Swarm liegen in
 * eigenen Klassen (DiscordIpc, Notebot, Swarm/SwarmNet).
 */
public final class MiscMods {
    private MiscMods() {
    }

    // ---------- Schalter + Einstellungen ----------

    public static boolean antiPacketKick = false;
    public static int antiPacketKickMax = 20;       // max. ausgehende Pakete pro Tick

    public static boolean autoReconnect = false;
    public static int autoReconnectDelay = 5;       // Sekunden
    public static int autoReconnectMax = 0;         // hoechstens so viele Versuche hintereinander, 0 = unbegrenzt
    public static boolean autoReconnectBackoff = false; // nach jedem Fehlversuch laenger warten
    public static int autoReconnectAfterLogout = 0; // Minuten nach einem Auto-Logout doch neu verbinden, 0 = nie

    public static boolean betterBeacons = false;
    public static int beaconPrimary = 4;     // Index in BEACON_NAMES (Staerke)
    public static int beaconSecondary = 1;   // 0 = keiner, 1 = Regeneration, 2 = Hauptwirkung verstaerken

    public static boolean betterChat = false;
    public static boolean betterChatDedup = true;   // gleiche Nachricht buendeln (x2, x3)
    public static boolean betterChatTime = false;   // Uhrzeit davor
    public static int betterChatWindow = 10;        // Sekunden, in denen Wiederholungen ausgeblendet werden
    public static boolean betterChatFriends = false; // Nachrichten von Freunden hervorheben
    public static String betterChatFilter = "";     // Woerter, mit Komma getrennt: solche Nachrichten ausblenden

    public static boolean bookBot = false;          // einmalig: schreibt das Buch und schaltet sich aus
    public static String bookBotText = "1912 Utilities";
    public static int bookBotPages = 50;
    public static boolean bookBotSign = false;
    public static String bookBotTitle = "1912";

    public static boolean discordPresence = false;
    public static String discordAppId = "";           // Application ID der eigenen Discord-Anwendung
    public static String discordText = "1912 Utilities"; // zweite Zeile
    public static boolean discordShowServer = false;

    public static boolean inventoryTweaks = false;

    public static boolean messageAura = false;
    public static int messageAuraChunks = 6;        // Reichweite in Chunks
    public static int messageAuraDelay = 60;        // Sekunden pro Spieler
    public static String messageAuraText = "Hi %name%!";
    public static int messageAuraWho = 0;            // 0 = alle, 1 = nur Freunde, 2 = keine Freunde
    static final String[] MESSAGE_AURA_WHO = {"Alle", "Nur Freunde", "Keine Freunde"};

    public static boolean notebot = false;
    public static int notebotSong = 0;
    public static int notebotTempo = 4;      // Ticks je Schritt
    public static int notebotTuneDelay = 2;  // Ticks zwischen zwei Stimm-Klicks
    public static boolean notebotLoop = false;

    public static boolean notifier = false;
    public static boolean notifyDurability = true;
    public static int notifyDurabilityAt = 10;      // Prozent
    public static boolean notifyTotem = true;
    public static boolean notifyHealth = false;
    public static int notifyHealthAt = 6;           // Lebenspunkte (2 = 1 Herz)
    public static boolean notifyFullInv = false;
    public static boolean notifyPlayers = false;    // Spieler (keine Freunde) kommen in Sichtweite

    public static boolean packetCanceller = false;
    public static boolean cancelSwing = false;
    public static boolean cancelInteract = false;
    public static boolean cancelAction = false;
    public static boolean cancelMove = false;

    public static boolean serverSpoof = false;
    public static String serverSpoofBrand = "vanilla";

    public static boolean soundBlocker = false;
    /** Teile von Geraeusch-Namen, mit Komma getrennt (z.B. "portal, ghast, thunder"). */
    public static String soundBlockList = "portal, thunder";

    public static boolean spam = false;
    public static int spamDelay = 5;                // Sekunden
    public static String spamText = "1912 Utilities";

    public static boolean serverGuard = false;

    // Join-Schutz: beim Betreten eines (neuen) Servers alle Module aus, wie mit der Taste J
    public static boolean joinGuard = true;
    public static int joinGuardMode = 0;            // 0 nur neue Server, 1 jeder Server
    public static String joinKnown = "";            // Server, auf denen man schon war (mit Komma getrennt)
    static final String[] JOIN_MODES = {"Nur auf neuen Servern", "Bei jedem Server"};
    /** Server-Adressen (Teile reichen), mit Komma getrennt, z.B. "hypixel, gommehd". */
    public static String serverGuardList = "hypixel, gommehd";

    public static boolean swarm = false;
    public static int swarmMode = 0;                // 0 = Auto, 1 = Host, 2 = Worker
    public static String swarmHost = "127.0.0.1";
    public static int swarmPort = 19120;
    public static boolean swarmLan = false;         // Host auch fuer andere PCs im Netzwerk
    public static boolean swarmMirror = true;       // Module des Hosts auf die Worker uebertragen
    public static boolean swarmFollow = false;      // Worker laufen dem Host nach
    public static String swarmMessage = "";

    // ---------- Zustand ----------

    private static int packetsThisTick = 0;
    private static int reconnectTicks = 0;
    private static int reconnectTries = 0;          // Versuche seit der letzten Verbindung
    private static long holdSince = 0;              // seit wann holdReconnect gilt (ms)
    /** Nach einem Auto-Log (Debris Miner) nicht automatisch neu verbinden; gilt bis zur naechsten Verbindung. */
    static boolean holdReconnect = false;
    private static ServerData lastServer = null;
    private static int spamTicks = 0;
    private static int beaconSentFor = -1;
    private static String guardLastServer = null;   // Fenster-ID, fuer das schon bezahlt wurde

    static final String[] BEACON_NAMES = {"Tempo", "Eile", "Resistenz", "Sprungkraft", "St\u00e4rke"};
    static final String[] BEACON_SECOND = {"Keine", "Regeneration", "Hauptwirkung st\u00e4rker"};
    private static boolean discordWasOn = false;
    private static long discordStart = 0;
    private static int discordTicks = 0;
    private static boolean durabilityWarned = false;
    private static boolean lastHadTotem = false;
    private static String lastChat = "";
    private static long lastChatTime = 0;
    private static final java.util.Map<String, Integer> auraCooldown = new java.util.HashMap<>();

    /** Better Chat: Nachricht enthaelt ein Wort aus dem Filter? */
    private static boolean filtered(Component message) {
        if (!betterChat || betterChatFilter == null || betterChatFilter.isBlank()) return false;
        String text = message.getString().toLowerCase(java.util.Locale.ROOT);
        for (String part : betterChatFilter.toLowerCase(java.util.Locale.ROOT).split(",")) {
            String w = part.trim();
            if (!w.isEmpty() && text.contains(w)) return true;
        }
        return false;
    }

    /** Better Chat: Stern in der Freundesfarbe vor die Nachricht. */
    private static Component highlight(Component message) {
        int col = Friends.color() & 0xFFFFFF;
        return Component.literal("★ ").withStyle(s -> s.withColor(col)).append(message);
    }

    /** Kommt in einer Server-Nachricht ein Freund als ganzes Wort vor? */
    private static boolean mentionsFriend(Component message) {
        String text = message.getString().toLowerCase(java.util.Locale.ROOT);
        for (String name : Friends.all()) {
            String n = name.toLowerCase(java.util.Locale.ROOT);
            if (n.isEmpty()) continue;
            for (int i = text.indexOf(n); i >= 0; i = text.indexOf(n, i + 1)) {
                int end = i + n.length();
                boolean before = i == 0 || !nameChar(text.charAt(i - 1));
                boolean after = end >= text.length() || !nameChar(text.charAt(end));
                if (before && after) return true;
            }
        }
        return false;
    }

    /** Zeichen, die in Minecraft-Namen vorkommen (Buchstaben, Ziffern, Unterstrich). */
    private static boolean nameChar(char c) {
        return c == '_' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9';
    }

    /** Better Chat: dieselbe Nachricht innerhalb des Zeitfensters nur einmal zeigen. */
    private static boolean allowRepeat(Component message) {
        if (filtered(message)) return false;
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
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, time) -> {
            if (!allowRepeat(message)) return false;
            if (betterChat && betterChatFriends && sender != null && Friends.listed(sender.name())) {
                // Spieler-Nachrichten lassen sich nicht aendern: selbst hervorgehoben einfuegen, das Original weglassen
                try {
                    Minecraft.getInstance().gui.hud.getChat().addPlayerMessage(highlight(message),
                            signed != null ? signed.signature() : null, null);
                    return false;
                } catch (RuntimeException e) {
                    return true;
                }
            }
            return true;
        });
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            if (overlay || !betterChat) return message;
            Component out = message;
            if (betterChatFriends && mentionsFriend(message)) out = highlight(out);
            if (betterChatTime) {
                String t = java.time.LocalTime.now().toString().substring(0, 5);
                out = Component.literal("§7[" + t + "] §r").append(out);
            }
            return out;
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
            p.sendSystemMessage(Lang.literal("\u00a7c1912: Book Bot - erst Buch und Feder in die Hand nehmen"));
            return;
        }
        String text = bookBotText == null || bookBotText.isBlank() ? "1912 Utilities" : bookBotText;
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
        p.sendSystemMessage(Lang.literal("\u00a7a1912: Book Bot - " + pages.size() + " Seiten geschrieben"
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

        holdReconnect = false; // wieder verbunden
        holdSince = 0;
        if (mc.player != null && mc.player.tickCount > 100) reconnectTries = 0; // Verbindung haelt
        if (autoReconnect) runAutoReconnect(mc);
        else reconnectTicks = 0;

        Notebot.tick(mc);
        Swarm.tick(mc);
        if (discordPresence) {
            runDiscord(mc);
        } else if (discordWasOn) {
            discordWasOn = false;
            DiscordIpc.stop();
        }
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
        if (betterBeacons) runBetterBeacons(mc);

    }

    /** Discord Presence: einmal pro Sekunde melden, was gerade angezeigt werden soll. */
    private static void runDiscord(Minecraft mc) {
        if (!discordWasOn) {
            discordWasOn = true;
            discordStart = System.currentTimeMillis();
            discordTicks = 0;
        }
        if (discordTicks++ % 20 != 0) return;
        String where;
        if (mc.level == null) {
            where = "Im Hauptmen\u00fc";
        } else if (mc.getCurrentServer() != null) {
            where = discordShowServer ? "Spielt auf " + mc.getCurrentServer().ip : "Spielt im Mehrspieler";
        } else {
            where = "Spielt im Einzelspieler";
        }
        DiscordIpc.update(discordAppId, where, discordText, discordStart);
    }

    /** true, wenn die Adresse auf der Schutz-Liste steht. */
    static boolean guarded(String address) {
        if (address == null) return false;
        String a = address.toLowerCase(java.util.Locale.ROOT);
        for (String part : serverGuardList.toLowerCase(java.util.Locale.ROOT).split(",")) {
            String t = part.trim();
            if (!t.isEmpty() && a.contains(t)) return true;
        }
        return false;
    }

    /**
     * Server-Schutz: beim Betreten eines Servers von der Liste alle Module ausschalten (wie die
     * J-Taste - mit J kommen sie zurueck, wenn man das wirklich will).
     */
    private static Object joinLastConnection;

    /**
     * Gleich nach dem Betreten einer Welt, noch vor den Modulen (aus TestClient): Server-Schutz und
     * Join-Schutz schalten bei Bedarf alle Module aus.
     */
    static void joinTick(Minecraft mc) {
        runServerGuard(mc);
        runJoinGuard(mc);
    }

    /** Adresse ohne Standard-Port, klein geschrieben. */
    private static String serverKey(String ip) {
        String s = ip == null ? "" : ip.strip().toLowerCase(java.util.Locale.ROOT);
        if (s.endsWith(":25565")) s = s.substring(0, s.length() - 6);
        while (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s.replace(",", "");
    }

    static int knownServers() {
        int n = 0;
        for (String part : joinKnown.split(",")) {
            if (!part.isBlank()) n++;
        }
        return n;
    }

    private static void runJoinGuard(Minecraft mc) {
        var conn = mc.getConnection();
        if (conn == null || mc.player == null || mc.level == null) {
            joinLastConnection = null;
            return;
        }
        if (conn == joinLastConnection) return; // nur einmal pro Verbindung (auch bei Dimensionswechsel)
        joinLastConnection = conn;
        ServerData sd = mc.getCurrentServer();
        if (sd == null) return; // Einzelspieler
        String key = serverKey(sd.ip);
        if (key.isEmpty()) return;
        boolean known = false;
        for (String part : joinKnown.split(",")) {
            if (part.strip().equals(key)) {
                known = true;
                break;
            }
        }
        if (!known) {
            joinKnown = joinKnown.isBlank() ? key : joinKnown + "," + key; // auch merken, wenn der Schutz aus ist
            TestConfig.save();
        }
        if (!joinGuard || (joinGuardMode == 0 && known)) return;
        if (!Keybinds.PANIC.isEmpty()) return; // schon alles aus (z.B. vom Server-Schutz)
        Keybinds.panic();
        if (Keybinds.PANIC.isEmpty()) return; // es war nichts an
        Integer code = Keybinds.get(Keybinds.PANIC_NAME);
        String k = code != null ? Keybinds.keyName(code) : Lang.t(Keybinds.PANIC_NAME);
        mc.player.sendSystemMessage(Lang.literal("§e1912: " + Lang.t(known ? "Join-Schutz: alle Module aus. "
                : "Join-Schutz: neuer Server - alle Module aus. ") + Lang.t("Wieder an mit: ") + k));
    }

    private static void runServerGuard(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            guardLastServer = null;
            return;
        }
        String ip = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "";
        if (ip.equals(guardLastServer)) return;
        guardLastServer = ip;
        if (!serverGuard || !guarded(ip)) return;
        if (!Keybinds.PANIC.isEmpty()) return; // schon alles aus
        Keybinds.panic();
        mc.player.sendSystemMessage(Lang.literal("\u00a7e1912: Server-Schutz - auf " + ip
                + " wurden alle Module ausgeschaltet."));
    }

    private static net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> beaconEffect(int i) {
        return switch (i) {
            case 0 -> net.minecraft.world.effect.MobEffects.SPEED;
            case 1 -> net.minecraft.world.effect.MobEffects.HASTE;
            case 2 -> net.minecraft.world.effect.MobEffects.RESISTANCE;
            case 3 -> net.minecraft.world.effect.MobEffects.JUMP_BOOST;
            default -> net.minecraft.world.effect.MobEffects.STRENGTH;
        };
    }

    /**
     * Better Beacons: sobald im Leuchtfeuer-Fenster ein Bezahl-Item liegt, werden die gewaehlten
     * Wirkungen gesetzt - auch solche, die die Pyramide eigentlich noch nicht freischaltet.
     */
    private static void runBetterBeacons(Minecraft mc) {
        var p = mc.player;
        if (!(p.containerMenu instanceof net.minecraft.world.inventory.BeaconMenu menu)) {
            beaconSentFor = -1;
            return;
        }
        if (beaconSentFor == menu.containerId || !menu.getSlot(0).hasItem() || mc.getConnection() == null) return;
        beaconSentFor = menu.containerId;
        var primary = beaconEffect(beaconPrimary);
        java.util.Optional<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>> second =
                beaconSecondary == 1 ? java.util.Optional.of(net.minecraft.world.effect.MobEffects.REGENERATION)
                        : beaconSecondary == 2 ? java.util.Optional.of(primary) : java.util.Optional.empty();
        mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetBeaconPacket(
                java.util.Optional.of(primary), second));
        p.sendSystemMessage(Lang.literal("\u00a7a1912: Leuchtfeuer gesetzt: " + Lang.t(BEACON_NAMES[Math.floorMod(beaconPrimary, 5)])
                + (beaconSecondary == 0 ? "" : " + " + BEACON_SECOND[beaconSecondary])));
    }

    static void sendChat(Minecraft mc, String text) {
        if (mc.getConnection() == null || text == null || text.isBlank()) return;
        String msg = text.strip();
        if (msg.startsWith("/")) {
            mc.getConnection().sendCommand(msg.substring(1));
        } else {
            mc.getConnection().sendChat(msg);
        }
    }

    /** Ohne Welt (z.B. nach einem Rauswurf): nur Auto Reconnect. */
    public static void tickOffline(Minecraft mc) {
        if (holdReconnect && holdSince == 0) holdSince = System.currentTimeMillis();
        boolean waited = holdReconnect && autoReconnectAfterLogout > 0
                && System.currentTimeMillis() - holdSince >= autoReconnectAfterLogout * 60_000L;
        if (autoReconnect && (!holdReconnect || waited)) runAutoReconnect(mc);
        else reconnectTicks = 0;
    }

    private static void runAutoReconnect(Minecraft mc) {
        Screen current = mc.gui.screen();
        if (!(current instanceof DisconnectedScreen)) {
            reconnectTicks = 0;
            return;
        }
        if (lastServer == null) return;
        if (autoReconnectMax > 0 && reconnectTries >= autoReconnectMax) return; // genug versucht
        int delay = autoReconnectDelay;
        if (autoReconnectBackoff) delay = Math.min(300, autoReconnectDelay * (1 + reconnectTries));
        if (++reconnectTicks < delay * 20) return;
        reconnectTicks = 0;
        reconnectTries++;
        try {
            net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                    current, mc, net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(lastServer.ip),
                    lastServer, false, null);
        } catch (RuntimeException e) {
            System.err.println("[1912] Auto Reconnect fehlgeschlagen: " + e);
            autoReconnect = false;
        }
    }

    private static boolean healthWarned;
    private static boolean invWarned;
    private static java.util.Map<Integer, String> seenPlayers = new java.util.HashMap<>();
    private static Object seenLevel;

    private static void runNotifier(Minecraft mc) {
        var p = mc.player;
        if (notifyHealth) {
            if (p.getHealth() <= notifyHealthAt && p.isAlive()) {
                if (!healthWarned) {
                    healthWarned = true;
                    p.sendSystemMessage(Lang.literal("§c1912: " + Lang.t("Wenig Leben: ")
                            + String.format(java.util.Locale.ROOT, "%.1f", p.getHealth() / 2f).replace(".0", "")
                            + " ❤"));
                }
            } else if (p.getHealth() > notifyHealthAt + 2) {
                healthWarned = false;
            }
        }
        if (notifyFullInv) {
            boolean full = true;
            for (int i = 0; i < 36; i++) {
                if (p.getInventory().getItem(i).isEmpty()) {
                    full = false;
                    break;
                }
            }
            if (full && !invWarned) {
                invWarned = true;
                p.sendSystemMessage(Lang.literal("§e1912: " + Lang.t("Inventar voll")));
            } else if (!full) {
                invWarned = false;
            }
        }
        if (notifyPlayers) {
            java.util.Map<Integer, String> now = new java.util.HashMap<>();
            for (var o : mc.level.players()) {
                if (o == p || PlayerMods.isFake(o) || Friends.listed(o.getGameProfile().name())) continue;
                now.put(o.getId(), o.getGameProfile().name());
                // erst nach einem Welt- oder Dimensionswechsel nicht gleich alle melden
                if (seenLevel == mc.level && !seenPlayers.containsKey(o.getId())) {
                    p.sendSystemMessage(Lang.literal("§e1912: " + Lang.t("Spieler in Sichtweite: ")
                            + o.getGameProfile().name() + " (" + Math.round(p.distanceTo(o)) + "m)"));
                }
            }
            seenPlayers = now;
            seenLevel = mc.level;
        } else {
            seenPlayers.clear();
            seenLevel = null;
        }
        if (notifyDurability) {
            ItemStack main = p.getMainHandItem();
            if (main.isDamageableItem()) {
                int left = main.getMaxDamage() - main.getDamageValue();
                int pct = left * 100 / main.getMaxDamage();
                if (pct <= notifyDurabilityAt && !durabilityWarned) {
                    durabilityWarned = true;
                    p.sendSystemMessage(Lang.literal("§e1912: Werkzeug fast kaputt (" + pct + "%)"));
                } else if (pct > notifyDurabilityAt) {
                    durabilityWarned = false;
                }
            }
        }
        if (notifyTotem) {
            boolean hasTotem = p.getOffhandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)
                    || p.getMainHandItem().is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING);
            if (lastHadTotem && !hasTotem) {
                p.sendSystemMessage(Lang.literal("§c1912: Totem verbraucht!"));
            }
            lastHadTotem = hasTotem;
        }
    }

    private static void runMessageAura(Minecraft mc) {
        for (var other : mc.level.players()) {
            if (other == mc.player) continue;
            if (other.distanceTo(mc.player) > messageAuraChunks * 16) continue;
            if (messageAuraWho == 1 && !Friends.is(other)) continue;
            if (messageAuraWho == 2 && Friends.is(other)) continue;
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
                f.slider("Höchstens Versuche (0 = immer weiter)", 0, 50, () -> autoReconnectMax,
                        v -> autoReconnectMax = v, 0);
                f.toggle("Nach jedem Fehlversuch länger warten", () -> autoReconnectBackoff,
                        v -> autoReconnectBackoff = v, false);
                f.slider("Nach Auto-Logout neu verbinden nach (Minuten, 0 = nie)", 0, 120,
                        () -> autoReconnectAfterLogout, v -> autoReconnectAfterLogout = v, 0);
                f.note("Länger warten: beim 2. Versuch doppelt so lange, beim 3. dreimal so lange und so weiter "
                        + "(höchstens 5 Minuten). Auto-Logout und der Auto-Log der Miner verbinden sonst nie von "
                        + "selbst neu.");
                f.footer(() -> autoReconnect, v -> autoReconnect = v);
            });
            case "Better Beacons" -> new ModuleScreen(parent, module, f -> {
                f.description("W\u00e4hlt Leuchtfeuer-Wirkungen unabh\u00e4ngig von der Gr\u00f6\u00dfe der Pyramide.\nLeuchtfeuer \u00f6ffnen, Bezahl-Item (z.B. Eisenbarren) reinlegen - fertig.");
                f.section("Wirkungen");
                f.choice("Hauptwirkung", BEACON_NAMES, () -> beaconPrimary, v -> beaconPrimary = v, 4);
                f.choice("Zweite Wirkung", BEACON_SECOND, () -> beaconSecondary, v -> beaconSecondary = v, 1);
                f.note("Die zweite Wirkung gibt Minecraft nur bei voller Pyramide (4 Stufen).");
                f.footer(() -> betterBeacons, v -> betterBeacons = v);
            });
            case "Better Chat" -> new ModuleScreen(parent, module, f -> {
                f.description("Verbessert den Chat.");
                f.section("Allgemein");
                f.toggle("Uhrzeit davor", () -> betterChatTime, v -> betterChatTime = v, false);
                f.toggle("Wiederholungen ausblenden", () -> betterChatDedup, v -> betterChatDedup = v, true);
                f.slider("Zeitfenster (s)", 1, 60, () -> betterChatWindow, v -> betterChatWindow = v, 10);
                f.toggle("Nachrichten von Freunden hervorheben", () -> betterChatFriends, v -> betterChatFriends = v,
                        false);
                f.text("Ausblenden, wenn darin vorkommt", () -> betterChatFilter, v -> betterChatFilter = v, "", 200,
                        null);
                f.note("Freunde bekommen einen Stern in ihrer Farbe. Beim Filter mehrere Wörter mit Komma "
                        + "trennen - Groß- und Kleinschreibung ist egal.");
                f.footer(() -> betterChat, v -> betterChat = v);
            });
            case "Book Bot" -> new ModuleScreen(parent, module, f -> {
                f.description("Beschreibt Buch und Feder in deiner Hand\nund schaltet sich danach wieder aus.");
                f.section("Inhalt");
                f.text("Text je Seite", () -> bookBotText, v -> bookBotText = v, "1912 Utilities");
                f.slider("Seiten", 1, 100, () -> bookBotPages, v -> bookBotPages = v, 50);
                f.section("Signieren");
                f.toggle("Buch signieren", () -> bookBotSign, v -> bookBotSign = v, false);
                f.text("Titel", () -> bookBotTitle, v -> bookBotTitle = v, "1912");
                f.footer(() -> bookBot, v -> bookBot = v);
            });
            case "Discord Presence" -> new ModuleScreen(parent, module, f -> {
                f.description("Zeigt in Discord, dass du gerade spielst.\nDiscord muss auf diesem PC laufen.");
                f.section("Allgemein");
                f.text("App-ID", () -> discordAppId, v -> discordAppId = v, "");
                f.text("Zweite Zeile", () -> discordText, v -> discordText = v, "1912 Utilities");
                f.toggle("Server-Adresse zeigen", () -> discordShowServer, v -> discordShowServer = v, false);
                f.note(() -> "Status: " + Lang.t(DiscordIpc.status()));
                f.note("App-ID: auf discord.com/developers/applications eine Anwendung anlegen. Ihr Name "
                        + "erscheint in Discord als Spiel. Die Application ID dann hier eintragen.");
                f.footer(() -> discordPresence, v -> discordPresence = v);
            });
            case "Inventory Tweaks" -> new ModuleScreen(parent, module, f -> {
                f.description("Sortiert dein Inventar nach deiner eigenen Aufteilung\n"
                        + "(Schwert, Werkzeuge, Bl\u00f6cke, Perlen und mehr auf feste Pl\u00e4tze).");
                f.wideButton("Aufteilung festlegen", true, () -> Minecraft.getInstance().gui.setScreen(
                        new InvLayoutScreen(Minecraft.getInstance().gui.screen())));
                f.footer(() -> inventoryTweaks, v -> inventoryTweaks = v);
            });
            case "Message Aura" -> new ModuleScreen(parent, module, f -> {
                f.description("Schreibt Spielern in der Nähe automatisch.\n%name% wird ersetzt.");
                f.section("Allgemein");
                f.text("Text", () -> messageAuraText, v -> messageAuraText = v, "Hi %name%!");
                f.slider("Reichweite (Chunks)", 1, 32, () -> messageAuraChunks, v -> messageAuraChunks = v, 6);
                f.slider("Pause je Spieler (s)", 5, 300, () -> messageAuraDelay, v -> messageAuraDelay = v, 60);
                f.choice("Wen anschreiben", MESSAGE_AURA_WHO, () -> messageAuraWho, v -> messageAuraWho = v, 0);
                f.footer(() -> messageAura, v -> messageAura = v);
            });
            case "Notebot" -> new ModuleScreen(parent, module, f -> {
                f.description("Spielt ein Lied auf Notenbl\u00f6cken in deiner N\u00e4he.\nStell dich so hin, dass du alle erreichst (ca. 4 Bl\u00f6cke).");
                f.section("Lied");
                f.choice("Lied", Notebot.SONG_NAMES, () -> notebotSong, v -> notebotSong = v, 0);
                f.slider("Tempo (Ticks je Note)", 1, 10, () -> notebotTempo, v -> notebotTempo = v, 4);
                f.toggle("Wiederholen", () -> notebotLoop, v -> notebotLoop = v, false);
                f.section("Stimmen");
                f.slider("Pause je Klick (Ticks)", 1, 10, () -> notebotTuneDelay, v -> notebotTuneDelay = v, 2);
                f.note("Eigene Datei: config/" + Notebot.FILE_NAME + " - Noten mit Leerzeichen: c d e f g a h, "
                        + "c2 = h\u00f6here Oktave, c# = Halbton, - = Pause, c+e+g = Akkord. "
                        + "Nicht im Kreativmodus. \u00dcber jedem Notenblock muss Luft sein.");
                f.footer(() -> notebot, v -> notebot = v);
            });
            case "Notifier" -> new ModuleScreen(parent, module, f -> {
                f.description("Meldet wichtige Ereignisse im Chat.");
                f.section("Allgemein");
                f.toggle("Haltbarkeit", () -> notifyDurability, v -> notifyDurability = v, true);
                f.slider("Warnen bei (%)", 1, 50, () -> notifyDurabilityAt, v -> notifyDurabilityAt = v, 10);
                f.toggle("Totem verbraucht", () -> notifyTotem, v -> notifyTotem = v, true);
                f.toggle("Wenig Leben", () -> notifyHealth, v -> notifyHealth = v, false);
                f.slider("Leben ≤ (2 = 1 Herz)", 2, 19, () -> notifyHealthAt, v -> notifyHealthAt = v, 6);
                f.toggle("Inventar voll", () -> notifyFullInv, v -> notifyFullInv = v, false);
                f.toggle("Spieler in Sichtweite (keine Freunde)", () -> notifyPlayers, v -> notifyPlayers = v, false);
                f.note("Freunde in Sichtweite meldet schon die Freundesliste (Menü → Freunde).");
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
            case "Sound Blocker" -> new ModuleScreen(parent, module, f -> {
                f.description("Stummt Ger\u00e4usche, die der Server schickt\n(Mobs, Portale, Gewitter, Explosionen und mehr).");
                f.section("Allgemein");
                f.text("Ger\u00e4usche", () -> soundBlockList, v -> soundBlockList = v, "portal, thunder");
                f.note("Teile des Namens, mit Komma getrennt - z.B. ghast, portal, thunder, explode, villager.");
                f.footer(() -> soundBlocker, v -> soundBlocker = v);
            });
            case "Spam" -> new ModuleScreen(parent, module, f -> {
                f.description("Schreibt wiederholt eine Nachricht.\nBeginnt mit / für Befehle.");
                f.section("Allgemein");
                f.text("Text", () -> spamText, v -> spamText = v, "1912 Utilities");
                f.slider("Abstand (s)", 1, 60, () -> spamDelay, v -> spamDelay = v, 5);
                f.footer(() -> spam, v -> spam = v);
            });
            case "Join-Schutz" -> new ModuleScreen(parent, module, f -> {
                f.description("Schaltet beim Betreten eines Servers alle Module aus - genau so, als hättest du die "
                        + "Taste „Alle Module aus / an“ (J) gedrückt. Mit J gehen sie wieder an.");
                f.choice("Wann", JOIN_MODES, () -> joinGuardMode, v -> joinGuardMode = v, 0);
                f.note(() -> "Bekannte Server: " + knownServers());
                f.wideButton("Bekannte Server vergessen", false, () -> {
                    joinKnown = "";
                    TestConfig.save();
                });
                f.note("Neu heißt: Auf diesem Server warst du mit 1912 Utilities noch nie. Nach einem Wechsel der "
                        + "Dimension oder Lobby passiert nichts. Freunde, Server-Schutz und Join-Schutz bleiben an.");
                f.footer(() -> joinGuard, v -> joinGuard = v);
            });
            case "Server-Schutz" -> new ModuleScreen(parent, module, f -> {
                f.description("Schaltet beim Betreten bestimmter Server automatisch alle Module aus - "
                        + "damit du dort nicht aus Versehen etwas anhast, das verboten ist.");
                f.section("Server");
                f.text("Adressen", () -> serverGuardList, v -> serverGuardList = v, "hypixel, gommehd");
                f.note("Teile der Adresse, mit Komma getrennt. Mit J holst du die Module zur\u00fcck.");
                f.footer(() -> serverGuard, v -> serverGuard = v);
            });
            case "Swarm" -> new ModuleScreen(parent, module, f -> {
                f.description("Steuert mehrere eigene Spiele gemeinsam: eines ist der Host,\ndie anderen verbinden sich als Worker und machen mit.");
                f.section("Verbindung");
                f.choice("Rolle", Swarm.MODES, () -> swarmMode, v -> swarmMode = v, 0);
                f.text("Host-Adresse", () -> swarmHost, v -> swarmHost = v, "127.0.0.1");
                f.number("Port", () -> swarmPort, v -> swarmPort = v, 19120);
                f.toggle("Auch andere PCs (LAN)", () -> swarmLan, v -> swarmLan = v, false);
                f.note(() -> "Status: " + Lang.t(SwarmNet.status()));
                f.note("Auto: das erste Spiel auf diesem PC wird Host, alle weiteren werden Worker.");
                f.section("Host steuert die Worker");
                f.toggle("Module spiegeln", () -> swarmMirror, v -> swarmMirror = v, true);
                f.toggle("Worker folgen mir", () -> swarmFollow, v -> swarmFollow = v, false);
                f.text("Nachricht", () -> swarmMessage, v -> swarmMessage = v, "");
                f.wideButton("Nachricht von allen Workern senden", false, () -> swarmHint(Swarm.sayAll()));
                f.wideButton("Alle Worker zu mir holen", false, () -> swarmHint(Swarm.callAll(Minecraft.getInstance())));
                f.wideButton("Alle Worker stoppen", false, () -> swarmHint(Swarm.stopAll()));
                f.footer(() -> swarm, v -> swarm = v);
            });
            default -> null;
        };
    }

    private static final java.util.Map<Class<?>, java.lang.reflect.Field[]> SOUND_FIELDS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Sound Blocker: true = dieses Ger\u00e4usch-Paket nicht abspielen. Der Name des Ger\u00e4uschs wird aus
     * den Feldern des Pakets gelesen (unabh\u00e4ngig von deren genauen Namen).
     */
    public static boolean blockSound(Object packet) {
        if (!soundBlocker || packet == null || soundBlockList.isBlank()) return false;
        String text;
        try {
            java.lang.reflect.Field[] fields = SOUND_FIELDS.computeIfAbsent(packet.getClass(), c -> {
                List<java.lang.reflect.Field> out = new java.util.ArrayList<>();
                for (java.lang.reflect.Field fd : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(fd.getModifiers()) || fd.getType().isPrimitive()) continue;
                    try {
                        fd.setAccessible(true);
                        out.add(fd);
                    } catch (RuntimeException e) {
                        // Feld nicht lesbar: ueberspringen
                    }
                }
                return out.toArray(new java.lang.reflect.Field[0]);
            });
            StringBuilder sb = new StringBuilder();
            for (java.lang.reflect.Field fd : fields) sb.append(fd.get(packet)).append(' ');
            text = sb.toString().toLowerCase(java.util.Locale.ROOT);
        } catch (Exception | LinkageError e) {
            return false;
        }
        for (String part : soundBlockList.toLowerCase(java.util.Locale.ROOT).split(",")) {
            String t = part.trim();
            if (!t.isEmpty() && text.contains(t)) return true;
        }
        return false;
    }

    /** Zeigt eine Fehlermeldung eines Swarm-Knopfs im Chat (null = alles gut). */
    private static void swarmHint(String error) {
        var p = Minecraft.getInstance().player;
        if (error != null && p != null) p.sendSystemMessage(Lang.literal("\u00a7c1912: Swarm - " + Lang.t(error)));
    }

}
