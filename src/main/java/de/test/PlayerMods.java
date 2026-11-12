package de.test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Spieler-Module (Kategorie "Player"). Was ohne Eingriff ins Spiel nicht geht, laeuft ueber
 * Mixins (de.test.mixin) oder Reflection - seit 26.1 ist Minecraft nicht mehr verschleiert,
 * die Feldnamen stimmen also zur Laufzeit.
 */
public final class PlayerMods {
    private PlayerMods() {
    }

    // ---------- Schalter + Einstellungen (public static = wird automatisch gespeichert) ----------

    public static boolean airPlace = false;
    public static int airPlaceRange = 4;

    public static boolean antiAfk = false;
    public static int antiAfkInterval = 15;     // Sekunden
    public static boolean antiAfkJump = true;
    public static boolean antiAfkSwing = true;
    public static boolean antiAfkTurn = true;

    public static boolean antiHunger = false;
    public static boolean antiHungerSprint = true;
    public static boolean antiHungerGround = true;

    public static boolean autoEat = false;
    public static int autoEatHunger = 14;       // essen, wenn Hunger <= Wert (20 = voll)
    public static boolean autoEatGapples = false;

    public static boolean autoFish = false;
    public static int autoFishDelay = 10;       // Ticks bis zum neuen Auswerfen

    public static boolean autoGap = false;
    public static int autoGapHealth = 10;       // Lebenspunkte (2 = 1 Herz)

    public static boolean autoMend = false;
    public static int autoMendBelow = 100;     // nur Items unter dieser Haltbarkeit (%)
    public static boolean autoMendHotbar = true;

    public static boolean autoReplenish = false;
    public static int autoReplenishMin = 8;

    public static boolean autoRespawn = false;
    public static int autoRespawnDelay = 10;    // Ticks

    public static boolean autoTool = false;
    public static boolean autoToolSwitchBack = true;
    public static boolean autoToolSave = true;  // fast kaputte Werkzeuge nicht benutzen

    public static boolean breakDelay = false;
    public static int breakDelayTicks = 0;

    public static boolean chestSwap = false;    // einmalig: tauscht und schaltet sich wieder aus
    public static int chestSwapTarget = 0;
    public static final String[] CHEST_TARGETS = {"Wechseln", "Elytra", "Brustpanzer"};

    public static boolean expThrower = false;
    public static int expThrowerDelay = 2;      // Ticks zwischen zwei Wuerfen

    public static boolean fakePlayer = false;
    public static String fakeName = "FakePlayer";
    public static boolean fakeCopyInv = true;
    public static int fakeHealth = 20;

    public static boolean fastUse = false;
    public static int fastUseDelay = 0;

    public static boolean ghostHand = false;
    public static int ghostHandRange = 4;

    public static boolean instantRebreak = false;
    public static int instantRebreakDelay = 0;

    public static boolean liquidInteract = false;
    public static boolean liquidLava = true;
    public static int liquidRange = 4;

    public static boolean middleClickExtra = false;
    public static int middleClickMode = 0;
    public static final String[] MIDDLE_MODES = {"Enderperle", "Feuerwerk", "XP-Flasche"};

    public static boolean multitask = false;
    public static boolean multitaskMine = true;
    public static boolean multitaskAttack = true;

    public static boolean nameProtect = false;
    public static String nameProtectAlias = "Ich";

    public static boolean noInteract = false;
    public static final Set<Block> noInteractBlocks = new LinkedHashSet<>();
    public static final Set<EntityType<?>> noInteractEntities = new LinkedHashSet<>();

    public static boolean noMiningTrace = false;
    public static boolean noMiningTraceTools = true; // nur mit Werkzeug in der Hand

    public static boolean noRotate = false;
    public static boolean noRotateYaw = true;
    public static boolean noRotatePitch = true;

    public static boolean noStatusEffects = false;
    public static boolean nseBlindness = true;
    public static boolean nseDarkness = true;
    public static boolean nseNausea = true;
    public static boolean nseLevitation = false;
    public static boolean nseSlowness = false;
    public static boolean nseMiningFatigue = false;

    public static boolean portals = false;

    public static boolean potionSaver = false;

    public static boolean reach = false;
    public static int reachBlocks = 5;
    public static int reachEntities = 4;

    public static boolean rotation = false;
    public static int rotYawMode = 0;
    public static final String[] YAW_MODES = {"Aus", "Fest", "Einrasten 45°"};
    public static int rotYaw = 0;
    public static int rotPitchMode = 0;
    public static final String[] PITCH_MODES = {"Aus", "Fest"};
    public static int rotPitch = 0;

    public static boolean speedMine = false;
    public static int speedMinePercent = 130;

    // ---------- Registrierung (einmal beim Start) ----------

