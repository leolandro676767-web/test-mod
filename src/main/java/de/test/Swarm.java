package de.test;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Swarm: mehrere eigene Spiele gemeinsam steuern. Der Host schickt Befehle, die Worker fuehren
 * sie aus: Module an/aus (spiegeln), zum Host laufen, stoppen, eine Chat-Nachricht senden.
 * Rolle "Auto": das erste Spiel auf dem PC wird Host, alle weiteren werden Worker.
 */
final class Swarm {
    private Swarm() {
    }

    static final String[] MODES = {"Auto", "Host", "Worker"};

    private static boolean wasOn;
    private static String signature = "";
    private static final Map<String, Boolean> lastStates = new HashMap<>();
    private static int ticks;
    private static BlockPos lastFollow;

    static void tick(Minecraft mc) {
        if (!MiscMods.swarm) {
            if (wasOn) {
                SwarmNet.stop();
                wasOn = false;
                signature = "";
                lastStates.clear();
                lastFollow = null;
            }
            return;
        }
        String sig = MiscMods.swarmMode + "|" + MiscMods.swarmHost + "|" + MiscMods.swarmPort + "|" + MiscMods.swarmLan;
        if (!wasOn || !sig.equals(signature)) {
            SwarmNet.start(Math.max(0, Math.min(2, MiscMods.swarmMode)), MiscMods.swarmHost, MiscMods.swarmPort,
                    MiscMods.swarmLan);
            wasOn = true;
            signature = sig;
            lastStates.clear();
            lastFollow = null;
        }
        if (mc.player == null || mc.level == null) {
            SwarmNet.INBOX.clear();
            return;
        }
        ticks++;
        if (SwarmNet.hosting()) hostTick(mc);
        else workerTick(mc);
    }

    private static Map<String, Keybinds.Action> actions() {
        if (Keybinds.ACTIONS.isEmpty()) TestScreen.registerActions();
        return Keybinds.ACTIONS;
    }

    // ---------- Host ----------

    private static void hostTick(Minecraft mc) {
        boolean fresh = SwarmNet.takeNewWorker();
        if (MiscMods.swarmMirror) {
            for (var e : actions().entrySet()) {
                String name = e.getKey();
                var state = e.getValue().state();
                if (state == null || name.equals("Swarm")) continue;
                boolean now = state.getAsBoolean();
                Boolean before = lastStates.put(name, now);
                // Aenderungen sofort weitergeben; neue Worker bekommen einmal den ganzen Stand
                if (fresh || (before != null && before != now)) {
                    SwarmNet.broadcast("MODULE " + (now ? "on" : "off") + " " + name);
                }
            }
        } else {
            lastStates.clear();
        }
        if (MiscMods.swarmFollow && ticks % 20 == 0) {
            BlockPos p = mc.player.blockPosition();
            if (lastFollow == null || lastFollow.distManhattan(p) > 2 || fresh) {
                lastFollow = p;
                SwarmNet.broadcast("GOTO " + p.getX() + " " + p.getY() + " " + p.getZ());
            }
        }
    }

    /** Knopf: alle Worker laufen zur aktuellen Position des Hosts. */
    static String callAll(Minecraft mc) {
        if (!SwarmNet.hosting()) return "Nur der Host kann das";
        if (mc.player == null) return "Nicht im Spiel";
        BlockPos p = mc.player.blockPosition();
        SwarmNet.broadcast("GOTO " + p.getX() + " " + p.getY() + " " + p.getZ());
        return null;
    }

    static String stopAll() {
        if (!SwarmNet.hosting()) return "Nur der Host kann das";
        SwarmNet.broadcast("STOP");
        return null;
    }

    static String sayAll() {
        if (!SwarmNet.hosting()) return "Nur der Host kann das";
        if (MiscMods.swarmMessage.isBlank()) return "Erst eine Nachricht eintragen";
        SwarmNet.broadcast("SAY " + MiscMods.swarmMessage.strip());
        return null;
    }

    // ---------- Worker ----------

    private static void workerTick(Minecraft mc) {
        for (int i = 0; i < 20; i++) {
            String line = SwarmNet.INBOX.poll();
            if (line == null) return;
            try {
                run(mc, line);
            } catch (RuntimeException e) {
                // ein kaputter Befehl darf das Spiel nicht stoeren
            }
        }
    }

    private static void run(Minecraft mc, String line) {
        if (line.startsWith("MODULE ")) {
            String rest = line.substring(7);
            int sp = rest.indexOf(' ');
            if (sp < 0) return;
            boolean want = rest.substring(0, sp).equals("on");
            String name = rest.substring(sp + 1);
            if (name.equals("Swarm")) return;
            Keybinds.Action a = actions().get(name);
            if (a == null || a.state() == null) return;
            if (a.state().getAsBoolean() != want) a.run().run();
        } else if (line.startsWith("GOTO ")) {
            String[] p = line.substring(5).trim().split(" ");
            if (p.length != 3) return;
            int x = Integer.parseInt(p[0]);
            int y = Integer.parseInt(p[1]);
            int z = Integer.parseInt(p[2]);
            if (TestClient.areaActive && TestClient.gx == x && TestClient.gy == y && TestClient.gz == z) return;
            if (TestClient.areaActive) TestClient.stopArea(mc, null);
            TestClient.gx = x;
            TestClient.gy = y;
            TestClient.gz = z;
            TestClient.startGoto(mc);
        } else if (line.equals("STOP")) {
            if (TestClient.areaActive) TestClient.stopArea(mc, "Swarm: gestoppt");
        } else if (line.startsWith("SAY ")) {
            String text = line.substring(4);
            if (text.length() > 256) text = text.substring(0, 256);
            MiscMods.sendChat(mc, text);
        }
    }
}
