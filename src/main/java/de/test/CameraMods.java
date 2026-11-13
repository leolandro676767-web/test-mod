package de.test;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import org.lwjgl.glfw.GLFW;

/**
 * Freecam und Free Look, ohne Kamera-Mixins.
 * <p>Freecam: Die Kamera bekommt eine eigene, unsichtbare Figur ({@code setCameraEntity}). Die Maus
 * dreht weiterhin den Spieler; jede Drehung wird sofort auf die Kamera-Figur uebertragen und der
 * Spieler zurueckgedreht. Solange der Spieler nicht die Kamera ist, bewegt Minecraft ihn nicht und
 * schickt dem Server keine Bewegung - der Koerper bleibt also wirklich stehen.
 * <p>Free Look: Der Spieler bleibt die Kamera und dreht sich frei; nur fuer die Dauer seiner
 * Bewegungsberechnung (zwischen Tick-Anfang und -Ende) wird die alte Laufrichtung eingesetzt.
 */
public final class CameraMods {
    private CameraMods() {
    }

    private static final int OFF = 0;
    private static final int FREECAM = 1;
    private static final int FREELOOK = 2;

    private static RemotePlayer cam;
    private static int mode = OFF;
    private static float camYaw;
    private static float camPitch;
    private static float bodyYaw;
    private static float bodyPitch;
    private static float walkYaw;   // Free Look: feste Laufrichtung
    private static float lookYaw;   // Free Look: aktuelle Blickrichtung
    private static boolean waitNoted; // Hinweis "wartet auf Boden" schon gezeigt
    private static int camIds;

    public static boolean active() {
        return mode != OFF;
    }

    // ---------- Start / Stopp ----------

