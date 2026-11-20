package de.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Elytra-Autopilot, Elytra-Tausch und Todespunkt. */
public final class TravelMods {
    private TravelMods() {
    }

    // =====================================================================================
    // Einstellungen (werden mit dem Praefix "tv." gespeichert)
    // =====================================================================================

    // --- Elytra-Autopilot ---
    public static boolean autopilot = false;
    public static String apTarget = "";        // "X Z"
    public static int apHeight = 200;          // Flughoehe
    public static boolean apRockets = true;
    public static boolean apLand = true;

    // --- Elytra-Tausch ---
    public static boolean elytraSwap = false;
    public static int swapAt = 10;             // so viel Haltbarkeit ist noch uebrig

    // --- Todespunkt ---
    public static boolean deathPoint = false;
    public static boolean dpChat = true;
    public static boolean dpWaypoint = true;
    public static boolean dpOnlyLast = true;

    static void reset() {
        if (apStage != 0) apStop(Minecraft.getInstance());
        swapWarned = false;
    }

    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) {
            reset();
            return;
        }
        Safe.run("Todespunkt", () -> deathTick(mc, p));
        if (!p.isAlive()) return;
        Safe.run("Elytra-Tausch", () -> swapTick(mc, p));
        Safe.run("Elytra-Autopilot", () -> autopilotTick(mc, p));
    }

    private static boolean isElytra(ItemStack st) {
        return !st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().equals("elytra");
    }

    private static boolean isRocket(ItemStack st) {
        return !st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().equals("firework_rocket");
    }

    // =====================================================================================
    // Elytra-Autopilot
    // =====================================================================================

    private static int apStage;        // 0 aus, 1 starten, 2 fliegen, 3 landen
    private static int apTicks;
    private static int apRocketTimer;
    private static int apJump;

    /** "X Z" (auch mit Komma oder Y dazwischen: "X Y Z") -> {X, Z}; null = ungueltig. */
    static int[] parseTarget(String s) {
        if (s == null) return null;
        String[] parts = s.trim().replace(',', ' ').split("\\s+");
        try {
            if (parts.length == 2) return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
            if (parts.length == 3) return new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException e) {
            return null;
        }
        return null;
    }

    private static void apStop(Minecraft mc) {
        apStage = 0;
        if (mc.options != null) mc.options.keyJump.setDown(false);
    }

    private static void apFail(Minecraft mc, String why) {
        autopilot = false;
        apStop(mc);
        Keybinds.message(Lang.t("Elytra-Autopilot: ") + Lang.t(why), "");
    }

    private static int ground(Level level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
    }

    /** Blick hoechstens ein Stueck pro Tick drehen (sonst springt die Kamera). */
    private static void look(LocalPlayer p, float yaw, float pitch) {
        float dy = Mth.clamp(Mth.wrapDegrees(yaw - p.getYRot()), -15f, 15f);
        float dp = Mth.clamp(pitch - p.getXRot(), -8f, 8f);
        p.turn(dy / 0.15, dp / 0.15);
    }

    private static boolean useRocket(Minecraft mc, LocalPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            if (!isRocket(inv.getItem(i))) continue;
            int prev = inv.getSelectedSlot();
            inv.setSelectedSlot(i);
            mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
            inv.setSelectedSlot(prev);
            return true;
        }
        return false;
    }

    private static boolean hasRocket(LocalPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            if (isRocket(inv.getItem(i))) return true;
        }
        return false;
    }

    private static void autopilotTick(Minecraft mc, LocalPlayer p) {
        if (!autopilot) {
            if (apStage != 0) apStop(mc);
            return;
        }
        int[] t = parseTarget(apTarget);
        if (t == null) {
            apFail(mc, "Ziel eingeben (X Z)");
            return;
        }
        if (!isElytra(p.getItemBySlot(EquipmentSlot.CHEST))) {
            apFail(mc, "keine Elytra angezogen");
            return;
        }
        if (p.isPassenger() || p.isSpectator()) return;
        Level level = mc.level;
        double dx = t[0] + 0.5 - p.getX();
        double dz = t[1] + 0.5 - p.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        if (apStage == 0) {
            apStage = p.isFallFlying() ? 2 : 1;
            apTicks = 0;
            apJump = 0;
        }
        apTicks++;
        if (apRocketTimer > 0) apRocketTimer--;

        if (apStage == 1) { // starten: springen, in der Luft noch einmal springen -> gleiten
            if (p.isFallFlying()) {
                mc.options.keyJump.setDown(false);
                apStage = 2;
                return;
            }
            if (apTicks > 20 * 8) {
                apFail(mc, "konnte nicht losfliegen (Platz nach oben?)");
                return;
            }
            mc.options.keyJump.setDown(apJump++ % 3 == 0);
            look(p, yaw, -20f);
            return;
        }

        if (apStage == 2) { // fliegen
            mc.options.keyJump.setDown(false);
            if (!p.isFallFlying()) {
                if (p.onGround() && dist < 40) {
                    arrived(mc);
                } else {
                    apStage = 1; // abgestuerzt oder gelandet: neu starten
                    apTicks = 0;
                }
                return;
            }
            Vec3 v = p.getDeltaMovement();
            double hs = Math.sqrt(v.x * v.x + v.z * v.z);
            double y = p.getY();
            int terrain = Math.max(ground(level, p.getX(), p.getZ()),
                    ground(level, p.getX() + v.x * 30, p.getZ() + v.z * 30));
            double want = Math.max(apHeight, terrain + 20);
            boolean rockets = apRockets && hasRocket(p);
            if (apLand && dist < Math.max(40, (y - terrain) * 1.3)) {
                apStage = 3;
                return;
            }
            float pitch;
            boolean rocket = false;
            if (y < terrain + 8) { // gleich unten: hochziehen
                pitch = -35f;
                rocket = rockets;
            } else if (y < want - 4) {
                pitch = rockets ? -30f : 3f; // ohne Raketen kann er nicht steigen, nur flach gleiten
                rocket = rockets && (hs < 1.4 || v.y < 0.2);
            } else if (y > want + 6) {
                pitch = 15f;
            } else {
                pitch = rockets ? 0f : 6f;
                rocket = rockets && hs < 1.0;
            }
            look(p, yaw, pitch);
            if (rocket && apRocketTimer <= 0 && useRocket(mc, p)) apRocketTimer = 40;
            return;
        }

        // landen
        if (!p.isFallFlying()) {
            if (p.onGround() || p.isInWater()) arrived(mc);
            else apStage = 1;
            return;
        }
        double above = p.getY() - ground(level, p.getX(), p.getZ());
        float pitch = dist > 6 ? (float) Mth.clamp(Math.toDegrees(Math.atan2(above, dist)), 10.0, 50.0) : 40f;
        if (above < 4) pitch = 5f; // flach aufsetzen
        look(p, yaw, pitch);
    }

    private static void arrived(Minecraft mc) {
        autopilot = false;
        apStop(mc);
        Keybinds.message(Lang.t("Elytra-Autopilot: angekommen"), "");
    }

    // =====================================================================================
    // Elytra-Tausch
    // =====================================================================================

    private static int swapCooldown;
    private static boolean swapWarned;

    private static void swapTick(Minecraft mc, LocalPlayer p) {
        if (!elytraSwap) return;
        if (swapCooldown > 0) {
            swapCooldown--;
            return;
        }
        ItemStack worn = p.getItemBySlot(EquipmentSlot.CHEST);
        if (!isElytra(worn) || !worn.isDamageableItem()) return;
        int left = worn.getMaxDamage() - worn.getDamageValue();
        if (left > swapAt) {
            swapWarned = false;
            return;
        }
        if (mc.gui.screen() != null || p.containerMenu != p.inventoryMenu) return;
        if (!p.inventoryMenu.getCarried().isEmpty()) return;
        var inv = p.getInventory();
        int best = -1;
        int bestLeft = swapAt;
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (!isElytra(st)) continue;
            int l = st.isDamageableItem() ? st.getMaxDamage() - st.getDamageValue() : 1000;
            if (l > bestLeft) {
                bestLeft = l;
                best = i;
            }
        }
        if (best < 0) {
            if (!swapWarned) {
                swapWarned = true;
                Keybinds.message(Lang.t("Elytra-Tausch: keine Ersatz-Elytra im Inventar"), "");
            }
            return;
        }
        int slot = best < 9 ? 36 + best : best; // Platz im Inventar-Menue (6 = Brust)
        PlayerMods.click(p, slot, 0, "PICKUP");
        PlayerMods.click(p, 6, 0, "PICKUP");
        PlayerMods.click(p, slot, 0, "PICKUP");
        swapCooldown = 20;
        Keybinds.message(Lang.t("Elytra getauscht"), "");
    }

    // =====================================================================================
    // Todespunkt
    // =====================================================================================

    private static boolean wasDead;

    private static void deathTick(Minecraft mc, LocalPlayer p) {
        boolean dead = p.isDeadOrDying();
        if (dead && !wasDead && deathPoint) {
            BlockPos b = p.blockPosition();
            String dim = mc.level.dimension() == Level.NETHER ? "Nether"
                    : mc.level.dimension() == Level.END ? "End" : "Oberwelt";
            String coords = b.getX() + " " + b.getY() + " " + b.getZ();
            if (dpChat) {
                p.sendSystemMessage(Lang.literal("§c1912: " + Lang.t("Gestorben bei ") + coords + " ("
                        + Lang.t(dim) + ")"));
            }
            if (dpWaypoint) addWaypoint(b, dim);
        }
        wasDead = dead;
    }

    private static void addWaypoint(BlockPos b, String dim) {
        String name = Lang.t("Todespunkt") + (dim.equals("Oberwelt") ? "" : " (" + Lang.t(dim) + ")");
        StringBuilder list = new StringBuilder();
        if (RenderMods.waypointList != null && !RenderMods.waypointList.isEmpty()) {
            for (String e : RenderMods.waypointList.split(";")) {
                if (dpOnlyLast && (e.startsWith(Lang.t("Todespunkt")) || e.startsWith("Todespunkt"))) continue;
                if (list.length() > 0) list.append(';');
                list.append(e);
            }
        }
        if (list.length() > 0) list.append(';');
        list.append(name.replaceAll("[,;]", " ")).append(',').append(b.getX()).append(',').append(b.getY())
                .append(',').append(b.getZ());
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null) list.append(',').append(RenderMods.dimId(level));
        RenderMods.waypointList = list.toString();
        RenderMods.waypoints = true; // sonst sieht man ihn nicht
        TestConfig.save();
    }

    // =====================================================================================
    // Einstellungen
    // =====================================================================================

    static Screen screen(String module, Screen parent) {
        return switch (module) {
            case "Elytra-Autopilot" -> new ModuleScreen(parent, module, f -> {
                f.description("Fliegt mit der Elytra selbst zu den Koordinaten: losfliegen, auf Höhe gehen, "
                        + "Raketen zünden und am Ziel landen.");
                f.text("Ziel (X Z)", () -> apTarget, v -> apTarget = v, "");
                f.slider("Flughöhe (Y)", 70, 320, () -> apHeight, v -> apHeight = v, 200);
                f.toggle("Raketen benutzen", () -> apRockets, v -> apRockets = v, true);
                f.toggle("Am Ziel landen", () -> apLand, v -> apLand = v, true);
                f.note("Elytra anziehen, Raketen in die Hotbar, dann einschalten. Ohne Raketen kann er nur gleiten "
                        + "und kommt nicht weit. Über Bergen fliegt er automatisch höher. Ausschalten = sofort "
                        + "selbst steuern.");
                f.footer(() -> autopilot, v -> autopilot = v);
            });
            case "Elytra-Tausch" -> new ModuleScreen(parent, module, f -> {
                f.description("Zieht eine neue Elytra aus dem Inventar an, bevor die alte kaputtgeht - auch "
                        + "mitten im Flug.");
                f.slider("Tauschen bei (Haltbarkeit übrig)", 2, 60, () -> swapAt, v -> swapAt = v, 10);
                f.note("Eine Elytra hat neu 432 Haltbarkeit, im Flug verliert sie 1 pro Sekunde.");
                f.footer(() -> elytraSwap, v -> elytraSwap = v);
            });
            case "Todespunkt" -> new ModuleScreen(parent, module, f -> {
                f.description("Merkt sich, wo du gestorben bist - damit du deine Sachen wiederfindest.");
                f.toggle("Koordinaten in den Chat", () -> dpChat, v -> dpChat = v, true);
                f.toggle("Wegpunkt setzen", () -> dpWaypoint, v -> dpWaypoint = v, true);
                f.toggle("Nur den letzten behalten", () -> dpOnlyLast, v -> dpOnlyLast = v, true);
                f.note("Der Wegpunkt heißt „Todespunkt“ und steht bei den Waypoints. Die werden dafür "
                        + "eingeschaltet.");
                f.footer(() -> deathPoint, v -> deathPoint = v);
            });
            default -> null;
        };
    }
}
