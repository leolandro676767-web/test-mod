package de.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Gemeinsame Kampf-Hilfen fuer Kill Aura und Bot-Verteidigung: Reichweite, Zielen, Waffen. */
final class Combat {
    private Combat() {
    }

    /** Abstand vom Auge zum naechsten Punkt der Hitbox (so misst auch Minecraft die Reichweite). */
    static double distance(Vec3 eye, AABB bb) {
        double x = Mth.clamp(eye.x, bb.minX, bb.maxX);
        double y = Mth.clamp(eye.y, bb.minY, bb.maxY);
        double z = Mth.clamp(eye.z, bb.minZ, bb.maxZ);
        return eye.distanceTo(new Vec3(x, y, z));
    }

    /** Der Punkt der Hitbox, der dem Auge am naechsten ist (etwas nach innen gerueckt). */
    static Vec3 aimPoint(LocalPlayer p, LivingEntity le) {
        AABB bb = le.getBoundingBox().deflate(0.1);
        Vec3 eye = p.getEyePosition();
        return new Vec3(Mth.clamp(eye.x, bb.minX, bb.maxX), Mth.clamp(eye.y, bb.minY, bb.maxY),
                Mth.clamp(eye.z, bb.minZ, bb.maxZ));
    }

    static float[] rotationTo(LocalPlayer p, Vec3 point) {
        double dx = point.x - p.getX();
        double dy = point.y - p.getEyeY();
        double dz = point.z - p.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = horiz < 1.0E-4 ? p.getYRot() : (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
        return new float[] {yaw, Mth.clamp(pitch, -90f, 90f)};
    }

    /** Dreht den Blick hoechstens speed Grad pro Tick in Richtung rot. */
    static void turn(LocalPlayer p, float[] rot, float speed) {
        float dy = Mth.wrapDegrees(rot[0] - p.getYRot());
        float dp = rot[1] - p.getXRot();
        p.setYRot(p.getYRot() + Mth.clamp(dy, -speed, speed));
        p.setXRot(Mth.clamp(p.getXRot() + Mth.clamp(dp, -speed, speed), -90f, 90f));
    }

    /** Liegt das Ziel unter dem Fadenkreuz? */
    static boolean looksAt(LocalPlayer p, LivingEntity le, double r) {
        Vec3 eye = p.getEyePosition();
        return rayHit(eye, eye.add(p.getLookAngle().scale(r + 1.0)), le.getBoundingBox().inflate(0.05)) >= 0;
    }

    /**
     * Trifft die Strecke from-to die Box? Ergebnis: Anteil der Strecke bis zum Eintritt (0..1),
     * 0 wenn from schon drin ist, -1 wenn nicht getroffen. (Eigene Rechnung statt AABB.clip, damit
     * keine Minecraft-Methode noetig ist, die sich zwischen Versionen aendern koennte.)
     */
    static double rayHit(Vec3 from, Vec3 to, AABB bb) {
        double t0 = 0;
        double t1 = 1;
        double[] o = {from.x, from.y, from.z};
        double[] d = {to.x - from.x, to.y - from.y, to.z - from.z};
        double[] lo = {bb.minX, bb.minY, bb.minZ};
        double[] hi = {bb.maxX, bb.maxY, bb.maxZ};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1.0E-9) {
                if (o[i] < lo[i] || o[i] > hi[i]) return -1;
                continue;
            }
            double a = (lo[i] - o[i]) / d[i];
            double b = (hi[i] - o[i]) / d[i];
            if (a > b) {
                double t = a;
                a = b;
                b = t;
            }
            t0 = Math.max(t0, a);
            t1 = Math.min(t1, b);
            if (t0 > t1) return -1;
        }
        return t0;
    }

    /** Blickrichtung nur dem Server schicken (die Kamera bleibt, wie sie ist). */
    static void sendRotation(Minecraft mc, LocalPlayer p, float[] rot) {
        var conn = mc.getConnection();
        if (conn != null) {
            conn.send(new ServerboundMovePlayerPacket.Rot(rot[0], rot[1], p.onGround(), p.horizontalCollision));
        }
    }

    // ---------- Waffen ----------

    private static String path(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
    }

    static boolean isWeapon(ItemStack st) {
        return weaponScore(st) >= 0;
    }

    /** Je hoeher, desto lieber: Schwerter vor Aexten, besseres Material zuerst; -1 = keine Waffe. */
    static int weaponScore(ItemStack st) {
        if (st.isEmpty()) return -1;
        String id = path(st);
        int material = id.startsWith("netherite_") ? 7 : id.startsWith("diamond_") ? 6 : id.startsWith("iron_") ? 5
                : id.startsWith("copper_") ? 4 : id.startsWith("stone_") ? 3 : id.startsWith("golden_") ? 2
                : id.startsWith("wooden_") ? 1 : 0;
        if (id.endsWith("_sword")) return 100 + material;
        if (id.endsWith("_axe")) return 60 + material;
        if (id.equals("trident")) return 55;
        if (id.equals("mace")) return 50;
        if (id.endsWith("_spear")) return 40 + material;
        return -1;
    }

    /** Nimmt die beste Waffe aus der Hotbar; true, wenn jetzt eine Waffe in der Hand ist. */
    static boolean selectWeapon(LocalPlayer p) {
        var inv = p.getInventory();
        int best = -1;
        int bestScore = weaponScore(inv.getItem(inv.getSelectedSlot()));
        for (int i = 0; i < 9; i++) {
            int s = weaponScore(inv.getItem(i));
            if (s > bestScore) {
                bestScore = s;
                best = i;
            }
        }
        if (best >= 0) inv.setSelectedSlot(best);
        return bestScore >= 0;
    }
}