    private static void start(Minecraft mc, LocalPlayer p, int newMode) {
        if (newMode == FREELOOK) {
            walkYaw = p.getYRot();
            lookYaw = walkYaw;
            mode = FREELOOK;
            return;
        }
        try {
            cam = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "1912-Kamera"));
        } catch (RuntimeException e) {
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§c1912: Kamera konnte nicht erstellt werden"));
            RenderMods.freecam = false;
            RenderMods.freeLook = false;
            return;
        }
        // Eine eigene Kennung (ID) geben: andere Mods (z.B. die Animationen vom NoRisk Client) fragen die
        // ID der Kamera-Figur ab, und ohne ID stuerzt das Spiel dann ab. Negativ, damit sie nie mit
        // einem echten Wesen auf dem Server zusammenfaellt.
        cam.setId(-1912000 - (++camIds % 1000));
        cam.copyPosition(p);
        cam.xo = p.getX();
        cam.yo = p.getY();
        cam.zo = p.getZ();
        bodyYaw = p.getYRot();
        bodyPitch = p.getXRot();
        camYaw = bodyYaw;
        camPitch = bodyPitch;
        applyCamRotation();
        mode = newMode;
        mc.setCameraEntity(cam);
    }

    private static void stop(Minecraft mc) {
        if (mode == FREELOOK) {
            mode = OFF;
            return;
        }
        if (mc.player != null) mc.setCameraEntity(mc.player);
        releaseKeys(mc);
        cam = null;
        mode = OFF;
    }

    /** Beim Verlassen der Welt alles zuruecksetzen (Freecam soll nach dem Beitreten nicht sofort an sein). */
    private static void reset() {
        cam = null;
        mode = OFF;
        RenderMods.freecam = false;
        RenderMods.freeLook = false;
    }

    // ---------- Drehung (pro Frame und pro Tick) ----------

    /** Maus-Drehung des Spielers auf die Kamera uebertragen und den Spieler zurueckdrehen. */
    private static void transferRotation(LocalPlayer p) {
        float dy = p.getYRot() - bodyYaw;
        float dp = p.getXRot() - bodyPitch;
        if (dy != 0f || dp != 0f) {
            camYaw += dy;
            camPitch = Math.max(-90f, Math.min(90f, camPitch + dp));
            p.setYRot(bodyYaw);
            p.setXRot(bodyPitch);
            p.yRotO = bodyYaw;
            p.xRotO = bodyPitch;
        }
        applyCamRotation();
    }

    private static void applyCamRotation() {
        if (cam == null) return;
        cam.setYRot(camYaw);
        cam.setXRot(camPitch);
        cam.yRotO = camYaw;
        cam.xRotO = camPitch;
        // Die Kamera schaut entlang der Kopfdrehung und mischt dabei alten und neuen Wert. Der alte
        // Wert muss mitgesetzt werden, sonst pendelt das Bild 20-mal pro Sekunde zwischen beiden.
        cam.setYHeadRot(camYaw);
        cam.yHeadRotO = camYaw;
        cam.yBodyRot = camYaw;
        cam.yBodyRotO = camYaw;
    }

    /** Jeden Frame aus dem HUD-Zeichnen aufgerufen: sorgt fuer fluessiges Umsehen. */
    public static void frame(Minecraft mc) {
        if (mode != FREECAM || mc.player == null || cam == null) return;
        transferRotation(mc.player);
    }

    // ---------- Ticks ----------

    /** Tick-Anfang: Modus wechseln, Drehung uebertragen, in Freecam den Koerper stillhalten. */
    public static void startTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            if (mode != OFF) reset();
            return;
        }
        int want = RenderMods.freecam ? FREECAM : RenderMods.freeLook ? FREELOOK : OFF;
        // Freecam erst starten, wenn der Koerper am Boden steht: in der Luft "eingefroren" saehe er
        // fuer den Server aus, als wuerde er schweben
        if (want == FREECAM && mode != FREECAM && RenderMods.freecamWaitGround
                && !p.onGround() && !p.isPassenger() && !p.getAbilities().flying && !p.isSpectator()) {
            if (!waitNoted) {
                waitNoted = true;
                p.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "\u00a7e1912: Freecam startet, sobald du am Boden stehst"));
            }
            want = mode == FREELOOK ? OFF : mode;
        } else {
            waitNoted = false;
        }
        if (want != mode) {
            if (mode != OFF) stop(mc);
            if (want != OFF) start(mc, p, want);
        }
        if (mode == FREELOOK) {
            // Blickrichtung merken, fuer die Bewegung kurz die Laufrichtung einsetzen
            lookYaw = p.getYRot();
            p.setYRot(walkYaw);
            p.yRotO = walkYaw;
            return;
        }
        if (mode == OFF || cam == null) return;
        // Jemand anderes (z.B. Zuschauer-Modus) hat die Kamera uebernommen
        if (mc.getCameraEntity() != cam) {
            mode = OFF;
            cam = null;
            RenderMods.freecam = false;
            RenderMods.freeLook = false;
            return;
        }
        transferRotation(p);
        if (mode == FREECAM) {
            // Der Koerper bleibt stehen: Bewegungstasten gelten nur fuer die Kamera
            releaseKeys(mc);
            if (RenderMods.freecamBlockClicks) {
                while (mc.options.keyAttack.consumeClick()) {
                    // Klicks verwerfen
                }
                while (mc.options.keyUse.consumeClick()) {
                    // Klicks verwerfen
                }
            }
        }
    }

    /** Tick-Ende: Kamera bewegen (Freecam) bzw. dem Spieler folgen lassen (Free Look). */
    public static void endTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (mode == FREELOOK && p != null) {
            // Nach der Bewegung wieder die Blickrichtung herstellen
            p.setYRot(lookYaw);
            p.yRotO = lookYaw;
            return;
        }
        if (mode != FREECAM || cam == null || p == null) return;
        cam.xo = cam.getX();
        cam.yo = cam.getY();
        cam.zo = cam.getZ();
        if (mc.gui.screen() != null) return;
        long win = GLFW.glfwGetCurrentContext();
        if (win == 0L) return;
        double f = (down(mc.options.keyUp, win) ? 1 : 0) - (down(mc.options.keyDown, win) ? 1 : 0);
        double s = (down(mc.options.keyLeft, win) ? 1 : 0) - (down(mc.options.keyRight, win) ? 1 : 0);
        double u = (down(mc.options.keyJump, win) ? 1 : 0) - (down(mc.options.keyShift, win) ? 1 : 0);
        double speed = RenderMods.freecamSpeed / 20.0 * (down(mc.options.keySprint, win) ? 2.0 : 1.0);
        double len = Math.sqrt(f * f + s * s);
        if (len > 1e-6) {
            f /= len;
            s /= len;
        }
        double yaw = Math.toRadians(camYaw);
        double dx = (-Math.sin(yaw) * f + Math.cos(yaw) * s) * speed;
        double dz = (Math.cos(yaw) * f + Math.sin(yaw) * s) * speed;
        double dy = u * speed;
        cam.setPos(cam.getX() + dx, cam.getY() + dy, cam.getZ() + dz);
    }

    // ---------- Tasten ----------

    /** Physischer Tastenzustand (unabhaengig davon, was das Spiel gerade fuer die Taste haelt). */
    static boolean down(KeyMapping k, long win) {
        InputConstants.Key key = InputConstants.getKey(k.saveString());
        if (key.getType() != InputConstants.Type.KEYSYM) return false;
        int code = key.getValue();
        return code >= 0 && GLFW.glfwGetKey(win, code) == GLFW.GLFW_PRESS;
    }

    private static void releaseKeys(Minecraft mc) {
        KeyMapping[] keys = {mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight,
                mc.options.keyJump, mc.options.keyShift, mc.options.keySprint};
        for (KeyMapping k : keys) k.setDown(false);
    }
}
