package de.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Einstellungen der Freundesliste: Freunde eintragen/entfernen und festlegen, was fuer Freunde gilt. */
public class FriendsScreen extends Form {
    /** Name im Eingabefeld (bleibt beim Neuaufbau des Menues erhalten). */
    private static String pending = "";

    public FriendsScreen(Screen parent) {
        super(Component.literal("Freunde"), parent);
    }

    @Override
    protected void build() {
        description("Freunde werden nicht angegriffen und überall in ihrer eigenen Farbe angezeigt. "
                + "Mittelklick auf einen Spieler fügt ihn hinzu oder entfernt ihn wieder.");

        section("Freundesliste");
        text("Spielername", () -> pending, v -> pending = v, "", 32, () -> "z.B. Notch");
        wideButton("Hinzufügen", true, () -> {
            if (Friends.add(pending)) pending = "";
            this.rebuildWidgets();
        }, true);
        Set<String> online = onlineNames();
        List<String> friends = Friends.all();
        for (String n : friends) {
            boolean on = online.contains(n.toLowerCase(Locale.ROOT));
            nameRow(n, on ? Friends.color() : Ui.MUTED, "Entfernen", () -> Friends.remove(n));
        }
        note(() -> Friends.count() == 0
                ? "Noch keine Freunde. Trag oben einen Namen ein, wähl unten einen Spieler aus der Nähe oder "
                        + "klick einen Spieler mit dem Mausrad an."
                : "Leuchtende Namen sind gerade online, graue nicht.");

        section("Spieler in der Nähe");
        List<Player> nearby = nearbyPlayers();
        for (Player pl : nearby) {
            String n = pl.getGameProfile().name();
            int dist = this.minecraft.player == null ? 0 : Math.round(pl.distanceTo(this.minecraft.player));
            nameRow(n + "  " + dist + "m", Ui.TEXT, "Hinzufügen", () -> Friends.add(n));
        }
        if (nearby.isEmpty()) note("Gerade ist kein anderer Spieler in der Nähe.");

        section("Anzeige");
        hex("Freundesfarbe", () -> Friends.color, v -> Friends.color = v);
        toggle("ESP in Freundesfarbe", () -> Friends.showEsp, v -> Friends.showEsp = v, true);
        toggle("Tracers in Freundesfarbe", () -> Friends.showTracers, v -> Friends.showTracers = v, true);
        toggle("Chams in Freundesfarbe", () -> Friends.showChams, v -> Friends.showChams = v, true);
        toggle("Nametags in Freundesfarbe", () -> Friends.showNametags, v -> Friends.showNametags = v, true);
        toggle("Better Tab: Freunde oben und farbig", () -> Friends.showTab, v -> Friends.showTab = v, true);
        note("Leere Freundesfarbe = Mintgrün.");

        section("Schutz");
        toggle("Nicht angreifen (Kill Aura, Auto Clicker, Multitask)", () -> Friends.noAttack, v -> Friends.noAttack = v, true);
        toggle("Auch eigene Schläge abfangen", () -> Friends.blockHits, v -> Friends.blockHits = v, false);
        toggle("Auto Wasp fliegt nicht zu Freunden", () -> Friends.noChase, v -> Friends.noChase = v, true);
        toggle("City Esp lässt Freunde aus", () -> Friends.noCity, v -> Friends.noCity = v, true);
        note("Mit „Auch eigene Schläge abfangen“ triffst du Freunde auch mit der Hand nicht mehr. "
                + "Message Aura und Debris Miner haben dazu eigene Einstellungen.");

        section("Bedienung");
        toggle("Mittelklick auf Spieler: hinzufügen oder entfernen", () -> Friends.middleClick,
                v -> Friends.middleClick = v, true);
        slider("Mittelklick-Reichweite", 4, 128, () -> Friends.middleClickRange, v -> Friends.middleClickRange = v, 64);
        toggle("Melden, wenn ein Freund online oder offline geht", () -> Friends.notifyOnline,
                v -> Friends.notifyOnline = v, true);
        toggle("Melden, wenn ein Freund in Sichtweite kommt", () -> Friends.notifyNear,
                v -> Friends.notifyNear = v, false);

        footer(() -> Friends.enabled, v -> Friends.enabled = v);
    }

    private Set<String> onlineNames() {
        Set<String> out = new HashSet<>();
        var conn = this.minecraft == null ? null : this.minecraft.getConnection();
        if (conn == null) return out;
        for (var info : conn.getOnlinePlayers()) out.add(info.getProfile().name().toLowerCase(Locale.ROOT));
        return out;
    }

    /** Andere Spieler in Sichtweite, die noch keine Freunde sind (die naechsten zuerst, hoechstens 12). */
    private List<Player> nearbyPlayers() {
        List<Player> out = new ArrayList<>();
        var mc = this.minecraft;
        if (mc == null || mc.player == null || mc.level == null) return out;
        for (Player o : mc.level.players()) {
            if (o == mc.player || PlayerMods.isFake(o)) continue;
            if (Friends.listed(o.getGameProfile().name())) continue;
            out.add(o);
        }
        out.sort((a, b) -> Double.compare(a.distanceToSqr(mc.player), b.distanceToSqr(mc.player)));
        return out.size() > 12 ? new ArrayList<>(out.subList(0, 12)) : out;
    }
}
