package de.test;

import java.util.ArrayDeque;
import java.util.Deque;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Weitere Bewegungs-Module: Air Jump, Anchor, Anti Void, Auto Jump, Auto Wasp, Blink, Click Tp,
 * Elytra Boost, Elytra Fly, Entity Control, Fast Climb, Flight, Gui Move.
 */
public final class Movement2 {
    private Movement2() {
    }

    // ---------- Schalter + Einstellungen ----------

    public static boolean airJump = false;
    public static int airJumpMax = 0;           // Spruenge in der Luft, 0 = unbegrenzt
    public static int airJumpPower = 42;        // Sprungkraft x 0,01 (42 = normal)

    public static boolean anchor = false;
    public static int anchorHeight = 10;
    public static int anchorPull = 50;          // Prozent

    public static boolean antiVoid = false;
    public static int antiVoidMode = 0;         // 0 = Hochspringen, 1 = Schweben
    public static final String[] ANTI_VOID_MODES = {"Hochspringen", "Schweben"};

    public static boolean autoJump = false;
    public static boolean autoJumpMoving = true;

    public static boolean autoWasp = false;
    public static int waspRange = 64;
    public static int waspSpeed = 30;           // Bloecke pro Sekunde

    public static boolean blink = false;
    public static int blinkAuto = 0;            // Sekunden bis zum automatischen Senden, 0 = aus

    public static boolean clickTp = false;
    public static int clickTpRange = 5;

    public static boolean elytraBoost = false;
    public static int elytraBoostPower = 3;
    public static int elytraBoostMax = 40;      // Bloecke pro Sekunde
    public static boolean elytraBoostKey = true;

    public static boolean elytraFly = false;
    public static int elytraFlySpeed = 25;
    public static int elytraFlyVertical = 10;

    public static boolean entityControl = false;
    public static int entitySpeed = 12;

    public static boolean fastClimb = false;
    public static int fastClimbSpeed = 6;

    public static boolean flight = false;
    public static int flightMode = 0;           // 0 = Tempo, 1 = Kreativ-Flug
    public static final String[] FLIGHT_MODES = {"Tempo", "Kreativ-Flug"};
    public static int flightSpeed = 11;
    public static int flightVertical = 8;
    public static boolean flightAntiKick = true;

    public static boolean guiMove = false;
    public static boolean guiMoveSneak = false;

    // ---------- Zustand ----------

    private static boolean jumpWasDown = false;
    private static int airJumpsUsed = 0;
    private static boolean useWasDown = false;
    private static boolean autoJumpPressed = false;
    private static boolean flightApplied = false;
    private static int flightTicks = 0;

    // Blink: zurueckgehaltene Bewegungs-Pakete
    private static final Deque<Packet<?>> HELD = new ArrayDeque<>();
    private static boolean flushing = false;
    private static boolean hookSeen = false;
    private static boolean hookWarned = false;
    private static int blinkTicks = 0;

    // ---------- Blink: wird vom Mixin fuer jedes ausgehende Paket aufgerufen ----------

    /** true = Paket zurueckhalten (das Mixin bricht das Senden dann ab). */
    public static boolean intercept(Packet<?> packet) {
        hookSeen = true;
        if (!blink || flushing) return false;
        if (!(packet instanceof ServerboundMovePlayerPacket)) return false;
        HELD.add(packet);
        return true;
    }

    private static void flush(Minecraft mc) {
        flushing = true;
        try {
            while (!HELD.isEmpty()) {
                PlayerMods.sendRaw(HELD.poll());
            }
        } finally {
            flushing = false;
        }
        blinkTicks = 0;
    }

    public static int heldPackets() {
        return HELD.size();
    }

    // ---------- Tick-Anfang (vor der Spieler-Bewegung) ----------

    public static void startTick(Minecraft mc) {
        if (guiMove) runGuiMove(mc);
    }

    /** Gibt an, ob die Bewegungs-Module trotz offenem Menue arbeiten duerfen (Gui Move). */
    public static boolean screenAllowsMovement(Minecraft mc) {
        Screen s = mc.gui.screen();
        if (s == null) return true;
        if (!guiMove) return false;
        return !(s instanceof ChatScreen) && !(s.getFocused() instanceof EditBox);
    }