    public static void register() {
        // No Interact: Rechtsklick auf gewaehlte Bloecke / Wesen verhindern
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (noInteract && level.isClientSide()
                    && noInteractBlocks.contains(level.getBlockState(hit.getBlockPos()).getBlock())) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (noInteract && level.isClientSide() && noInteractEntities.contains(entity.getType())) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        // Name Protect: eigenen Namen in Chat- und Systemnachrichten ersetzen
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> protect(message));
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, time) -> {
            if (!nameProtect || !containsOwnName(message)) return true;
            Minecraft.getInstance().gui.getChat().addMessage(protect(message));
            return false;
        });
    }

    // ---------- Name Protect ----------

    private static String ownName() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getName().getString() : mc.getUser().getName();
    }

    private static boolean containsOwnName(Component c) {
        String name = ownName();
        return name != null && !name.isEmpty() && c.getString().contains(name);
    }

    /** Ersetzt den eigenen Namen und behaelt dabei Farben und Formatierung. */
    public static Component protect(Component message) {
        if (!nameProtect || !containsOwnName(message)) return message;
        String name = ownName();
        MutableComponent out = Component.empty();
        message.visit((Style style, String part) -> {
            out.append(Component.literal(part.replace(name, nameProtectAlias)).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    // ---------- Pakete (vom ConnectionMixin fuer jedes ausgehende Paket) ----------

    private static boolean bypass = false;

    /** true = Original-Paket nicht senden. */
    public static boolean onSend(Packet<?> packet) {
        if (bypass) return false;
        Packet<?> out = packet;
        if (antiHunger) {
            if (antiHungerSprint && packet instanceof ServerboundPlayerCommandPacket cmd
                    && cmd.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING) {
                return true;
            }
            if (antiHungerGround) out = spoofAir(out);
        }
        if (potionSaver && holdForPotions(out)) return true;
        if (MiscMods.onSend(out)) return true;
        if (Movement2.intercept(out)) return true;
        if (out != packet) {
            sendRaw(out);
            return true;
        }
        return false;
    }

    /** Sendet ein Paket an allen Abfang-Regeln vorbei. */
    public static void sendRaw(Packet<?> packet) {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null) return;
        bypass = true;
        try {
            conn.send(packet);
        } finally {
            bypass = false;
        }
    }

    /** Anti Hunger: "am Boden" weglassen, solange man wirklich steht (nicht beim Landen nach einem Fall). */
    private static Packet<?> spoofAir(Packet<?> packet) {
        if (!(packet instanceof ServerboundMovePlayerPacket m) || !m.isOnGround()) return packet;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.fallDistance > 0 || p.isUsingItem() || p.isInWater()) return packet;
        boolean hc = m.horizontalCollision();
        if (m.hasPosition() && m.hasRotation()) {
            return new ServerboundMovePlayerPacket.PosRot(m.getX(0), m.getY(0), m.getZ(0),
                    m.getYRot(0), m.getXRot(0), false, hc);
        }
        if (m.hasPosition()) return new ServerboundMovePlayerPacket.Pos(m.getX(0), m.getY(0), m.getZ(0), false, hc);
        if (m.hasRotation()) return new ServerboundMovePlayerPacket.Rot(m.getYRot(0), m.getXRot(0), false, hc);
        return new ServerboundMovePlayerPacket.StatusOnly(false, hc);
    }

    /** Potion Saver: im Stillstand keine Bewegungspakete senden (wirkt nur auf manchen Servern). */
    private static boolean holdForPotions(Packet<?> packet) {
        if (!(packet instanceof ServerboundMovePlayerPacket m) || m.hasPosition()) return false;
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && !p.getActiveEffects().isEmpty() && p.getDeltaMovement().horizontalDistanceSqr() < 1e-4;
    }

    // ---------- Reflection-Helfer (Namen stimmen seit 26.1 zur Laufzeit) ----------

    private static Field field(Class<?> c, String name) {
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.err.println("[1912] Feld nicht gefunden: " + c.getSimpleName() + "." + name);
            return null;
        }
    }

    private static Field rightClickDelayField;
    private static Field destroyDelayField;
    private static Field destroyProgressField;
    private static Field isDestroyingField;
    private static boolean fieldsLoaded = false;

    private static void loadFields() {
        if (fieldsLoaded) return;
        fieldsLoaded = true;
        rightClickDelayField = field(Minecraft.class, "rightClickDelay");
        destroyDelayField = field(MultiPlayerGameMode.class, "destroyDelay");
        destroyProgressField = field(MultiPlayerGameMode.class, "destroyProgress");
        isDestroyingField = field(MultiPlayerGameMode.class, "isDestroying");
    }

    // ---------- Inventar-Klicks (gleicher Weg wie AutoDrop) ----------

    private static Method clickMethod;
    private static Class<?> clickEnum;
    private static boolean clickLoaded = false;

    private static boolean click(LocalPlayer p, int menuSlot, int button, String type) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null) return false;
        if (!clickLoaded) {
            clickLoaded = true;
            for (Method m : MultiPlayerGameMode.class.getMethods()) {
                Class<?>[] t = m.getParameterTypes();
                if (t.length == 5 && t[0] == int.class && t[1] == int.class && t[2] == int.class && t[3].isEnum()
                        && t[4].isAssignableFrom(LocalPlayer.class)) {
                    clickMethod = m;
                    clickEnum = t[3];
                    break;
                }
            }
        }
        if (clickMethod == null) return false;
        try {
            for (Object c : clickEnum.getEnumConstants()) {
                if (((Enum<?>) c).name().equals(type)) {
                    clickMethod.invoke(mc.gameMode, p.inventoryMenu.containerId, menuSlot, button, c, p);
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.err.println("[1912] Inventar-Klick fehlgeschlagen: " + e);
        }
        return false;
    }

    /** Legt den Stapel aus Inv-Slot 'from' auf 'to' (0..35). Fuer Inventory Tweaks. */
    public static void publicClick(LocalPlayer p, int from, int to) {
        click(p, menuSlot(from), 0, "PICKUP");
        click(p, menuSlot(to), 0, "PICKUP");
        if (!p.inventoryMenu.getCarried().isEmpty()) click(p, menuSlot(from), 0, "PICKUP");
    }

    /** Inventar-Index (0-8 Hotbar, 9-35 Rest) -> Menue-Slot im Spieler-Inventar. */
    private static int menuSlot(int invIndex) {
        return invIndex < 9 ? 36 + invIndex : invIndex;
    }

    private static int findHotbar(LocalPlayer p, Predicate<ItemStack> test) {
        var inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            if (test.test(inv.getItem(i))) return i;
        }
        return -1;
    }

    /** Benutzt ein Item aus der Hotbar, ohne die Auswahl dauerhaft zu aendern. */
    private static boolean useFromHotbar(Minecraft mc, LocalPlayer p, Predicate<ItemStack> test) {
        int slot = findHotbar(p, test);
        if (slot < 0 || mc.gameMode == null) return false;
        var inv = p.getInventory();
        int prev = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
        inv.setSelectedSlot(prev);
        return true;
    }

    // ---------- Zustand ----------

    private static final Random RNG = new Random();
    private static boolean useWasDown = false;
    private static boolean attackWasDown = false;
    private static int afkTicks = 0;
    private static boolean afkJumpPressed = false;

    private static boolean eating = false;
    private static int eatPrevSlot = -1;
    private static int eatTicks = 0;

    private static int fishCooldown = -1;
    private static int respawnTicks = 0;
    private static int toolPrevSlot = -1;
    private static int expTicks = 0;
    private static RemotePlayer fake = null;

    private static BlockPos miningPos = null;
    private static Direction miningDir = Direction.UP;
    private static BlockPos rebreakPos = null;
    private static Direction rebreakDir = Direction.UP;
    private static int rebreakTicks = 0;

    private static float lastProgress = -1f;
    private static boolean reachApplied = false;

    // ---------- Tick-Anfang (vor Tasten und Spieler-Bewegung) ----------

    public static void startTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) return;
        loadFields();

        if (middleClickExtra) {
            while (mc.options.keyPickItem.consumeClick()) runMiddleClick(mc, p);
        }
        if (fastUse && rightClickDelayField != null) {
            try {
                if (rightClickDelayField.getInt(mc) > fastUseDelay) rightClickDelayField.setInt(mc, fastUseDelay);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Feld nicht nutzbar: Fast Use wirkt dann nicht
            }
        }
        if (breakDelay && destroyDelayField != null) {
            try {
                if (destroyDelayField.getInt(mc.gameMode) > breakDelayTicks) {
                    destroyDelayField.setInt(mc.gameMode, breakDelayTicks);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Feld nicht nutzbar
            }
        }
        if (autoTool) runAutoTool(mc, p);
        if (noStatusEffects) runNoStatusEffects(p);
    }

    // ---------- Tick-Ende ----------

    public static void endTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) {
            fake = null;
            return;
        }
        boolean screen = mc.gui.screen() != null;
        boolean useDown = !screen && mc.options.keyUse.isDown();
        boolean usePressed = useDown && !useWasDown;
        boolean attackDown = !screen && mc.options.keyAttack.isDown();
        boolean attackPressed = attackDown && !attackWasDown;

        if (autoRespawn) runAutoRespawn(mc, p);
        runReach(p);
        runFakePlayer(mc, p);
        if (rotation) runRotation(p);

        if (!screen) {
            if (usePressed) {
                if (liquidInteract && runLiquidInteract(mc, p)) {
                    // erledigt
                } else if (airPlace && runAirPlace(mc, p)) {
                    // erledigt
                } else if (ghostHand) {
                    runGhostHand(mc, p);
                }
            }
            if (autoEat || autoGap) runEating(mc, p);
            if (autoFish) runAutoFish(mc, p);
            if (expThrower) runExpThrower(mc, p);
            if (multitask) runMultitask(mc, p, attackDown, attackPressed);
            if (antiAfk) runAntiAfk(mc, p);
            if (autoReplenish) runAutoReplenish(p);
            if (autoMend) runAutoMend(p);
            if (chestSwap) {
                runChestSwap(p);
                chestSwap = false;
            }
        }
        trackMining(mc, attackDown);
        if (instantRebreak) runInstantRebreak(mc, p);
        if (speedMine) runSpeedMine(mc);
        else lastProgress = -1f;

        useWasDown = useDown;
        attackWasDown = attackDown;
    }

    // ---------- Module ----------

    private static boolean runAirPlace(Minecraft mc, LocalPlayer p) {
        if (!(p.getMainHandItem().getItem() instanceof BlockItem)) return false;
        if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) return false;
        Vec3 at = p.getEyePosition().add(p.getLookAngle().scale(airPlaceRange));
        BlockPos pos = BlockPos.containing(at);
        if (!mc.level.getBlockState(pos).canBeReplaced()) return false;
        mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        p.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private static boolean runLiquidInteract(Minecraft mc, LocalPlayer p) {
        if (!(p.getMainHandItem().getItem() instanceof BlockItem)) return false;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) return false;
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(liquidRange + 0.5));
        BlockHitResult hit = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, p));
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos pos = hit.getBlockPos();
        var fluid = mc.level.getFluidState(pos);
        if (fluid.isEmpty()) return false;
        if (!liquidLava && fluid.is(net.minecraft.tags.FluidTags.LAVA)) return false;
        mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit.getLocation(), hit.getDirection(), pos, false));
        p.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    /** Ghost Hand: Kisten usw. durch Waende oeffnen. */
    private static void runGhostHand(Minecraft mc, LocalPlayer p) {
        if (p.getMainHandItem().getItem() instanceof BlockItem) return;
        BlockPos direct = mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK
                ? bh.getBlockPos() : null;
        if (direct != null && mc.level.getBlockEntity(direct) != null) return; // normal erreichbar
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        for (double d = 0.5; d <= ghostHandRange + 0.5; d += 0.1) {
            BlockPos pos = BlockPos.containing(eye.add(look.scale(d)));
            if (pos.equals(direct) || mc.level.getBlockEntity(pos) == null) continue;
            mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            p.swing(InteractionHand.MAIN_HAND);
            return;
        }
    }

    private static void runAntiAfk(Minecraft mc, LocalPlayer p) {
        if (afkJumpPressed) {
            mc.options.keyJump.setDown(false);
            afkJumpPressed = false;
        }
        if (++afkTicks < antiAfkInterval * 20) return;
        afkTicks = 0;
        if (antiAfkJump && p.onGround()) {
            mc.options.keyJump.setDown(true);
            afkJumpPressed = true;
        }
        if (antiAfkSwing) p.swing(InteractionHand.MAIN_HAND);
        if (antiAfkTurn) p.setYRot(p.getYRot() + (RNG.nextFloat() * 60f - 30f));
    }

    // --- Essen (Auto Eat + Auto Gap) ---

    private static boolean badFood(ItemStack st) {
        Item it = st.getItem();
        return it == Items.ROTTEN_FLESH || it == Items.SPIDER_EYE || it == Items.POISONOUS_POTATO
                || it == Items.PUFFERFISH || it == Items.CHORUS_FRUIT || it == Items.SUSPICIOUS_STEW;
    }

    private static boolean isGapple(ItemStack st) {
        return st.is(Items.GOLDEN_APPLE) || st.is(Items.ENCHANTED_GOLDEN_APPLE);
    }

    private static int bestFoodSlot(LocalPlayer p, boolean gapple) {
        var inv = p.getInventory();
        int best = -1;
        int bestScore = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            int score;
            if (gapple) {
                if (!isGapple(st)) continue;
                score = st.is(Items.ENCHANTED_GOLDEN_APPLE) ? 2 : 1;
            } else {
                FoodProperties food = st.get(DataComponents.FOOD);
                if (food == null || badFood(st)) continue;
                if (isGapple(st) && !autoEatGapples) continue;
                score = food.nutrition();
            }
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    private static void runEating(Minecraft mc, LocalPlayer p) {
        var inv = p.getInventory();
        if (eating) {
            eatTicks++;
            boolean done = eatTicks > 5 && !p.isUsingItem();
            if (done || eatTicks > 100) {
                mc.options.keyUse.setDown(false);
                if (eatPrevSlot >= 0) inv.setSelectedSlot(eatPrevSlot);
                eating = false;
                eatPrevSlot = -1;
            } else {
                mc.options.keyUse.setDown(true);
            }
            return;
        }
        if (p.isUsingItem() || p.isCreative()) return;
        int slot = -1;
        if (autoGap && p.getHealth() <= autoGapHealth) slot = bestFoodSlot(p, true);
        if (slot < 0 && autoEat && p.getFoodData().getFoodLevel() <= autoEatHunger) slot = bestFoodSlot(p, false);
        if (slot < 0) return;
        eatPrevSlot = inv.getSelectedSlot();
        inv.setSelectedSlot(slot);
        mc.options.keyUse.setDown(true);
        eating = true;
        eatTicks = 0;
    }

    private static void runAutoFish(Minecraft mc, LocalPlayer p) {
        if (!p.getMainHandItem().is(Items.FISHING_ROD)) return;
        FishingHook hook = p.fishing;
        if (hook == null) {
            if (fishCooldown > 0 && --fishCooldown == 0) {
                mc.gameMode.useItem(p, InteractionHand.MAIN_HAND); // neu auswerfen
                fishCooldown = -1;
            }
            return;
        }
        // Biss: der Schwimmer wird ruckartig nach unten gezogen
        Vec3 v = hook.getDeltaMovement();
        if (hook.isInWater() && v.y < -0.12 && Math.abs(v.x) < 0.05 && Math.abs(v.z) < 0.05) {
            mc.gameMode.useItem(p, InteractionHand.MAIN_HAND); // einholen
            fishCooldown = Math.max(1, autoFishDelay);
        }
    }

    private static boolean hasMending(ItemStack st) {
        for (var holder : st.getEnchantments().keySet()) {
            if (holder.is(Enchantments.MENDING)) return true;
        }
        return false;
    }

    /** Auto Mend: beschaedigte Mending-Items in die zweite Hand, damit XP sie repariert. */
    private static void runAutoMend(LocalPlayer p) {
        var inv = p.getInventory();
        ItemStack off = p.getOffhandItem();
        if (!off.isEmpty() && (!off.isDamaged() || !hasMending(off))) return;
        if (!off.isEmpty() && off.isDamaged()) return; // wird gerade repariert
        for (int i = autoMendHotbar ? 0 : 9; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isDamaged() || !hasMending(st)) continue;
            int left = (st.getMaxDamage() - st.getDamageValue()) * 100 / Math.max(1, st.getMaxDamage());
            if (left >= autoMendBelow) continue;
            click(p, menuSlot(i), 40, "SWAP");
            return;
        }
    }

    private static void runAutoReplenish(LocalPlayer p) {
        var inv = p.getInventory();
        for (int h = 0; h < 9; h++) {
            ItemStack hot = inv.getItem(h);
            if (hot.isEmpty() || !hot.isStackable() || hot.getCount() > autoReplenishMin) continue;
            for (int i = 9; i < 36; i++) {
                ItemStack st = inv.getItem(i);
                if (st.isEmpty() || !ItemStack.isSameItemSameComponents(st, hot)) continue;
                // aufnehmen, auf den Hotbar-Stapel legen, Rest zuruecklegen
                click(p, menuSlot(i), 0, "PICKUP");
                click(p, menuSlot(h), 0, "PICKUP");
                if (!p.inventoryMenu.getCarried().isEmpty()) click(p, menuSlot(i), 0, "PICKUP");
                return; // ein Nachfuellen pro Tick reicht
            }
        }
    }

    private static void runAutoRespawn(Minecraft mc, LocalPlayer p) {
        if (!(mc.gui.screen() instanceof DeathScreen)) {
            respawnTicks = 0;
            return;
        }
        if (++respawnTicks < autoRespawnDelay) return;
        respawnTicks = 0;
        p.respawn();
        mc.gui.setScreen(null);
    }

    private static boolean usableTool(ItemStack st) {
        return !autoToolSave || !st.isDamageableItem() || st.getMaxDamage() - st.getDamageValue() > 10;
    }

    private static void runAutoTool(Minecraft mc, LocalPlayer p) {
        var inv = p.getInventory();
        boolean mining = mc.gui.screen() == null && mc.options.keyAttack.isDown()
                && mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK;
        if (!mining) {
            if (autoToolSwitchBack && toolPrevSlot >= 0) {
                inv.setSelectedSlot(toolPrevSlot);
                toolPrevSlot = -1;
            }
            return;
        }
        if (p.isCreative()) return;
        BlockState state = mc.level.getBlockState(((BlockHitResult) mc.hitResult).getBlockPos());
        int cur = inv.getSelectedSlot();
        float best = usableTool(inv.getItem(cur)) ? inv.getItem(cur).getDestroySpeed(state) : 0f;
        int bestSlot = cur;
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            if (!usableTool(st)) continue;
            float s = st.getDestroySpeed(state);
            if (s > best + 0.01f) {
                best = s;
                bestSlot = i;
            }
        }
        if (bestSlot != cur) {
            if (toolPrevSlot < 0) toolPrevSlot = cur;
            inv.setSelectedSlot(bestSlot);
        }
    }

    private static final String[] CHEST_ORDER = {"netherite", "diamond", "iron", "copper", "golden", "chainmail", "leather"};

    private static int chestRank(ItemStack st) {
        String path = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        if (!path.endsWith("_chestplate")) return -1;
        for (int i = 0; i < CHEST_ORDER.length; i++) {
            if (path.startsWith(CHEST_ORDER[i])) return CHEST_ORDER.length - i;
        }
        return 0;
    }

    /** Chest Swap: Elytra <-> bester Brustpanzer (Menue-Slot 6 = Brust). */
    private static void runChestSwap(LocalPlayer p) {
        var inv = p.getInventory();
        ItemStack worn = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST);
        boolean wantElytra = chestSwapTarget == 1 || (chestSwapTarget == 0 && !worn.is(Items.ELYTRA));
        if (chestSwapTarget == 1 && worn.is(Items.ELYTRA)) return;   // schon an
        if (chestSwapTarget == 2 && chestRank(worn) >= 0) return;    // schon an
        int found = -1;
        int bestRank = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack st = inv.getItem(i);
            if (wantElytra) {
                if (st.is(Items.ELYTRA)) {
                    found = i;
                    break;
                }
            } else {
                int r = chestRank(st);
                if (r > bestRank) {
                    bestRank = r;
                    found = i;
                }
            }
        }
        if (found < 0) {
            p.sendSystemMessage(Component.literal("1912: Chest Swap - nichts zum Tauschen gefunden"));
            return;
        }
        click(p, menuSlot(found), 0, "PICKUP");
        click(p, 6, 0, "PICKUP");
        click(p, menuSlot(found), 0, "PICKUP");
    }

    private static void runExpThrower(Minecraft mc, LocalPlayer p) {
        if (++expTicks < expThrowerDelay) return;
        expTicks = 0;
        float pitch = p.getXRot();
        p.setXRot(90f); // nach unten werfen, damit die Kugeln bei einem landen
        useFromHotbar(mc, p, st -> st.is(Items.EXPERIENCE_BOTTLE));
        p.setXRot(pitch);
    }

    private static void runFakePlayer(Minecraft mc, LocalPlayer p) {
        if (fakePlayer && fake == null) {
            try {
                String name = fakeName == null || fakeName.isBlank() ? "FakePlayer" : fakeName.strip();
                fake = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), name));
                fake.copyPosition(p);
                fake.setYHeadRot(p.getYHeadRot());
                if (fakeCopyInv) fake.getInventory().replaceWith(p.getInventory());
                fake.setHealth(Math.max(1, Math.min(20, fakeHealth)));
                mc.level.addEntity(fake);
            } catch (RuntimeException e) {
                fake = null;
                fakePlayer = false;
                p.sendSystemMessage(Component.literal("1912: Fake Player konnte nicht erstellt werden"));
            }
        } else if (!fakePlayer && fake != null) {
            mc.level.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
            fake = null;
        }
    }

    private static void runMiddleClick(Minecraft mc, LocalPlayer p) {
        Item want = switch (middleClickMode) {
            case 1 -> Items.FIREWORK_ROCKET;
            case 2 -> Items.EXPERIENCE_BOTTLE;
            default -> Items.ENDER_PEARL;
        };
        useFromHotbar(mc, p, st -> st.is(want));
    }

    /** Multitask: abbauen und angreifen, waehrend man isst oder blockt. */
    private static void runMultitask(Minecraft mc, LocalPlayer p, boolean attackDown, boolean attackPressed) {
        if (!p.isUsingItem() || !attackDown) return;
        HitResult hit = mc.hitResult;
        if (multitaskMine && hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
            mc.gameMode.continueDestroyBlock(bh.getBlockPos(), bh.getDirection());
            p.swing(InteractionHand.MAIN_HAND);
        } else if (multitaskAttack && attackPressed && hit instanceof EntityHitResult eh) {
            mc.gameMode.attack(p, eh.getEntity());
            p.swing(InteractionHand.MAIN_HAND);
        }
    }

    private static void runNoStatusEffects(LocalPlayer p) {
        if (nseBlindness) p.removeEffect(MobEffects.BLINDNESS);
        if (nseDarkness) p.removeEffect(MobEffects.DARKNESS);
        if (nseNausea) p.removeEffect(MobEffects.NAUSEA);
        if (nseLevitation) p.removeEffect(MobEffects.LEVITATION);
        if (nseSlowness) p.removeEffect(MobEffects.SLOWNESS);
        if (nseMiningFatigue) p.removeEffect(MobEffects.MINING_FATIGUE);
    }

    private static void runReach(LocalPlayer p) {
        AttributeInstance blocks = p.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        AttributeInstance entities = p.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (blocks == null || entities == null) return;
        if (reach) {
            if (blocks.getBaseValue() != reachBlocks) blocks.setBaseValue(reachBlocks);
            if (entities.getBaseValue() != reachEntities) entities.setBaseValue(reachEntities);
            reachApplied = true;
        } else if (reachApplied) {
            blocks.setBaseValue(4.5);
            entities.setBaseValue(3.0);
            reachApplied = false;
        }
    }

    private static void runRotation(LocalPlayer p) {
        if (rotYawMode == 1) {
            p.setYRot(rotYaw);
        } else if (rotYawMode == 2) {
            p.setYRot(Math.round(p.getYRot() / 45f) * 45f);
        }
        if (rotPitchMode == 1) p.setXRot(rotPitch);
    }

    /** Merkt sich den Block, der gerade abgebaut wird, und ob er weg ist (fuer Instant Rebreak). */
    private static void trackMining(Minecraft mc, boolean attackDown) {
        if (attackDown && mc.hitResult instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
            if (!bh.getBlockPos().equals(miningPos)) {
                miningPos = bh.getBlockPos();
                miningDir = bh.getDirection();
                if (!miningPos.equals(rebreakPos)) rebreakPos = null;
            }
        }
        if (miningPos != null && mc.level.getBlockState(miningPos).isAir()) {
            rebreakPos = miningPos;
            rebreakDir = miningDir;
            miningPos = null;
        }
    }

    private static void runInstantRebreak(Minecraft mc, LocalPlayer p) {
        if (rebreakPos == null) return;
        if (mc.level.getBlockState(rebreakPos).isAir()) return;
        if (p.getEyePosition().distanceToSqr(Vec3.atCenterOf(rebreakPos)) > 30) {
            rebreakPos = null;
            return;
        }
        if (++rebreakTicks < instantRebreakDelay) return;
        rebreakTicks = 0;
        sendRaw(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                rebreakPos, rebreakDir));
        p.swing(InteractionHand.MAIN_HAND);
    }

    private static void runSpeedMine(Minecraft mc) {
        if (destroyProgressField == null || isDestroyingField == null) return;
        try {
            if (!isDestroyingField.getBoolean(mc.gameMode)) {
                lastProgress = -1f;
                return;
            }
            float cur = destroyProgressField.getFloat(mc.gameMode);
            if (lastProgress >= 0f && cur > lastProgress) {
                float boosted = cur + (cur - lastProgress) * (speedMinePercent / 100f - 1f);
                cur = Math.min(boosted, 1f);
                destroyProgressField.setFloat(mc.gameMode, cur);
            }
            lastProgress = cur;
        } catch (ReflectiveOperationException | RuntimeException e) {
            lastProgress = -1f;
        }
    }

    /** No Mining Trace: vom GameRendererMixin nach dem Zielen aufgerufen. */
    public static void afterPick(Minecraft mc) {
        if (!noMiningTrace || mc.player == null || !(mc.hitResult instanceof EntityHitResult)) return;
        if (noMiningTraceTools && mc.player.getMainHandItem().getDestroySpeed(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()) <= 1f) {
            return;
        }
        double range = mc.player.blockInteractionRange();
        HitResult block = mc.player.pick(range, 1f, false);
        if (block.getType() == HitResult.Type.BLOCK) mc.hitResult = block;
    }

    // ---------- Einstellungs-Menues ----------

    public static Screen screen(String module, Screen parent) {
        return switch (module) {
            case "Air Place" -> new ModuleScreen(parent, module, f -> {
                f.description("Setzt Blöcke in die Luft, wenn du\nmit Rechtsklick ins Leere zielst.");
                f.section("Allgemein");
                f.slider("Abstand (Blöcke)", 1, 5, () -> airPlaceRange, v -> airPlaceRange = v, 4);
                f.footer(() -> airPlace, v -> airPlace = v);
            });
            case "Anti Afk" -> new ModuleScreen(parent, module, f -> {
                f.description("Bewegt dich regelmäßig, damit der\nServer dich nicht wegen AFK kickt.");
                f.section("Allgemein");
                f.slider("Abstand (s)", 3, 120, () -> antiAfkInterval, v -> antiAfkInterval = v, 15);
                f.toggle("Springen", () -> antiAfkJump, v -> antiAfkJump = v, true);
                f.toggle("Schlagen", () -> antiAfkSwing, v -> antiAfkSwing = v, true);
                f.toggle("Drehen", () -> antiAfkTurn, v -> antiAfkTurn = v, true);
                f.footer(() -> antiAfk, v -> antiAfk = v);
            });
            case "Anti Hunger" -> new ModuleScreen(parent, module, f -> {
                f.description("Verringert den Hunger, indem dem Server\nweniger Bewegung gemeldet wird.");
                f.section("Allgemein");
                f.toggle("Sprinten verbergen", () -> antiHungerSprint, v -> antiHungerSprint = v, true);
                f.toggle("Boden verbergen", () -> antiHungerGround, v -> antiHungerGround = v, true);
                f.footer(() -> antiHunger, v -> antiHunger = v);
            });
            case "Auto Eat" -> new ModuleScreen(parent, module, f -> {
                f.description("Isst automatisch das beste Essen\naus der Hotbar.");
                f.section("Allgemein");
                f.slider("Essen bei Hunger ≤", 1, 19, () -> autoEatHunger, v -> autoEatHunger = v, 14);
                f.toggle("Goldäpfel erlauben", () -> autoEatGapples, v -> autoEatGapples = v, false);
                f.footer(() -> autoEat, v -> autoEat = v);
            });
            case "Auto Fish" -> new ModuleScreen(parent, module, f -> {
                f.description("Holt die Angel bei einem Biss ein\nund wirft sie neu aus.");
                f.section("Allgemein");
                f.slider("Neu auswerfen nach (Ticks)", 1, 40, () -> autoFishDelay, v -> autoFishDelay = v, 10);
                f.footer(() -> autoFish, v -> autoFish = v);
            });
            case "Auto Gap" -> new ModuleScreen(parent, module, f -> {
                f.description("Isst einen Goldapfel, wenn das Leben\nniedrig ist (verzauberte zuerst).");
                f.section("Allgemein");
                f.slider("Leben ≤ (2 = 1 Herz)", 2, 19, () -> autoGapHealth, v -> autoGapHealth = v, 10);
                f.footer(() -> autoGap, v -> autoGap = v);
            });
            case "Auto Mend" -> new ModuleScreen(parent, module, f -> {
                f.description("Legt besch\u00e4digte Items mit Mending in die\nzweite Hand, damit XP sie repariert.");
                f.section("Allgemein");
                f.slider("Nur unter Haltbarkeit (%)", 10, 100, () -> autoMendBelow, v -> autoMendBelow = v, 100);
                f.toggle("Auch aus der Hotbar", () -> autoMendHotbar, v -> autoMendHotbar = v, true);
                f.footer(() -> autoMend, v -> autoMend = v);
            });
            case "Auto Replenish" -> new ModuleScreen(parent, module, f -> {
                f.description("Füllt Hotbar-Stapel aus dem Inventar auf.");
                f.section("Allgemein");
                f.slider("Auffüllen ab (Stück)", 1, 63, () -> autoReplenishMin, v -> autoReplenishMin = v, 8);
                f.footer(() -> autoReplenish, v -> autoReplenish = v);
            });
            case "Auto Respawn" -> new ModuleScreen(parent, module, f -> {
                f.description("Klickt nach dem Tod automatisch auf\n„Wiederbeleben“.");
                f.section("Allgemein");
                f.slider("Verzögerung (Ticks)", 0, 100, () -> autoRespawnDelay, v -> autoRespawnDelay = v, 10);
                f.footer(() -> autoRespawn, v -> autoRespawn = v);
            });
            case "Auto Tool" -> new ModuleScreen(parent, module, f -> {
                f.description("Nimmt beim Abbauen das schnellste\nWerkzeug aus der Hotbar.");
                f.section("Allgemein");
                f.toggle("Zurückwechseln", () -> autoToolSwitchBack, v -> autoToolSwitchBack = v, true);
                f.toggle("Fast kaputte schonen", () -> autoToolSave, v -> autoToolSave = v, true);
                f.footer(() -> autoTool, v -> autoTool = v);
            });
            case "Break Delay" -> new ModuleScreen(parent, module, f -> {
                f.description("Wartezeit zwischen zwei abgebauten\nBlöcken (normal 5 Ticks).");
                f.section("Allgemein");
                f.slider("Wartezeit (Ticks)", 0, 5, () -> breakDelayTicks, v -> breakDelayTicks = v, 0);
                f.footer(() -> breakDelay, v -> breakDelay = v);
            });
            case "Chest Swap" -> new ModuleScreen(parent, module, f -> {
                f.description("Tauscht einmal Elytra \u2194 Brustpanzer\nund schaltet sich wieder aus.");
                f.section("Allgemein");
                f.choice("Ziel", CHEST_TARGETS, () -> chestSwapTarget, v -> chestSwapTarget = v, 0);
                f.footer(() -> chestSwap, v -> chestSwap = v);
            });
            case "Exp Thrower" -> new ModuleScreen(parent, module, f -> {
                f.description("Wirft XP-Flaschen aus der Hotbar\nauf deine Füße.");
                f.section("Allgemein");
                f.slider("Abstand (Ticks)", 1, 20, () -> expThrowerDelay, v -> expThrowerDelay = v, 2);
                f.footer(() -> expThrower, v -> expThrower = v);
            });
            case "Fake Player" -> new ModuleScreen(parent, module, f -> {
                f.description("Erzeugt eine Kopie von dir an deiner\nStelle (nur bei dir sichtbar).\n\u00c4nderungen gelten beim n\u00e4chsten Einschalten.");
                f.section("Allgemein");
                f.text("Name", () -> fakeName, v -> fakeName = v, "FakePlayer");
                f.slider("Leben", 1, 20, () -> fakeHealth, v -> fakeHealth = v, 20);
                f.toggle("Inventar kopieren", () -> fakeCopyInv, v -> fakeCopyInv = v, true);
                f.footer(() -> fakePlayer, v -> fakePlayer = v);
            });
            case "Fast Use" -> new ModuleScreen(parent, module, f -> {
                f.description("Benutzt Items und setzt Blöcke schneller\n(normal 4 Ticks Pause).");
                f.section("Allgemein");
                f.slider("Pause (Ticks)", 0, 3, () -> fastUseDelay, v -> fastUseDelay = v, 0);
                f.footer(() -> fastUse, v -> fastUse = v);
            });
            case "Ghost Hand" -> new ModuleScreen(parent, module, f -> {
                f.description("\u00d6ffnet Kisten und \u00e4hnliches durch W\u00e4nde\n(Rechtsklick ohne Block in der Hand).");
                f.section("Allgemein");
                f.slider("Reichweite (Bl\u00f6cke)", 2, 6, () -> ghostHandRange, v -> ghostHandRange = v, 4);
                f.footer(() -> ghostHand, v -> ghostHand = v);
            });
            case "Instant Rebreak" -> new ModuleScreen(parent, module, f -> {
                f.description("Baut einen gerade abgebauten Block sofort\nwieder ab, wenn er neu gesetzt wird.");
                f.section("Allgemein");
                f.slider("Pause (Ticks)", 0, 20, () -> instantRebreakDelay, v -> instantRebreakDelay = v, 0);
                f.footer(() -> instantRebreak, v -> instantRebreak = v);
            });
            case "Liquid Interact" -> new ModuleScreen(parent, module, f -> {
                f.description("Bl\u00f6cke direkt auf Wasser oder Lava\nsetzen.");
                f.section("Allgemein");
                f.toggle("Auch Lava", () -> liquidLava, v -> liquidLava = v, true);
                f.slider("Reichweite (Bl\u00f6cke)", 2, 6, () -> liquidRange, v -> liquidRange = v, 4);
                f.footer(() -> liquidInteract, v -> liquidInteract = v);
            });
            case "Middle Click Extra" -> new ModuleScreen(parent, module, f -> {
                f.description("Mittlere Maustaste benutzt ein Item aus\nder Hotbar, ohne zu wechseln.");
                f.section("Allgemein");
                f.choice("Item", MIDDLE_MODES, () -> middleClickMode, v -> middleClickMode = v, 0);
                f.footer(() -> middleClickExtra, v -> middleClickExtra = v);
            });
            case "Multitask" -> new ModuleScreen(parent, module, f -> {
                f.description("Abbauen und angreifen, w\u00e4hrend du isst\noder mit dem Schild blockst.");
                f.section("Erlauben");
                f.toggle("Abbauen", () -> multitaskMine, v -> multitaskMine = v, true);
                f.toggle("Angreifen", () -> multitaskAttack, v -> multitaskAttack = v, true);
                f.footer(() -> multitask, v -> multitask = v);
            });
            case "Name Protect" -> new ModuleScreen(parent, module, f -> {
                f.description("Ersetzt deinen Namen im Chat.");
                f.section("Allgemein");
                f.text("Ersatzname", () -> nameProtectAlias, v -> nameProtectAlias = v, "Ich");
                f.footer(() -> nameProtect, v -> nameProtect = v);
            });
            case "No Interact" -> new ModuleScreen(parent, module, f -> {
                f.description("Verhindert Rechtsklick auf gewählte\nBlöcke und Wesen.");
                f.wideButton("Blöcke wählen (" + noInteractBlocks.size() + ")", false,
                        () -> Minecraft.getInstance().gui.setScreen(new PickerScreen(f, "No Interact: Blöcke",
                                PickerScreen.Kind.BLOCK, PickerScreen.ofSet(noInteractBlocks), null, null)));
                f.wideButton("Wesen wählen (" + noInteractEntities.size() + ")", false,
                        () -> Minecraft.getInstance().gui.setScreen(new PickerScreen(f, "No Interact: Wesen",
                                PickerScreen.Kind.ENTITY, PickerScreen.ofSet(noInteractEntities), null, null)));
                f.footer(() -> noInteract, v -> noInteract = v);
            });
            case "No Mining Trace" -> new ModuleScreen(parent, module, f -> {
                f.description("Baut durch Wesen hindurch ab, die\nvor dem Block stehen.");
                f.section("Allgemein");
                f.toggle("Nur mit Werkzeug", () -> noMiningTraceTools, v -> noMiningTraceTools = v, true);
                f.footer(() -> noMiningTrace, v -> noMiningTrace = v);
            });
            case "No Rotate" -> new ModuleScreen(parent, module, f -> {
                f.description("Der Server kann deine Blickrichtung\nnicht mehr verstellen.");
                f.section("Festhalten");
                f.toggle("Drehung (links/rechts)", () -> noRotateYaw, v -> noRotateYaw = v, true);
                f.toggle("Neigung (hoch/runter)", () -> noRotatePitch, v -> noRotatePitch = v, true);
                f.footer(() -> noRotate, v -> noRotate = v);
            });
            case "No Status Effects" -> new ModuleScreen(parent, module, f -> {
                f.description("Entfernt störende Effekte bei dir.");
                f.section("Effekte");
                f.toggle("Blindheit", () -> nseBlindness, v -> nseBlindness = v, true);
                f.toggle("Dunkelheit", () -> nseDarkness, v -> nseDarkness = v, true);
                f.toggle("Übelkeit", () -> nseNausea, v -> nseNausea = v, true);
                f.toggle("Schwebeeffekt", () -> nseLevitation, v -> nseLevitation = v, false);
                f.toggle("Langsamkeit", () -> nseSlowness, v -> nseSlowness = v, false);
                f.toggle("Abbaulähmung", () -> nseMiningFatigue, v -> nseMiningFatigue = v, false);
                f.footer(() -> noStatusEffects, v -> noStatusEffects = v);
            });
            case "Portals" -> new ModuleScreen(parent, module, f -> {
                f.description("Menüs und Chat bleiben in Netherportalen\noffen.");
                f.footer(() -> portals, v -> portals = v);
            });
            case "Potion Saver" -> new ModuleScreen(parent, module, f -> {
                f.description("Hält Tränke an, solange du stillstehst.\nWirkt nur auf manchen Servern.");
                f.footer(() -> potionSaver, v -> potionSaver = v);
            });
            case "Reach" -> new ModuleScreen(parent, module, f -> {
                f.description("Größere Reichweite (normal 4,5 / 3).\nServer begrenzen das oft.");
                f.section("Allgemein");
                f.slider("Blöcke", 3, 10, () -> reachBlocks, v -> reachBlocks = v, 5);
                f.slider("Wesen", 3, 8, () -> reachEntities, v -> reachEntities = v, 4);
                f.footer(() -> reach, v -> reach = v);
            });
            case "Rotation" -> new ModuleScreen(parent, module, f -> {
                f.description("Blickrichtung festhalten oder einrasten.");
                f.section("Allgemein");
                f.choice("Drehung", YAW_MODES, () -> rotYawMode, v -> rotYawMode = v, 0);
                f.slider("Drehung (°)", -180, 180, () -> rotYaw, v -> rotYaw = v, 0);
                f.choice("Neigung", PITCH_MODES, () -> rotPitchMode, v -> rotPitchMode = v, 0);
                f.slider("Neigung (°)", -90, 90, () -> rotPitch, v -> rotPitch = v, 0);
                f.footer(() -> rotation, v -> rotation = v);
            });
            case "Speed Mine" -> new ModuleScreen(parent, module, f -> {
                f.description("Baut schneller ab. Über ~140 % lehnen\nviele Server den Abbau ab.");
                f.section("Allgemein");
                f.slider("Tempo (%)", 100, 300, () -> speedMinePercent, v -> speedMinePercent = v, 130);
                f.footer(() -> speedMine, v -> speedMine = v);
            });
            default -> null;
        };
    }
}
