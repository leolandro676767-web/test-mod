package de.test;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Bewegungs-Module: High Jump, Jesus, Long Jump, No Fall, No Slow, Parkour, Reverse Step, Safe Walk,
 * Scaffold, Slippy, Sneak, Speed, Spider, Sprint, Step, Trident Boost, Velocity.
 * Alles laeuft clientseitig ueber Tick-Ereignisse, ohne Mixins.
 */
public final class Movement {
    private Movement() {
    }

    // ---------- Schalter + Einstellungen (werden in der Config gespeichert) ----------

    public static boolean slippy = false;
    public static int slippyAmount = 98;        // Rutschigkeit in % (Eis = 98)

    public static boolean sneak = false;
    public static boolean sneakOnlyMoving = false;

    public static boolean speed = false;
    public static int speedBps = 9;             // Bloecke pro Sekunde
    public static boolean speedGroundOnly = true;
    public static boolean speedAutoJump = false;

    public static boolean spider = false;
    public static int spiderSpeed = 4;          // x 0,05 Bloecke pro Tick

    public static boolean autoSprint = false;
    public static boolean sprintOmni = false;   // auch seitwaerts/rueckwaerts

    public static boolean step = false;
    public static int stepHeight = 1;           // Bloecke

    public static boolean trident = false;
    public static int tridentPower = 200;       // Prozent

    public static boolean velocity = false;
    public static int velocityH = 0;            // Prozent des Rueckstosses, der bleibt
    public static int velocityV = 0;

    public static boolean highJump = false;
    public static int highJumpHeight = 3;       // Bloecke

    public static boolean jesus = false;
    public static boolean jesusLava = false;

    public static boolean longJump = false;
    public static int longJumpPower = 250;      // Prozent

    public static boolean noFall = false;
    public static int noFallHeight = 3;          // ab dieser Fallhoehe eingreifen

    public static boolean noSlow = false;
    public static boolean noSlowFood = true;
    public static boolean noSlowShield = true;
    public static boolean noSlowBow = true;
    public static boolean noSlowOther = true;
    public static int noSlowPercent = 100;       // Tempo beim Benutzen (100 = normal)

    public static boolean parkour = false;
    public static int parkourLead = 1;           // Vorlauf in Ticks
    public static boolean parkourSprintOnly = false;

    public static boolean reverseStep = false;
    public static int reverseStepHeight = 3;    // Bloecke

    public static boolean safeWalk = false;
    public static int safeWalkDist = 4;          // Abstand zur Kante x 0,1 Bloecke
    public static boolean safeWalkNotJumping = true;

    public static boolean scaffold = false;
    public static boolean scaffoldLook = true;  // zum Platzieren nach unten schauen
    public static int scaffoldPerTick = 1;

    private static final double VANILLA_STEP = 0.6;
    private static final double VANILLA_JUMP = 0.42;

    private static boolean jumpApplied = false;
    private static double jumpForHeight = -1;
    private static int jumpHeightCached = -1;
    private static boolean wasOnGround = true;
    private static boolean parkourJumped = false;
    private static boolean scaffoldLooking = false;
    private static float scaffoldSavedPitch = 0f;
    private static int scaffoldIdle = 0;

    private static boolean sneakApplied = false;
    private static boolean stepApplied = false;
    private static boolean wasSpinning = false;
    private static int lastHurtTime = 0;

    // ---------- Tick-Anfang: Pakete sind verarbeitet, der Spieler hat sich aber noch nicht bewegt ----------