    private static void runGuiMove(Minecraft mc) {
        Screen s = mc.gui.screen();
        if (s == null || !screenAllowsMovement(mc)) return;
        long win = GLFW.glfwGetCurrentContext();
        if (win == 0L) return;
        KeyMapping[] keys = {mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft,
                mc.options.keyRight, mc.options.keyJump, mc.options.keySprint};
        for (KeyMapping k : keys) setFromKeyboard(k, win);
        if (guiMoveSneak) setFromKeyboard(mc.options.keyShift, win);
    }

    private static void setFromKeyboard(KeyMapping k, long win) {
        InputConstants.Key key = InputConstants.getKey(k.saveString());
        if (key.getType() != InputConstants.Type.KEYSYM) return;
        int code = key.getValue();
        if (code < 0) return;
        k.setDown(GLFW.glfwGetKey(win, code) == GLFW.GLFW_PRESS);
    }

    // ---------- Tick-Ende ----------

    public static void endTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            HELD.clear();
            return;
        }

        // Blink
        if (blink) {
            if (!hookSeen && !hookWarned && p.tickCount > 40) {
                hookWarned = true;
                p.sendSystemMessage(Component.literal(
                        "1912: Blink funktioniert in dieser Version nicht (Paket-Hook fehlt)"));
            }
            blinkTicks++;
            if (blinkAuto > 0 && blinkTicks >= blinkAuto * 20) flush(mc);
        } else if (!HELD.isEmpty()) {
            flush(mc);
        }

        boolean bot = TestClient.areaActive;
        boolean canMove = screenAllowsMovement(mc);
        boolean fwd = canMove && mc.options.keyUp.isDown();
        boolean back = canMove && mc.options.keyDown.isDown();
        boolean left = canMove && mc.options.keyLeft.isDown();
        boolean right = canMove && mc.options.keyRight.isDown();
        boolean jump = canMove && mc.options.keyJump.isDown();
        boolean sneak = canMove && mc.options.keyShift.isDown();
        boolean moving = fwd || back || left || right;
        double[] dir = moveDir(p, fwd, back, left, right);

        if (!bot) {
            if (airJump) runAirJump(p, jump);
            if (autoJump) runAutoJump(mc, p, moving);
            if (anchor) runAnchor(mc, p);
            if (fastClimb) runFastClimb(p, moving, jump);
            if (entityControl) runEntityControl(p, dir, jump);
            if (p.isFallFlying()) {
                if (elytraFly) runElytraFly(p, dir, jump, sneak);
                else if (autoWasp) runAutoWasp(mc, p);
                if (elytraBoost) runElytraBoost(p, fwd);
            }
            runFlight(p, dir, jump, sneak);
            if (clickTp) runClickTp(mc, p);
        } else if (flightApplied) {
            stopFlight(p);
        }
        if (antiVoid) runAntiVoid(mc, p);
        jumpWasDown = jump;
    }

    private static double[] moveDir(LocalPlayer p, boolean fwd, boolean back, boolean left, boolean right) {
        double f = (fwd ? 1 : 0) - (back ? 1 : 0);
        double s = (left ? 1 : 0) - (right ? 1 : 0);
        double len = Math.sqrt(f * f + s * s);
        if (len < 1e-6) return null;
        f /= len;
        s /= len;
        double yaw = Math.toRadians(p.getYRot());
        return new double[] {-Math.sin(yaw) * f + Math.cos(yaw) * s, Math.cos(yaw) * f + Math.sin(yaw) * s};
    }

    private static boolean solid(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    // ---------- Module ----------

    private static void runAirJump(LocalPlayer p, boolean jump) {
        if (p.onGround()) {
            airJumpsUsed = 0;
            return;
        }
        if (jump && !jumpWasDown && !p.isFallFlying() && !p.getAbilities().flying) {
            if (airJumpMax > 0 && airJumpsUsed >= airJumpMax) return;
            airJumpsUsed++;
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x, airJumpPower / 100.0, v.z);
        }
    }

    private static void runAutoJump(Minecraft mc, LocalPlayer p, boolean moving) {
        boolean want = p.onGround() && !p.isInWater() && (moving || !autoJumpMoving);
        if (want) {
            mc.options.keyJump.setDown(true);
            autoJumpPressed = true;
        } else if (autoJumpPressed) {
            mc.options.keyJump.setDown(false);
            autoJumpPressed = false;
        }
    }

    /** Zieht in ein 1x1-Loch darunter (z.B. Obsidian-Loch). */
    private static void runAnchor(Minecraft mc, LocalPlayer p) {
        if (p.onGround() || p.getDeltaMovement().y > 0) return;
        Level level = mc.level;
        BlockPos feet = p.blockPosition();
        for (int i = 0; i <= anchorHeight; i++) {
            BlockPos pos = feet.below(i);
            if (solid(level, pos)) return; // Weg nach unten versperrt
            boolean hole = solid(level, pos.below()) && solid(level, pos.north()) && solid(level, pos.south())
                    && solid(level, pos.east()) && solid(level, pos.west());
            if (!hole) continue;
            double k = anchorPull / 100.0;
            double dx = (pos.getX() + 0.5 - p.getX()) * k;
            double dz = (pos.getZ() + 0.5 - p.getZ()) * k;
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(dx, Math.min(v.y, -0.2), dz);
            return;
        }
    }

    private static void runAntiVoid(Minecraft mc, LocalPlayer p) {
        if (p.getAbilities().flying) return;
        if (p.getY() > mc.level.getMinY() - 2 || p.getDeltaMovement().y >= 0) return;
        Vec3 v = p.getDeltaMovement();
        if (antiVoidMode == 0) {
            p.setDeltaMovement(v.x, 1.0, v.z);
        } else {
            p.setDeltaMovement(v.x, 0, v.z);
        }
        p.fallDistance = 0;
    }

    private static void runFastClimb(LocalPlayer p, boolean moving, boolean jump) {
        if (!p.onClimbable() || !(jump || (moving && p.horizontalCollision))) return;
        double speed = fastClimbSpeed * 0.05;
        Vec3 v = p.getDeltaMovement();
        if (v.y < speed) p.setDeltaMovement(v.x, speed, v.z);
    }

    /** Reittiere und Boote, die man selbst steuert, schneller und in Blickrichtung bewegen. */
    private static void runEntityControl(LocalPlayer p, double[] dir, boolean jump) {
        Entity vehicle = p.getVehicle();
        if (vehicle == null || vehicle.getControllingPassenger() != p) return;
        vehicle.setYRot(p.getYRot());
        Vec3 v = vehicle.getDeltaMovement();
        if (dir == null) {
            vehicle.setDeltaMovement(v.x * 0.5, v.y, v.z * 0.5);
            return;
        }
        double perTick = entitySpeed / 20.0;
        double vy = v.y;
        if (jump && vehicle.onGround()) vy = 0.5;
        vehicle.setDeltaMovement(dir[0] * perTick, vy, dir[1] * perTick);
    }

    private static void runElytraFly(LocalPlayer p, double[] dir, boolean jump, boolean sneak) {
        double h = elytraFlySpeed / 20.0;
        double vSpeed = elytraFlyVertical / 20.0;
        double vx = dir == null ? 0 : dir[0] * h;
        double vz = dir == null ? 0 : dir[1] * h;
        double vy = jump ? vSpeed : sneak ? -vSpeed : 0;
        p.setDeltaMovement(vx, vy, vz);
    }

    private static void runElytraBoost(LocalPlayer p, boolean fwd) {
        if (elytraBoostKey && !fwd) return;
        Vec3 look = p.getLookAngle();
        Vec3 v = p.getDeltaMovement().add(look.scale(elytraBoostPower * 0.02));
        double max = elytraBoostMax / 20.0;
        double len = v.length();
        if (len > max) v = v.scale(max / len);
        p.setDeltaMovement(v);
    }

    /** Fliegt mit der Elytra zum naechsten Spieler. */
    private static void runAutoWasp(Minecraft mc, LocalPlayer p) {
        Player target = null;
        double best = (double) waspRange * waspRange;
        for (Player other : mc.level.players()) {
            if (other == p || !other.isAlive() || other.isSpectator()) continue;
            double d = other.distanceToSqr(p);
            if (d < best) {
                best = d;
                target = other;
            }
        }
        if (target == null) return;
        Vec3 to = target.position().add(0, 1.5, 0).subtract(p.position());
        double dist = to.length();
        if (dist < 2.0) {
            p.setDeltaMovement(target.getDeltaMovement());
            return;
        }
        double speed = Math.min(waspSpeed / 20.0, dist);
        p.setDeltaMovement(to.scale(speed / dist));
        // In Flugrichtung schauen
        p.setYRot((float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0));
    }

    private static void runFlight(LocalPlayer p, double[] dir, boolean jump, boolean sneak) {
        if (!flight) {
            if (flightApplied) stopFlight(p);
            return;
        }
        flightApplied = true;
        flightTicks++;
        boolean antiKickTick = flightAntiKick && flightTicks % 40 == 0;
        if (flightMode == 1) {
            Abilities a = p.getAbilities();
            a.mayfly = true;
            a.flying = true;
            a.setFlyingSpeed(flightSpeed / 200f);
            if (antiKickTick && !jump) {
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(v.x, -0.04, v.z);
            }
            return;
        }
        double h = flightSpeed / 20.0;
        double vSpeed = flightVertical / 20.0;
        double vx = dir == null ? 0 : dir[0] * h;
        double vz = dir == null ? 0 : dir[1] * h;
        double vy = jump ? vSpeed : sneak ? -vSpeed : 0;
        if (antiKickTick && !jump) vy = -0.04;
        p.setDeltaMovement(vx, vy, vz);
        p.fallDistance = 0;
    }

    private static void stopFlight(LocalPlayer p) {
        flightApplied = false;
        flightTicks = 0;
        Abilities a = p.getAbilities();
        if (!p.isCreative() && !p.isSpectator()) {
            a.flying = false;
            a.mayfly = false;
        }
        a.setFlyingSpeed(0.05f);
    }

    /** Rechtsklick mit leerer Hand: auf den anvisierten Block teleportieren. */
    private static void runClickTp(Minecraft mc, LocalPlayer p) {
        boolean down = mc.gui.screen() == null && mc.options.keyUse.isDown();
        boolean pressed = down && !useWasDown;
        useWasDown = down;
        if (!pressed || !p.getMainHandItem().isEmpty()) return;
        HitResult hit = p.pick(clickTpRange + 0.5, 1f, false);
        if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos target = bh.getBlockPos().relative(bh.getDirection());
        if (solid(mc.level, target) || solid(mc.level, target.above())) return;
        p.setPos(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
    }

    // ---------- Einstellungs-Menues ----------

    public static Screen airJumpScreen(Screen parent) {
        return new ModuleScreen(parent, "Air Jump", f -> {
            f.description("Springen auch in der Luft.");
            f.section("Allgemein");
            f.slider("Spr\u00fcnge (0 = unbegrenzt)", 0, 10, () -> airJumpMax, v -> airJumpMax = v, 0);
            f.slider("Sprungkraft", 20, 100, () -> airJumpPower, v -> airJumpPower = v, 42);
            f.footer(() -> airJump, v -> airJump = v);
        });
    }

    public static Screen anchorScreen(Screen parent) {
        return new ModuleScreen(parent, "Anchor", f -> {
            f.description("Zieht dich in ein 1x1-Loch unter dir.");
            f.section("Allgemein");
            f.slider("Max. Höhe", 1, 20, () -> anchorHeight, v -> anchorHeight = v, 10);
            f.slider("Zugkraft (%)", 10, 100, () -> anchorPull, v -> anchorPull = v, 50);
            f.footer(() -> anchor, v -> anchor = v);
        });
    }

    public static Screen antiVoidScreen(Screen parent) {
        return new ModuleScreen(parent, "Anti Void", f -> {
            f.description("Rettet dich, wenn du in die Leere fällst.");
            f.section("Allgemein");
            f.choice("Modus", ANTI_VOID_MODES, () -> antiVoidMode, v -> antiVoidMode = v, 0);
            f.footer(() -> antiVoid, v -> antiVoid = v);
        });
    }

    public static Screen autoJumpScreen(Screen parent) {
        return new ModuleScreen(parent, "Auto Jump", f -> {
            f.description("Springt automatisch, sobald du am Boden bist.");
            f.section("Allgemein");
            f.toggle("Nur beim Laufen", () -> autoJumpMoving, v -> autoJumpMoving = v, true);
            f.footer(() -> autoJump, v -> autoJump = v);
        });
    }

    public static Screen autoWaspScreen(Screen parent) {
        return new ModuleScreen(parent, "Auto Wasp", f -> {
            f.description("Fliegt mit der Elytra automatisch\nzum nächsten Spieler.");
            f.section("Allgemein");
            f.slider("Reichweite", 8, 256, () -> waspRange, v -> waspRange = v, 64);
            f.slider("Tempo (Blöcke/s)", 5, 80, () -> waspSpeed, v -> waspSpeed = v, 30);
            f.footer(() -> autoWasp, v -> autoWasp = v);
        });
    }

    public static Screen blinkScreen(Screen parent) {
        return new ModuleScreen(parent, "Blink", f -> {
            f.description("Hält deine Bewegung zurück. Beim Ausschalten\nspringst du für andere zur neuen Stelle.");
            f.section("Allgemein");
            f.slider("Auto-Senden (s, 0=aus)", 0, 30, () -> blinkAuto, v -> blinkAuto = v, 0);
            f.note(() -> "Zurückgehalten: " + heldPackets() + " Pakete");
            f.footer(() -> blink, v -> blink = v);
        });
    }

    public static Screen clickTpScreen(Screen parent) {
        return new ModuleScreen(parent, "Click Tp", f -> {
            f.description("Rechtsklick mit leerer Hand teleportiert\ndich auf den anvisierten Block.");
            f.section("Allgemein");
            f.slider("Reichweite", 1, 8, () -> clickTpRange, v -> clickTpRange = v, 5);
            f.footer(() -> clickTp, v -> clickTp = v);
        });
    }

    public static Screen elytraBoostScreen(Screen parent) {
        return new ModuleScreen(parent, "Elytra Boost", f -> {
            f.description("Beschleunigt beim Elytra-Fliegen\nin Blickrichtung.");
            f.section("Allgemein");
            f.slider("Stärke", 1, 10, () -> elytraBoostPower, v -> elytraBoostPower = v, 3);
            f.slider("Max. Tempo (Blöcke/s)", 10, 100, () -> elytraBoostMax, v -> elytraBoostMax = v, 40);
            f.toggle("Nur mit Vorwärts-Taste", () -> elytraBoostKey, v -> elytraBoostKey = v, true);
            f.footer(() -> elytraBoost, v -> elytraBoost = v);
        });
    }

    public static Screen elytraFlyScreen(Screen parent) {
        return new ModuleScreen(parent, "Elytra Fly", f -> {
            f.description("Steuert die Elytra frei: Laufen-Tasten,\nSpringen = hoch, Schleichen = runter.");
            f.section("Allgemein");
            f.slider("Tempo (Blöcke/s)", 5, 60, () -> elytraFlySpeed, v -> elytraFlySpeed = v, 25);
            f.slider("Hoch/Runter (Blöcke/s)", 2, 30, () -> elytraFlyVertical, v -> elytraFlyVertical = v, 10);
            f.footer(() -> elytraFly, v -> elytraFly = v);
        });
    }

    public static Screen entityControlScreen(Screen parent) {
        return new ModuleScreen(parent, "Entity Control", f -> {
            f.description("Reittiere und Boote fahren schneller\nund in Blickrichtung.");
            f.section("Allgemein");
            f.slider("Tempo (Blöcke/s)", 4, 40, () -> entitySpeed, v -> entitySpeed = v, 12);
            f.footer(() -> entityControl, v -> entityControl = v);
        });
    }

    public static Screen fastClimbScreen(Screen parent) {
        return new ModuleScreen(parent, "Fast Climb", f -> {
            f.description("Klettert Leitern und Ranken schneller.");
            f.section("Allgemein");
            f.slider("Tempo", 1, 10, () -> fastClimbSpeed, v -> fastClimbSpeed = v, 6);
            f.footer(() -> fastClimb, v -> fastClimb = v);
        });
    }

    public static Screen flightScreen(Screen parent) {
        return new ModuleScreen(parent, "Flight", f -> {
            f.description("Fliegen wie im Kreativ-Modus.\nSpringen = hoch, Schleichen = runter.");
            f.section("Allgemein");
            f.choice("Modus", FLIGHT_MODES, () -> flightMode, v -> flightMode = v, 0);
            f.slider("Tempo (Blöcke/s)", 2, 60, () -> flightSpeed, v -> flightSpeed = v, 11);
            f.slider("Hoch/Runter (Blöcke/s)", 2, 30, () -> flightVertical, v -> flightVertical = v, 8);
            f.toggle("Anti-Kick", () -> flightAntiKick, v -> flightAntiKick = v, true);
            f.footer(() -> flight, v -> flight = v);
        });
    }

    public static Screen guiMoveScreen(Screen parent) {
        return new ModuleScreen(parent, "Gui Move", f -> {
            f.description("Laufen und Springen, während ein Menü\n(z.B. Inventar) offen ist. Nicht beim Tippen.");
            f.section("Allgemein");
            f.toggle("Auch Schleichen", () -> guiMoveSneak, v -> guiMoveSneak = v, false);
            f.footer(() -> guiMove, v -> guiMove = v);
        });
    }
}
