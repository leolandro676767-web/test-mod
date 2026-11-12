package de.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Gemeinsame Hilfe fuer alle Render-Module: projiziert Weltpunkte selbst auf das HUD
 * (ohne Welt-Render-Mixins). So lassen sich Kaesten, Linien und Beschriftungen "durch Waende"
 * zeichnen. Wird pro Frame mit {@link #begin} vorbereitet.
 */
public final class Render3D {
    private Render3D() {
    }

    private static double camX, camY, camZ, sinYaw, cosYaw, sinPitch, cosPitch, focal;
    private static int screenW, screenH;
    private static float partial;
    private static boolean ready;

    /** Nur in der Ego-Ansicht zuverlaessig; sonst meldet das keine gueltige Projektion. */
    public static boolean begin(Minecraft mc, float pt) {
        ready = false;
        if (mc.player == null || mc.level == null) return false;
        if (!mc.options.getCameraType().isFirstPerson()) return false;
        partial = pt;
        Vec3 eye = mc.player.getEyePosition(pt);
        camX = eye.x;
        camY = eye.y;
        camZ = eye.z;
        double yaw = Math.toRadians(mc.player.getViewYRot(pt));
        double pitch = Math.toRadians(mc.player.getViewXRot(pt));
        sinYaw = Math.sin(yaw);
        cosYaw = Math.cos(yaw);
        sinPitch = Math.sin(pitch);
        cosPitch = Math.cos(pitch);
        screenW = mc.getWindow().getGuiScaledWidth();
        screenH = mc.getWindow().getGuiScaledHeight();
        double fov = mc.options.fov().get();
        focal = 1.0 / Math.tan(Math.toRadians(fov) / 2.0) * (screenH / 2.0);
        ready = true;
        return true;
    }

    public static boolean ready() {
        return ready;
    }

    public static float partial() {
        return partial;
    }

    public static double camX() {
        return camX;
    }

    public static double camY() {
        return camY;
    }

    public static double camZ() {
        return camZ;
    }

    public static int screenW() {
        return screenW;
    }

    public static int screenH() {
        return screenH;
    }

    /** {x, y, vorne}: vorne &gt; 0 heisst vor der Kamera. */
    public static double[] project(double wx, double wy, double wz) {
        double dx = wx - camX;
        double dy = wy - camY;
        double dz = wz - camZ;
        double fwdH = -sinYaw * dx + cosYaw * dz;
        double right = -cosYaw * dx - sinYaw * dz;
        double fwd = fwdH * cosPitch - dy * sinPitch;
        double up = fwdH * sinPitch + dy * cosPitch;
        if (fwd <= 0.05) return new double[] {right, -up, fwd};
        return new double[] {screenW / 2.0 + right / fwd * focal, screenH / 2.0 - up / fwd * focal, fwd};
    }

    /** Zwischen den Ticks geglaettete Hitbox eines Wesens. */
    public static AABB smoothBox(Entity e) {
        double ix = lerp(e.xo, e.getX()) - e.getX();
        double iy = lerp(e.yo, e.getY()) - e.getY();
        double iz = lerp(e.zo, e.getZ()) - e.getZ();
        return e.getBoundingBox().move(ix, iy, iz);
    }

    public static Vec3 smoothPos(Entity e) {
        return new Vec3(lerp(e.xo, e.getX()), lerp(e.yo, e.getY()), lerp(e.zo, e.getZ()));
    }

    private static double lerp(double a, double b) {
        return a + (b - a) * partial;
    }

    // ---------- Zeichnen ----------

    /** Umschliessender 2D-Kasten einer Welt-Hitbox. Gibt false zurueck, wenn nichts sichtbar ist. */
    public static boolean box(GuiGraphicsExtractor g, AABB bb, int color, boolean filled) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            double[] p = project((i & 1) == 0 ? bb.minX : bb.maxX,
                    (i & 2) == 0 ? bb.minY : bb.maxY,
                    (i & 4) == 0 ? bb.minZ : bb.maxZ);
            if (p[2] <= 0.05) return false;
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0]);
            maxY = Math.max(maxY, p[1]);
        }
        if (maxX < 0 || maxY < 0 || minX > screenW || minY > screenH) return false;
        int x1 = (int) Math.floor(minX);
        int y1 = (int) Math.floor(minY);
        int x2 = (int) Math.ceil(maxX);
        int y2 = (int) Math.ceil(maxY);
        if (x2 - x1 < 2) x2 = x1 + 2;
        if (y2 - y1 < 2) y2 = y1 + 2;
        if (filled) {
            Ui.fill(g, x1, y1, x2, y2, (color & 0x00FFFFFF) | 0x33000000);
        }
        outline(g, x1, y1, x2, y2, color);
        return true;
    }

    public static void outline(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        Ui.fill(g, x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0x90000000);
        Ui.fill(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, 0x90000000);
        Ui.fill(g, x1, y1, x2, y1 + 1, color);
        Ui.fill(g, x1, y2 - 1, x2, y2, color);
        Ui.fill(g, x1, y1 + 1, x1 + 1, y2 - 1, color);
        Ui.fill(g, x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    /** Linie von der Bildschirmmitte zu einem Weltpunkt (Tracer). */
    public static void tracer(GuiGraphicsExtractor g, Vec3 target, int color) {
        double[] c = project(target.x, target.y, target.z);
        float sx = screenW / 2f;
        float sy = screenH / 2f;
        float ex;
        float ey;
        if (c[2] > 0.05) {
            ex = (float) c[0];
            ey = (float) c[1];
        } else {
            double len = Math.max(1e-6, Math.hypot(c[0], c[1]));
            double reach = screenW + screenH;
            ex = (float) (sx + c[0] / len * reach);
            ey = (float) (sy + c[1] / len * reach);
        }
        line(g, sx, sy, ex, ey, color);
    }

    /** Linie zwischen zwei Weltpunkten. */
    public static void worldLine(GuiGraphicsExtractor g, Vec3 a, Vec3 b, int color) {
        double[] pa = project(a.x, a.y, a.z);
        double[] pb = project(b.x, b.y, b.z);
        if (pa[2] <= 0.05 || pb[2] <= 0.05) return;
        line(g, (float) pa[0], (float) pa[1], (float) pb[0], (float) pb[1], color);
    }

    /** Linie als gedrehtes, 1 Pixel hohes Rechteck. */
    public static void line(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) return;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x1, y1);
        pose.rotate((float) Math.atan2(dy, dx));
        Ui.fill(g, 0, 0, Math.round(len), 1, color);
        pose.popMatrix();
    }

    /** Gefuelltes Quadrat an einem Weltpunkt (Marker-Punkt). */
    public static void dot(GuiGraphicsExtractor g, Vec3 at, int size, int color) {
        double[] p = project(at.x, at.y, at.z);
        if (p[2] <= 0.05) return;
        int x = (int) Math.round(p[0]);
        int y = (int) Math.round(p[1]);
        Ui.fill(g, x - size - 1, y - size - 1, x + size + 1, y + size + 1, 0x90000000);
        Ui.fill(g, x - size, y - size, x + size, y + size, color);
    }

    /** Text mittig ueber einem Weltpunkt. */
    public static void label(GuiGraphicsExtractor g, Minecraft mc, Vec3 at, String text, int color, int yOffset) {
        double[] p = project(at.x, at.y, at.z);
        if (p[2] <= 0.05) return;
        int tw = mc.font.width(text);
        int x = (int) Math.round(p[0]) - tw / 2;
        int y = (int) Math.round(p[1]) + yOffset;
        Ui.fill(g, x - 2, y - 1, x + tw + 2, y + 9, 0x90000000);
        Ui.text(g, mc.font, text, x, y, (color & 0x00FFFFFF) | 0xFF000000, true);
    }
}