    public static void startTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) {
            lastHurtTime = 0;
            return;
        }
        // Velocity: frischen Treffer erkennen (hurtTime springt hoch) und den Rueckstoss abschwaechen
        int ht = p.hurtTime;
        if (velocity && ht > lastHurtTime && ht > 0) {
            Vec3 v = p.getDeltaMovement();
            double h = velocityH / 100.0;
            double vy = v.y > 0 ? v.y * (velocityV / 100.0) : v.y;
            p.setDeltaMovement(v.x * h, vy, v.z * h);
        }
        lastHurtTime = ht;
    }

    // ---------- Tick-Ende: nach der Bewegung, Werte gelten fuer den naechsten Tick ----------

    public static void endTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        // Der Abbau-Bot steuert selbst; da wuerden Speed & Co. nur stoeren
        boolean bot = TestClient.areaActive;
        boolean inScreen = !Movement2.screenAllowsMovement(mc);

        boolean fwd = !inScreen && mc.options.keyUp.isDown();
        boolean back = !inScreen && mc.options.keyDown.isDown();
        boolean left = !inScreen && mc.options.keyLeft.isDown();
        boolean right = !inScreen && mc.options.keyRight.isDown();
        boolean moving = fwd || back || left || right;
        boolean jumpedNow = wasOnGround && !p.onGround() && p.getDeltaMovement().y > 0.2;
        boolean droppedNow = wasOnGround && !p.onGround() && p.getDeltaMovement().y <= 0;

        runStep(p);
        runHighJump(p);
        runTrident(p);
        if (noFall) runNoFall(mc, p);

        if (!bot) {
            if (scaffold && !inScreen) runScaffold(mc, p);
            else restoreScaffoldLook(p);
            if (jesus) runJesus(mc, p);
            if (autoSprint) runSprint(p, fwd, moving);
            if (slippy) runSlippy(mc, p);
            if (noSlow && moving) runNoSlow(p, fwd, back, left, right);
            if (speed && moving) runSpeed(p, fwd, back, left, right);
            if (longJump && jumpedNow && moving) runLongJump(p);
            if (reverseStep && droppedNow) runReverseStep(mc, p);
            if (spider && moving) runSpider(p);
            if (parkour) runParkour(mc, p, moving);
        }
        // Schleichen zuletzt: Sneak-Modul oder Safe Walk an einer Kante
        boolean jumping = !inScreen && mc.options.keyJump.isDown();
        boolean edge = !bot && safeWalk && moving && p.onGround() && !(safeWalkNotJumping && jumping)
                && edgeAhead(mc.level, p, moveDir(p, fwd, back, left, right), safeWalkDist / 10.0);
        runSneak(mc, (sneak && !bot && (!sneakOnlyMoving || moving)) || edge);
        wasOnGround = p.onGround();
    }

    private static void runSneak(Minecraft mc, boolean wantShift) {
        boolean want = wantShift && mc.gui.screen() == null;
        if (want) {
            mc.options.keyShift.setDown(true);
            sneakApplied = true;
        } else if (sneakApplied) {
            mc.options.keyShift.setDown(false);
            sneakApplied = false;
        }
    }

    private static void runStep(LocalPlayer p) {
        AttributeInstance attr = p.getAttribute(Attributes.STEP_HEIGHT);
        if (attr == null) return;
        if (step && !p.isShiftKeyDown()) {
            if (attr.getBaseValue() != stepHeight) attr.setBaseValue(stepHeight);
            stepApplied = true;
        } else if (stepApplied) {
            attr.setBaseValue(VANILLA_STEP);
            stepApplied = false;
        }
    }

    private static void runTrident(LocalPlayer p) {
        boolean spin = p.isAutoSpinAttack();
        if (trident && spin && !wasSpinning) {
            // Der Riptide-Schub wurde gerade gesetzt: verstaerken, bevor er angewendet wird
            p.setDeltaMovement(p.getDeltaMovement().scale(tridentPower / 100.0));
        }
        wasSpinning = spin;
    }

    private static void runSprint(LocalPlayer p, boolean fwd, boolean moving) {
        if (!(sprintOmni ? moving : fwd)) return;
        if (p.isShiftKeyDown() || p.isUsingItem() || p.horizontalCollision) return;
        if (p.getFoodData().getFoodLevel() <= 6 && !p.getAbilities().mayfly) return;
        if (!p.isSprinting()) p.setSprinting(true);
    }

    /** Hoehere Bodenreibung nachbilden: Schwung bleibt laenger erhalten (wie auf Eis). */
    private static void runSlippy(Minecraft mc, LocalPlayer p) {
        if (!p.onGround()) return;
        BlockPos below = BlockPos.containing(p.getX(), p.getY() - 0.5, p.getZ());
        double actual = mc.level.getBlockState(below).getBlock().getFriction() * 0.91;
        double target = slippyAmount / 100.0 * 0.91;
        if (target <= actual || actual <= 0) return;
        Vec3 v = p.getDeltaMovement();
        double before = Math.sqrt(v.x * v.x + v.z * v.z);
        double k = target / actual;
        double nx = v.x * k;
        double nz = v.z * k;
        // Ohne Begrenzung wuerde man immer schneller - hoechstens Eis-Tempo
        double after = Math.sqrt(nx * nx + nz * nz);
        double cap = Math.max(0.4, before);
        if (after > cap) {
            nx *= cap / after;
            nz *= cap / after;
        }
        p.setDeltaMovement(nx, v.y, nz);
    }

    private static void runSpeed(LocalPlayer p, boolean fwd, boolean back, boolean left, boolean right) {
        if (p.isInWater() || p.isInLava() || p.isFallFlying() || p.getAbilities().flying || p.isPassenger()) return;
        if (speedGroundOnly && !p.onGround()) return;
        double f = (fwd ? 1 : 0) - (back ? 1 : 0);
        double s = (left ? 1 : 0) - (right ? 1 : 0);
        double len = Math.sqrt(f * f + s * s);
        if (len < 1e-6) return;
        f /= len;
        s /= len;
        double yaw = Math.toRadians(p.getYRot());
        double perTick = speedBps / 20.0;
        double vx = (-Math.sin(yaw) * f + Math.cos(yaw) * s) * perTick;
        double vz = (Math.cos(yaw) * f + Math.sin(yaw) * s) * perTick;
        double vy = p.getDeltaMovement().y;
        if (speedAutoJump && p.onGround()) vy = 0.42;
        p.setDeltaMovement(vx, vy, vz);
    }

    private static void runSpider(LocalPlayer p) {
        if (!p.horizontalCollision) return;
        double climb = spiderSpeed * 0.05;
        Vec3 v = p.getDeltaMovement();
        if (v.y < climb) p.setDeltaMovement(v.x, climb, v.z);
    }

    // ---------- Hilfen ----------

    /** Laufrichtung aus den Tasten als Einheitsvektor {x, z}, oder null wenn keine Taste gedrueckt ist. */
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

    /** true, wenn unter dem um (dir * dist + Geschwindigkeit) verschobenen Spieler kein Boden mehr ist. */
    private static boolean edgeAhead(Level level, LocalPlayer p, double[] dir, double dist) {
        if (dir == null) return false;
        Vec3 v = p.getDeltaMovement();
        double dx = v.x + dir[0] * dist;
        double dz = v.z + dir[1] * dist;
        AABB bb = p.getBoundingBox();
        AABB under = new AABB(bb.minX + dx + 0.001, bb.minY - 0.6, bb.minZ + dz + 0.001,
                bb.maxX + dx - 0.001, bb.minY, bb.maxZ + dz - 0.001);
        return level.noCollision(p, under);
    }

    private static boolean solid(Level level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** Sprunggeschwindigkeit, mit der man ungefaehr 'height' Bloecke hoch kommt (Schwerkraft wie im Spiel). */
    private static double jumpVelocityFor(double height) {
        double lo = VANILLA_JUMP;
        double hi = 4.0;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            double y = 0;
            double vy = mid;
            while (vy > 0) {
                y += vy;
                vy = (vy - 0.08) * 0.98;
            }
            if (y < height) lo = mid; else hi = mid;
        }
        return hi;
    }

    // ---------- Neue Module ----------

    private static void runHighJump(LocalPlayer p) {
        AttributeInstance attr = p.getAttribute(Attributes.JUMP_STRENGTH);
        if (attr == null) return;
        if (highJump) {
            if (jumpHeightCached != highJumpHeight) {
                jumpForHeight = jumpVelocityFor(highJumpHeight);
                jumpHeightCached = highJumpHeight;
            }
            if (attr.getBaseValue() != jumpForHeight) attr.setBaseValue(jumpForHeight);
            jumpApplied = true;
        } else if (jumpApplied) {
            attr.setBaseValue(VANILLA_JUMP);
            jumpApplied = false;
        }
    }

    /** Auf Wasser (und optional Lava) laufen; mit Schleichen taucht man ab. */
    private static void runJesus(Minecraft mc, LocalPlayer p) {
        if (mc.options.keyShift.isDown() && !sneakApplied) return;
        boolean inFluid = p.isInWater() || (jesusLava && p.isInLava());
        Vec3 v = p.getDeltaMovement();
        if (inFluid) {
            // Im Wasser: nach oben treiben
            if (v.y < 0.11) p.setDeltaMovement(v.x, 0.11, v.z);
            return;
        }
        BlockPos below = BlockPos.containing(p.getX(), p.getY() - 0.05, p.getZ());
        FluidState fs = mc.level.getFluidState(below);
        boolean water = !fs.isEmpty() && fs.is(FluidTags.WATER);
        boolean lava = jesusLava && !fs.isEmpty() && fs.is(FluidTags.LAVA);
        if (!water && !lava) return;
        // Auf der Oberflaeche: nicht einsinken
        if (v.y < 0) p.setDeltaMovement(v.x, 0, v.z);
        p.setOnGround(true);
        p.fallDistance = 0;
    }

    private static void runLongJump(LocalPlayer p) {
        Vec3 v = p.getDeltaMovement();
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        if (h < 0.05) return;
        double k = longJumpPower / 100.0;
        double nh = Math.min(h * k, 3.0);
        p.setDeltaMovement(v.x / h * nh, v.y, v.z / h * nh);
    }

    /** Meldet dem Server beim Fallen "am Boden", damit kein Fallschaden entsteht. */
    private static void runNoFall(Minecraft mc, LocalPlayer p) {
        if (p.isFallFlying() || p.getAbilities().flying || p.isCreative()) return;
        if (p.fallDistance <= noFallHeight - 0.5 || p.getDeltaMovement().y >= 0) return;
        ClientPacketListener conn = mc.getConnection();
        if (conn == null) return;
        conn.send(new ServerboundMovePlayerPacket.StatusOnly(true, p.horizontalCollision));
    }

    /** Beim Essen, Blocken oder Bogen spannen trotzdem normal schnell laufen. */
    private static void runNoSlow(LocalPlayer p, boolean fwd, boolean back, boolean left, boolean right) {
        if (!p.isUsingItem() || !p.onGround() || p.isPassenger()) return;
        if (speed) return; // Speed setzt das Tempo ohnehin
        if (!noSlowAllowed(p.getUseItem())) return;
        double[] dir = moveDir(p, fwd, back, left, right);
        if (dir == null) return;
        double perTick = (p.isSprinting() ? 5.6 : 4.3) / 20.0 * (noSlowPercent / 100.0);
        Vec3 v = p.getDeltaMovement();
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        if (h >= perTick) return;
        p.setDeltaMovement(dir[0] * perTick, v.y, dir[1] * perTick);
    }

    /** Welche benutzten Items No Slow beschleunigen darf. */
    private static boolean noSlowAllowed(ItemStack st) {
        if (st.is(Items.SHIELD)) return noSlowShield;
        if (st.is(Items.BOW) || st.is(Items.CROSSBOW) || st.is(Items.TRIDENT)) return noSlowBow;
        if (st.get(DataComponents.FOOD) != null || st.is(Items.POTION) || st.is(Items.MILK_BUCKET)
                || st.is(Items.HONEY_BOTTLE)) {
            return noSlowFood;
        }
        return noSlowOther;
    }

    /** Springt automatisch an der Blockkante. */
    private static void runParkour(Minecraft mc, LocalPlayer p, boolean moving) {
        if (parkourJumped) {
            mc.options.keyJump.setDown(false);
            parkourJumped = false;
        }
        if (!moving || !p.onGround() || p.isShiftKeyDown()) return;
        if (parkourSprintOnly && !p.isSprinting()) return;
        Vec3 v = p.getDeltaMovement();
        if (Math.abs(v.x) + Math.abs(v.z) < 0.02) return;
        double lx = v.x * parkourLead;
        double lz = v.z * parkourLead;
        AABB bb = p.getBoundingBox();
        AABB next = new AABB(bb.minX + lx + 0.001, bb.minY - 0.5, bb.minZ + lz + 0.001,
                bb.maxX + lx - 0.001, bb.minY, bb.maxZ + lz - 0.001);
        if (mc.level.noCollision(p, next)) {
            mc.options.keyJump.setDown(true);
            parkourJumped = true;
        }
    }

    /** Von kleinen Kanten sofort herunterfallen statt zu schweben. */
    private static void runReverseStep(Minecraft mc, LocalPlayer p) {
        if (p.isInWater() || p.isInLava() || p.isFallFlying() || p.getAbilities().flying) return;
        BlockPos feet = p.blockPosition();
        for (int i = 1; i <= reverseStepHeight; i++) {
            if (solid(mc.level, feet.below(i))) {
                Vec3 v = p.getDeltaMovement();
                p.setDeltaMovement(v.x, -3.0, v.z);
                return;
            }
        }
    }

    // ---------- Scaffold ----------

    private static boolean scaffoldBlock(ItemStack st) {
        if (st.isEmpty() || !(st.getItem() instanceof BlockItem bi)) return false;
        Block b = bi.getBlock();
        // Keine Kisten/Truhen usw., nichts was herunterfaellt, kein TNT
        if (b instanceof EntityBlock || b instanceof FallingBlock || b == Blocks.TNT) return false;
        return b.defaultBlockState().canOcclude();
    }

    private static int scaffoldSlot(LocalPlayer p) {
        var inv = p.getInventory();
        int sel = inv.getSelectedSlot();
        if (scaffoldBlock(inv.getItem(sel))) return sel;
        for (int i = 0; i < 9; i++) {
            if (scaffoldBlock(inv.getItem(i))) return i;
        }
        return -1;
    }

    private static final Direction[] PLACE_DIRS = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    /** Setzt einen Block an 'pos', wenn es einen Nachbarn zum Anklicken gibt. */
    private static boolean placeAt(Minecraft mc, LocalPlayer p, BlockPos pos, int slot) {
        Level level = mc.level;
        if (!level.getBlockState(pos).canBeReplaced()) return false;
        for (Direction d : PLACE_DIRS) {
            BlockPos against = pos.relative(d);
            if (!solid(level, against) || level.getBlockState(against).canBeReplaced()) continue;
            Direction face = d.getOpposite();
            Vec3 hit = Vec3.atCenterOf(against).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            if (scaffoldLook) {
                if (!scaffoldLooking) {
                    scaffoldSavedPitch = p.getXRot();
                    scaffoldLooking = true;
                }
                p.setXRot(80f);
            }
            var inv = p.getInventory();
            int prev = inv.getSelectedSlot();
            inv.setSelectedSlot(slot);
            mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, against, false));
            p.swing(InteractionHand.MAIN_HAND);
            inv.setSelectedSlot(prev);
            return true;
        }
        return false;
    }

    private static void runScaffold(Minecraft mc, LocalPlayer p) {
        if (mc.gameMode == null) return;
        int slot = scaffoldSlot(p);
        if (slot < 0) {
            restoreScaffoldLook(p);
            return;
        }
        Vec3 v = p.getDeltaMovement();
        int y = (int) Math.floor(p.getY() - 0.01) - 1;
        // Unter den Fuessen jetzt und dort, wo man im naechsten Tick steht
        BlockPos[] targets = {
                BlockPos.containing(p.getX(), y, p.getZ()),
                BlockPos.containing(p.getX() + v.x * 2, y, p.getZ() + v.z * 2)};
        int placed = 0;
        for (BlockPos pos : targets) {
            if (placed >= scaffoldPerTick) break;
            if (!mc.level.getBlockState(pos).canBeReplaced()) continue;
            if (placeAt(mc, p, pos, slot)) {
                placed++;
                continue;
            }
            // Kein Nachbar zum Anklicken: erst einen Block daneben setzen, der als Stuetze dient
            for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                if (placed >= scaffoldPerTick) break;
                if (placeAt(mc, p, pos.relative(d), slot)) placed++;
            }
        }
        if (placed > 0) {
            scaffoldIdle = 0;
        } else {
            restoreScaffoldLook(p);
        }
    }

    /** Nach dem Platzieren den Blick langsam wieder heben. */
    private static void restoreScaffoldLook(LocalPlayer p) {
        if (!scaffoldLooking) return;
        if (++scaffoldIdle < 5) return; // kurz unten bleiben, falls gleich der naechste Block kommt
        float pitch = p.getXRot();
        float diff = scaffoldSavedPitch - pitch;
        if (Math.abs(diff) <= 3f) {
            p.setXRot(scaffoldSavedPitch);
            scaffoldLooking = false;
        } else {
            p.setXRot(pitch + Math.signum(diff) * 3f);
        }
    }

    // ---------- Einstellungs-Menues ----------

    public static Screen autoWalkScreen(Screen parent) {
        return new ModuleScreen(parent, "AutoWalk", f -> {
            f.description("Läuft automatisch geradeaus und baut\nHindernisse ab (Taste G).");
            f.section("Allgemein");
            f.toggle("Sprinten", () -> TestClient.sprint, v -> TestClient.sprint = v, false);
            f.toggle("Sprint-Jump", () -> TestClient.sprintJump, v -> TestClient.sprintJump = v, false);
            f.toggle("Hindernisse abbauen", () -> TestClient.autoMine, v -> TestClient.autoMine = v, true);
            f.footer(() -> TestClient.walking, v -> TestClient.walking = v);
        });
    }

    public static Screen slippyScreen(Screen parent) {
        return new ModuleScreen(parent, "Slippy", f -> {
            f.description("Jeder Boden fühlt sich rutschig an wie Eis.");
            f.section("Allgemein");
            f.slider("Rutschigkeit (%)", 60, 99, () -> slippyAmount, v -> slippyAmount = v, 98);
            f.footer(() -> slippy, v -> slippy = v);
        });
    }

    public static Screen sneakScreen(Screen parent) {
        return new ModuleScreen(parent, "Sneak", f -> {
            f.description("H\u00e4lt die Schleichen-Taste dauerhaft gedr\u00fcckt.");
            f.section("Allgemein");
            f.toggle("Nur beim Laufen", () -> sneakOnlyMoving, v -> sneakOnlyMoving = v, false);
            f.footer(() -> sneak, v -> sneak = v);
        });
    }

    public static Screen speedScreen(Screen parent) {
        return new ModuleScreen(parent, "Speed", f -> {
            f.description("Läuft schneller in die gedrückte Richtung.");
            f.section("Allgemein");
            f.slider("Tempo (Blöcke/s)", 4, 30, () -> speedBps, v -> speedBps = v, 9);
            f.toggle("Nur am Boden", () -> speedGroundOnly, v -> speedGroundOnly = v, true);
            f.toggle("Auto-Springen", () -> speedAutoJump, v -> speedAutoJump = v, false);
            f.footer(() -> speed, v -> speed = v);
        });
    }

    public static Screen spiderScreen(Screen parent) {
        return new ModuleScreen(parent, "Spider", f -> {
            f.description("Klettert an Wänden hoch, wenn man\ndagegen läuft.");
            f.section("Allgemein");
            f.slider("Klettertempo", 1, 10, () -> spiderSpeed, v -> spiderSpeed = v, 4);
            f.footer(() -> spider, v -> spider = v);
        });
    }

    public static Screen sprintScreen(Screen parent) {
        return new ModuleScreen(parent, "Sprint", f -> {
            f.description("Sprintet automatisch, solange man läuft.");
            f.section("Allgemein");
            f.toggle("Alle Richtungen", () -> sprintOmni, v -> sprintOmni = v, false);
            f.footer(() -> autoSprint, v -> autoSprint = v);
        });
    }

    public static Screen stepScreen(Screen parent) {
        return new ModuleScreen(parent, "Step", f -> {
            f.description("Steigt höhere Stufen ohne Springen hoch.\nBeim Schleichen aus.");
            f.section("Allgemein");
            f.slider("Höhe (Blöcke)", 1, 5, () -> stepHeight, v -> stepHeight = v, 1);
            f.footer(() -> step, v -> step = v);
        });
    }

    public static Screen tridentScreen(Screen parent) {
        return new ModuleScreen(parent, "Trident Boost", f -> {
            f.description("Verstärkt den Schub von Dreizacken\nmit Sog (Riptide).");
            f.section("Allgemein");
            f.slider("Stärke (%)", 100, 500, () -> tridentPower, v -> tridentPower = v, 200);
            f.footer(() -> trident, v -> trident = v);
        });
    }

    public static Screen highJumpScreen(Screen parent) {
        return new ModuleScreen(parent, "High Jump", f -> {
            f.description("Springt h\u00f6her. Achtung: Fallschaden,\nam besten mit No Fall.");
            f.section("Allgemein");
            f.slider("Sprungh\u00f6he (Bl\u00f6cke)", 2, 10, () -> highJumpHeight, v -> highJumpHeight = v, 3);
            f.footer(() -> highJump, v -> highJump = v);
        });
    }

    public static Screen jesusScreen(Screen parent) {
        return new ModuleScreen(parent, "Jesus", f -> {
            f.description("L\u00e4uft \u00fcber Wasser. Mit Schleichen\ntaucht man ab.");
            f.section("Allgemein");
            f.toggle("Auch Lava", () -> jesusLava, v -> jesusLava = v, false);
            f.footer(() -> jesus, v -> jesus = v);
        });
    }

    public static Screen longJumpScreen(Screen parent) {
        return new ModuleScreen(parent, "Long Jump", f -> {
            f.description("Springt beim Laufen weiter.");
            f.section("Allgemein");
            f.slider("Weite (%)", 110, 500, () -> longJumpPower, v -> longJumpPower = v, 250);
            f.footer(() -> longJump, v -> longJump = v);
        });
    }

    public static Screen noFallScreen(Screen parent) {
        return new ModuleScreen(parent, "No Fall", f -> {
            f.description("Verhindert Fallschaden.\nWirkt nicht beim Elytra-Fliegen.");
            f.section("Allgemein");
            f.slider("Ab Fallh\u00f6he (Bl\u00f6cke)", 2, 20, () -> noFallHeight, v -> noFallHeight = v, 3);
            f.footer(() -> noFall, v -> noFall = v);
        });
    }

    public static Screen noSlowScreen(Screen parent) {
        return new ModuleScreen(parent, "No Slow", f -> {
            f.description("Beim Essen, Trinken, Blocken und\nBogenspannen normal schnell laufen.");
            f.section("Items");
            f.toggle("Essen / Trinken", () -> noSlowFood, v -> noSlowFood = v, true);
            f.toggle("Schild", () -> noSlowShield, v -> noSlowShield = v, true);
            f.toggle("Bogen / Armbrust", () -> noSlowBow, v -> noSlowBow = v, true);
            f.toggle("Sonstige", () -> noSlowOther, v -> noSlowOther = v, true);
            f.section("Tempo");
            f.slider("Tempo (%)", 30, 100, () -> noSlowPercent, v -> noSlowPercent = v, 100);
            f.footer(() -> noSlow, v -> noSlow = v);
        });
    }

    public static Screen parkourScreen(Screen parent) {
        return new ModuleScreen(parent, "Parkour", f -> {
            f.description("Springt automatisch an der Blockkante.");
            f.section("Allgemein");
            f.slider("Vorlauf (Ticks)", 1, 4, () -> parkourLead, v -> parkourLead = v, 1);
            f.toggle("Nur beim Sprinten", () -> parkourSprintOnly, v -> parkourSprintOnly = v, false);
            f.footer(() -> parkour, v -> parkour = v);
        });
    }

    public static Screen reverseStepScreen(Screen parent) {
        return new ModuleScreen(parent, "Reverse Step", f -> {
            f.description("F\u00e4llt von Kanten sofort herunter\nstatt langsam zu sinken.");
            f.section("Allgemein");
            f.slider("H\u00f6he (Bl\u00f6cke)", 1, 5, () -> reverseStepHeight, v -> reverseStepHeight = v, 3);
            f.footer(() -> reverseStep, v -> reverseStep = v);
        });
    }

    public static Screen safeWalkScreen(Screen parent) {
        return new ModuleScreen(parent, "Safe Walk", f -> {
            f.description("Schleicht automatisch an Kanten,\ndamit man nicht herunterf\u00e4llt.");
            f.section("Allgemein");
            f.slider("Abstand zur Kante (x0,1)", 1, 8, () -> safeWalkDist, v -> safeWalkDist = v, 4);
            f.toggle("Beim Springen aus", () -> safeWalkNotJumping, v -> safeWalkNotJumping = v, true);
            f.footer(() -> safeWalk, v -> safeWalk = v);
        });
    }

    public static Screen scaffoldScreen(Screen parent) {
        return new ModuleScreen(parent, "Scaffold", f -> {
            f.description("Setzt beim Laufen Bl\u00f6cke unter die F\u00fc\u00dfe.\nNimmt Bl\u00f6cke aus der Hotbar.");
            f.section("Allgemein");
            f.toggle("Nach unten schauen", () -> scaffoldLook, v -> scaffoldLook = v, true);
            f.slider("Bl\u00f6cke pro Tick", 1, 3, () -> scaffoldPerTick, v -> scaffoldPerTick = v, 1);
            f.footer(() -> scaffold, v -> scaffold = v);
        });
    }

    public static Screen velocityScreen(Screen parent) {
        return new ModuleScreen(parent, "Velocity", f -> {
            f.description("Schwächt den Rückstoß ab, wenn man\ngetroffen wird. 0 % = kein Rückstoß.");
            f.section("Allgemein");
            f.slider("Horizontal (%)", 0, 100, () -> velocityH, v -> velocityH = v, 0);
            f.slider("Vertikal (%)", 0, 100, () -> velocityV, v -> velocityV = v, 0);
            f.footer(() -> velocity, v -> velocity = v);
        });
    }
}
