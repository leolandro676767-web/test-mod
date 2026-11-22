package de.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.StringJoiner;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Speichert und laedt alle Einstellungen in config/test.properties. */
public final class TestConfig {
    private static final Path FILE =
            FabricLoader.getInstance().getConfigDir().resolve("test.properties");

    private TestConfig() {}

    public static void load() {
        if (defaults == null) {
            // Standardwerte merken (vor dem ersten Laden), fuer das Teilen als Text
            Keybinds.load(new Properties()); // Standardtasten (G, J, K) im 1912-Menue
            RenderMods.xrayDefaults();
            defaults = snapshot();
        }
        if (!Files.exists(FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
        } catch (IOException e) {
            System.err.println("[1912] Konnte Einstellungen nicht laden: " + e);
            return;
        }
        apply(p);
    }

    /** Uebernimmt alle Einstellungen aus p (fehlende Eintraege behalten ihren jetzigen Wert). */
    private static void apply(Properties p) {
        TestClient.sprint = bool(p, "sprint", TestClient.sprint);
        TestClient.sprintJump = bool(p, "sprintJump", TestClient.sprintJump);
        TestClient.autoMine = bool(p, "autoMine", TestClient.autoMine);
        TestClient.autoLogout = bool(p, "autoLogout", TestClient.autoLogout);
        TestClient.logoutHearts = Math.max(1, Math.min(10, num(p, "logoutHearts", TestClient.logoutHearts)));
        TestClient.logoutOnHealth = bool(p, "logoutOnHealth", TestClient.logoutOnHealth);
        TestClient.logoutPlayers = bool(p, "logoutPlayers", TestClient.logoutPlayers);
        TestClient.logoutPlayerRange = Math.max(0, Math.min(256, num(p, "logoutPlayerRange", TestClient.logoutPlayerRange)));
        TestClient.autoDrop = bool(p, "autoDrop", TestClient.autoDrop);
        TestClient.autoClicker = bool(p, "autoClicker", TestClient.autoClicker);
        TestClient.clickerRandom = Math.max(0, Math.min(100, num(p, "clickerRandom", TestClient.clickerRandom)));
        TestClient.clickerHold = bool(p, "clickerHold", TestClient.clickerHold);
        TestClient.clickerEarly = bool(p, "clickerEarly", TestClient.clickerEarly);
        TestClient.searchChunks = Math.max(1, Math.min(12, num(p, "searchChunks", TestClient.searchChunks)));
        TestClient.searchHeight = Math.max(2, Math.min(20, num(p, "searchHeight", TestClient.searchHeight)));
        TestClient.blockCollect = bool(p, "blockCollect", TestClient.blockCollect);
        TestClient.blockCollectAll = bool(p, "blockCollectAll", TestClient.blockCollectAll);
        TestClient.blockCollectRadius = Math.max(2, Math.min(16, num(p, "blockCollectRadius",
                TestClient.blockCollectRadius)));
        TestClient.areaCollect = bool(p, "areaCollect", TestClient.areaCollect);
        TestClient.areaCollectRadius = Math.max(2, Math.min(16, num(p, "areaCollectRadius",
                TestClient.areaCollectRadius)));
        Theme.scheme = num(p, "themeScheme", Theme.scheme);
        Theme.headHex = num(p, "themeHead", Theme.headHex);
        Theme.accentHex = num(p, "themeAccent", Theme.accentHex);
        Theme.showLogo = bool(p, "themeLogo", Theme.showLogo);
        Theme.animations = bool(p, "themeAnim", Theme.animations);
        Theme.logoSize = num(p, "themeLogoSize", Theme.logoSize);
        Theme.colWidth = num(p, "themeColWidth", Theme.colWidth);
        Theme.menuScale = num(p, "themeMenuScale", Theme.menuScale);
        // Modus: Anfänger / Standard / Experte (frueher nur Standard / Experte)
        if (p.containsKey("menuLevel")) {
            Levels.level = Math.max(0, Math.min(2, num(p, "menuLevel", Levels.level)));
        } else if (p.containsKey("themeMenuMode")) {
            Levels.level = num(p, "themeMenuMode", 0) == 1 ? Levels.EXPERT : Levels.STANDARD;
        }
        Theme.hudList = num(p, "themeHudList", 1) == 1;
        Theme.lowFx = num(p, "themeLowFx", 0) == 1;
        Theme.hudCorner = Math.max(0, Math.min(3, num(p, "themeHudCorner", 0)));
        Theme.hudListSize = Math.max(0, Math.min(3, num(p, "themeHudListSize", Theme.hudListSize)));
        // neue Spalten (Version 2): alte eingeklappte Spalten passen nicht mehr dazu
        Theme.collapsed = p.containsKey("menuColumns2") ? num(p, "menuCollapsed", Theme.collapsed) & 0xFFFF : 0;
        // Menue anpassen: dazugeholte / ausgeblendete Module, Reihenfolge der Spalten
        readNames(p.getProperty("menuShow"), Levels.shown);
        readNames(p.getProperty("menuHide"), Levels.hidden);
        TestScreen.ORDER.clear();
        for (String part : p.getProperty("menuOrder", "").split(",")) {
            try {
                if (!part.isBlank()) TestScreen.ORDER.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // kaputter Eintrag
            }
        }
        Theme.clamp();
        Movement.highJump = bool(p, "highJump", Movement.highJump);
        Movement.jesus = bool(p, "jesus", Movement.jesus);
        Movement.jesusLava = bool(p, "jesusLava", Movement.jesusLava);
        Movement.longJump = bool(p, "longJump", Movement.longJump);
        Movement.noFall = bool(p, "noFall", Movement.noFall);
        Movement.noSlow = bool(p, "noSlow", Movement.noSlow);
        Movement.parkour = bool(p, "parkour", Movement.parkour);
        Movement.safeWalk = bool(p, "safeWalk", Movement.safeWalk);
        Movement.scaffold = bool(p, "scaffold", Movement.scaffold);
        Movement.scaffoldLook = bool(p, "scaffoldLook", Movement.scaffoldLook);
        Movement.highJumpHeight = Math.max(2, Math.min(10, num(p, "highJumpHeight", Movement.highJumpHeight)));
        Movement.longJumpPower = Math.max(110, Math.min(500, num(p, "longJumpPower", Movement.longJumpPower)));
        Movement.scaffoldPerTick = Math.max(1, Math.min(3, num(p, "scaffoldPerTick", Movement.scaffoldPerTick)));
        Movement.scaffoldTower = bool(p, "scaffoldTower", Movement.scaffoldTower);
        readIds(p.getProperty("scaffoldSkip"), BuiltInRegistries.BLOCK, Movement.scaffoldSkip);
        Movement.speedPauseLiquid = bool(p, "speedPauseLiquid", Movement.speedPauseLiquid);
        Movement.speedSprintOnly = bool(p, "speedSprintOnly", Movement.speedSprintOnly);
        Movement.stepSneakOff = bool(p, "stepSneakOff", Movement.stepSneakOff);
        Movement2.airJump = bool(p, "airJump", Movement2.airJump);
        Movement2.airJumpMax = Math.max(0, Math.min(10, num(p, "airJumpMax", Movement2.airJumpMax)));
        Movement2.airJumpPower = Math.max(20, Math.min(100, num(p, "airJumpPower", Movement2.airJumpPower)));
        Movement2.anchor = bool(p, "anchor", Movement2.anchor);
        Movement2.antiVoid = bool(p, "antiVoid", Movement2.antiVoid);
        Movement2.autoJump = bool(p, "autoJump", Movement2.autoJump);
        Movement2.autoJumpMoving = bool(p, "autoJumpMoving", Movement2.autoJumpMoving);
        Movement2.autoWasp = bool(p, "autoWasp", Movement2.autoWasp);
        Movement2.clickTp = bool(p, "clickTp", Movement2.clickTp);
        Movement2.elytraBoost = bool(p, "elytraBoost", Movement2.elytraBoost);
        Movement2.elytraBoostKey = bool(p, "elytraBoostKey", Movement2.elytraBoostKey);
        Movement2.elytraFly = bool(p, "elytraFly", Movement2.elytraFly);
        Movement2.entityControl = bool(p, "entityControl", Movement2.entityControl);
        Movement2.fastClimb = bool(p, "fastClimb", Movement2.fastClimb);
        Movement2.flight = bool(p, "flight", Movement2.flight);
        Movement2.flightAntiKick = bool(p, "flightAntiKick", Movement2.flightAntiKick);
        Movement2.guiMove = bool(p, "guiMove", Movement2.guiMove);
        Movement2.guiMoveSneak = bool(p, "guiMoveSneak", Movement2.guiMoveSneak);
        Movement2.anchorHeight = Math.max(1, Math.min(20, num(p, "anchorHeight", Movement2.anchorHeight)));
        Movement2.anchorPull = Math.max(10, Math.min(100, num(p, "anchorPull", Movement2.anchorPull)));
        Movement2.antiVoidMode = Math.max(0, Math.min(1, num(p, "antiVoidMode", Movement2.antiVoidMode)));
        Movement2.waspChunks = Math.max(1, Math.min(32, num(p, "waspChunks", Movement2.waspChunks)));
        Movement2.waspSpeed = Math.max(5, Math.min(80, num(p, "waspSpeed", Movement2.waspSpeed)));
        Movement2.clickTpRange = Math.max(1, Math.min(8, num(p, "clickTpRange", Movement2.clickTpRange)));
        Movement2.elytraBoostPower = Math.max(1, Math.min(10, num(p, "elytraBoostPower", Movement2.elytraBoostPower)));
        Movement2.elytraBoostMax = Math.max(10, Math.min(100, num(p, "elytraBoostMax", Movement2.elytraBoostMax)));
        Movement2.elytraFlySpeed = Math.max(5, Math.min(60, num(p, "elytraFlySpeed", Movement2.elytraFlySpeed)));
        Movement2.elytraFlyVertical = Math.max(2, Math.min(30, num(p, "elytraFlyVertical", Movement2.elytraFlyVertical)));
        Movement2.entitySpeed = Math.max(4, Math.min(40, num(p, "entitySpeed", Movement2.entitySpeed)));
        Movement2.fastClimbSpeed = Math.max(1, Math.min(10, num(p, "fastClimbSpeed", Movement2.fastClimbSpeed)));
        Movement2.flightMode = Math.max(0, Math.min(1, num(p, "flightMode", Movement2.flightMode)));
        Movement2.flightSpeed = Math.max(2, Math.min(60, num(p, "flightSpeed", Movement2.flightSpeed)));
        Movement2.flightVertical = Math.max(2, Math.min(30, num(p, "flightVertical", Movement2.flightVertical)));
        Movement.sneakOnlyMoving = bool(p, "sneakOnlyMoving", Movement.sneakOnlyMoving);
        Movement.noSlowFood = bool(p, "noSlowFood", Movement.noSlowFood);
        Movement.noSlowShield = bool(p, "noSlowShield", Movement.noSlowShield);
        Movement.noSlowBow = bool(p, "noSlowBow", Movement.noSlowBow);
        Movement.noSlowOther = bool(p, "noSlowOther", Movement.noSlowOther);
        Movement.parkourSprintOnly = bool(p, "parkourSprintOnly", Movement.parkourSprintOnly);
        Movement.safeWalkNotJumping = bool(p, "safeWalkNotJumping", Movement.safeWalkNotJumping);
        Movement.noFallHeight = Math.max(2, Math.min(20, num(p, "noFallHeight", Movement.noFallHeight)));
        Movement.noSlowPercent = Math.max(30, Math.min(100, num(p, "noSlowPercent", Movement.noSlowPercent)));
        Movement.parkourLead = Math.max(1, Math.min(4, num(p, "parkourLead", Movement.parkourLead)));
        Movement.safeWalkDist = Math.max(1, Math.min(8, num(p, "safeWalkDist", Movement.safeWalkDist)));
        Movement.slippy = bool(p, "slippy", Movement.slippy);
        Movement.slippyAmount = Math.max(60, Math.min(99, num(p, "slippyAmount", Movement.slippyAmount)));
        Movement.sneak = bool(p, "sneak", Movement.sneak);
        Movement.speed = bool(p, "speed", Movement.speed);
        Movement.speedBps = Math.max(4, Math.min(30, num(p, "speedBps", Movement.speedBps)));
        Movement.speedGroundOnly = bool(p, "speedGroundOnly", Movement.speedGroundOnly);
        Movement.speedAutoJump = bool(p, "speedAutoJump", Movement.speedAutoJump);
        Movement.spider = bool(p, "spider", Movement.spider);
        Movement.spiderSpeed = Math.max(1, Math.min(10, num(p, "spiderSpeed", Movement.spiderSpeed)));
        Movement.autoSprint = bool(p, "autoSprint", Movement.autoSprint);
        Movement.sprintOmni = bool(p, "sprintOmni", Movement.sprintOmni);
        Movement.step = bool(p, "step", Movement.step);
        Movement.stepHeight = Math.max(1, Math.min(5, num(p, "stepHeight", Movement.stepHeight)));
        Movement.trident = bool(p, "trident", Movement.trident);
        Movement.tridentPower = Math.max(100, Math.min(500, num(p, "tridentPower", Movement.tridentPower)));
        loadStatics(p, PlayerMods.class, "pm.");
        readIds(p.getProperty("pm.noInteractBlocks"), BuiltInRegistries.BLOCK, PlayerMods.noInteractBlocks);
        readIds(p.getProperty("pm.noInteractEntities"), BuiltInRegistries.ENTITY_TYPE, PlayerMods.noInteractEntities);
        loadStatics(p, RenderMods.class, "rm.");
        readIds(p.getProperty("rm.blockEspBlocks"), BuiltInRegistries.BLOCK, RenderMods.blockEspBlocks);
        readIds(p.getProperty("rm.xrayBlocks"), BuiltInRegistries.BLOCK, RenderMods.xrayBlocks);
        loadStatics(p, MiscMods.class, "mm.");
        // alter Name als Standardtext: auf den neuen Namen umstellen
        if ("1912 Client".equals(MiscMods.discordText)) MiscMods.discordText = "1912 Utilities";
        if ("1912 Client".equals(MiscMods.bookBotText)) MiscMods.bookBotText = "1912 Utilities";
        if ("1912 Client".equals(MiscMods.spamText)) MiscMods.spamText = "1912 Utilities";
        loadStatics(p, DebrisMiner.class, "dm.");
        loadStatics(p, TunnelMiner.class, "tm.");
        loadStatics(p, Friends.class, "fr.");
        loadStatics(p, KillAura.class, "ka.");
        loadStatics(p, Survival.class, "sv.");
        loadStatics(p, InfoHud.class, "ih.");
        loadStatics(p, Extras.class, "ex.");
        loadStatics(p, CombatMods.class, "cb.");
        loadStatics(p, WorldMods.class, "wo.");
        loadStatics(p, TravelMods.class, "tv.");
        loadStatics(p, HudMods.class, "hm.");
        loadStatics(p, BaseFinder.class, "bf.");
        readIds(p.getProperty("wo.nukerBlocks"), BuiltInRegistries.BLOCK, WorldMods.nukerBlocks);
        readIds(p.getProperty("wo.stealItems"), BuiltInRegistries.ITEM, WorldMods.stealItems);
        Keybinds.load(p);
        InvLayout.load(p);
        Lang.language = Math.max(0, Math.min(Lang.NAMES.length - 1, num(p, "language", Lang.language)));
        Esp.enabled = bool(p, "espEnabled", Esp.enabled);
        Esp.colPlayer = color(num(p, "espColPlayer", Esp.colPlayer));
        Esp.colMob = color(num(p, "espColMob", Esp.colMob));
        Esp.colItem = color(num(p, "espColItem", Esp.colItem));
        Esp.colBlock = color(num(p, "espColBlock", Esp.colBlock));
        Esp.players = bool(p, "espPlayers", Esp.players);
        Esp.spectators = bool(p, "espSpectators", Esp.spectators);
        Esp.mobs = bool(p, "espMobs", Esp.mobs);
        Esp.items = bool(p, "espItems", Esp.items);
        Esp.blocks = bool(p, "espBlocks", Esp.blocks);
        Esp.box = bool(p, "espBox", Esp.box);
        Esp.tracers = bool(p, "espTracers", Esp.tracers);
        Esp.labels = bool(p, "espLabels", Esp.labels);
        Esp.rangeChunks = Math.max(1, Math.min(32, num(p, "espRangeChunks", Esp.rangeChunks)));
        Esp.blockChunks = Math.max(1, Math.min(12, num(p, "espBlockChunks", Esp.blockChunks)));
        readIds(p.getProperty("espMobTypes"), BuiltInRegistries.ENTITY_TYPE, Esp.mobTypes);
        readIds(p.getProperty("espItemTypes"), BuiltInRegistries.ITEM, Esp.itemTypes);
        readIds(p.getProperty("espBlockTypes"), BuiltInRegistries.BLOCK, Esp.blockTypes);
        String drops = p.getProperty("dropItems");
        if (drops != null) {
            TestClient.dropItems.clear();
            for (String part : drops.split(",")) {
                Identifier id = Identifier.tryParse(part.trim());
                if (id == null) continue;
                Item it = BuiltInRegistries.ITEM.getValue(id);
                if (it != Items.AIR) TestClient.dropItems.add(it);
            }
        }
        String blockId = p.getProperty("block");
        if (blockId != null) {
            Identifier id = Identifier.tryParse(blockId.trim());
            if (id != null) {
                Block b = BuiltInRegistries.BLOCK.getValue(id);
                TestClient.selectedBlock = b == Blocks.AIR ? null : b;
            }
        }
        Walker.allowPlace = bool(p, "walkPlace", Walker.allowPlace);
        Walker.allowParkour = bool(p, "walkParkour", Walker.allowParkour);
        Walker.sprint = bool(p, "walkSprint", Walker.sprint);
        Walker.showPath = bool(p, "walkShowPath", Walker.showPath);
        Walker.maxFall = Math.max(1, Math.min(10, num(p, "walkMaxFall", Walker.maxFall)));
        TestClient.gx = num(p, "gx", TestClient.gx);
        TestClient.gy = num(p, "gy", TestClient.gy);
        TestClient.gz = num(p, "gz", TestClient.gz);
        TestClient.ax1 = num(p, "ax1", TestClient.ax1);
        TestClient.ay1 = num(p, "ay1", TestClient.ay1);
        TestClient.az1 = num(p, "az1", TestClient.az1);
        TestClient.ax2 = num(p, "ax2", TestClient.ax2);
        TestClient.ay2 = num(p, "ay2", TestClient.ay2);
        TestClient.az2 = num(p, "az2", TestClient.az2);
    }

    public static void save() {
        Properties p = snapshot();
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "1912 Utilities Einstellungen");
            }
        } catch (IOException e) {
            System.err.println("[1912] Konnte Einstellungen nicht speichern: " + e);
        }
    }

    /** Alle Einstellungen, wie sie gespeichert werden. */
    private static Properties snapshot() {
        Properties p = new Properties();
        p.setProperty("sprint", Boolean.toString(TestClient.sprint));
        p.setProperty("sprintJump", Boolean.toString(TestClient.sprintJump));
        p.setProperty("autoMine", Boolean.toString(TestClient.autoMine));
        p.setProperty("autoLogout", Boolean.toString(TestClient.autoLogout));
        p.setProperty("logoutHearts", Integer.toString(TestClient.logoutHearts));
        p.setProperty("logoutOnHealth", Boolean.toString(TestClient.logoutOnHealth));
        p.setProperty("logoutPlayers", Boolean.toString(TestClient.logoutPlayers));
        p.setProperty("logoutPlayerRange", Integer.toString(TestClient.logoutPlayerRange));
        p.setProperty("autoDrop", Boolean.toString(TestClient.autoDrop));
        p.setProperty("autoClicker", Boolean.toString(TestClient.autoClicker));
        p.setProperty("clickerRandom", Integer.toString(TestClient.clickerRandom));
        p.setProperty("clickerHold", Boolean.toString(TestClient.clickerHold));
        p.setProperty("clickerEarly", Boolean.toString(TestClient.clickerEarly));
        p.setProperty("searchChunks", Integer.toString(TestClient.searchChunks));
        p.setProperty("searchHeight", Integer.toString(TestClient.searchHeight));
        p.setProperty("blockCollect", Boolean.toString(TestClient.blockCollect));
        p.setProperty("blockCollectAll", Boolean.toString(TestClient.blockCollectAll));
        p.setProperty("blockCollectRadius", Integer.toString(TestClient.blockCollectRadius));
        p.setProperty("areaCollect", Boolean.toString(TestClient.areaCollect));
        p.setProperty("areaCollectRadius", Integer.toString(TestClient.areaCollectRadius));
        p.setProperty("themeScheme", Integer.toString(Theme.scheme));
        p.setProperty("themeHead", Integer.toString(Theme.headHex));
        p.setProperty("themeAccent", Integer.toString(Theme.accentHex));
        p.setProperty("themeLogo", Boolean.toString(Theme.showLogo));
        p.setProperty("themeAnim", Boolean.toString(Theme.animations));
        p.setProperty("themeLogoSize", Integer.toString(Theme.logoSize));
        p.setProperty("themeColWidth", Integer.toString(Theme.colWidth));
        p.setProperty("menuCollapsed", Integer.toString(Theme.collapsed));
        p.setProperty("menuColumns2", "true");
        p.setProperty("themeMenuScale", Integer.toString(Theme.menuScale));
        p.setProperty("menuLevel", Integer.toString(Levels.level));
        p.setProperty("menuShow", String.join(";", Levels.shown));
        p.setProperty("menuHide", String.join(";", Levels.hidden));
        StringBuilder order = new StringBuilder();
        for (int bit : TestScreen.ORDER) order.append(order.length() > 0 ? "," : "").append(bit);
        p.setProperty("menuOrder", order.toString());
        p.setProperty("themeHudList", Theme.hudList ? "1" : "0");
        p.setProperty("themeLowFx", Theme.lowFx ? "1" : "0");
        p.setProperty("themeHudCorner", Integer.toString(Theme.hudCorner));
        p.setProperty("themeHudListSize", Integer.toString(Theme.hudListSize));
        StringJoiner drops = new StringJoiner(",");
        for (Item it : TestClient.dropItems) {
            drops.add(BuiltInRegistries.ITEM.getKey(it).toString());
        }
        p.setProperty("dropItems", drops.toString());
        p.setProperty("highJump", Boolean.toString(Movement.highJump));
        p.setProperty("jesus", Boolean.toString(Movement.jesus));
        p.setProperty("jesusLava", Boolean.toString(Movement.jesusLava));
        p.setProperty("longJump", Boolean.toString(Movement.longJump));
        p.setProperty("noFall", Boolean.toString(Movement.noFall));
        p.setProperty("noSlow", Boolean.toString(Movement.noSlow));
        p.setProperty("parkour", Boolean.toString(Movement.parkour));
        p.setProperty("safeWalk", Boolean.toString(Movement.safeWalk));
        p.setProperty("scaffold", Boolean.toString(Movement.scaffold));
        p.setProperty("scaffoldLook", Boolean.toString(Movement.scaffoldLook));
        p.setProperty("highJumpHeight", Integer.toString(Movement.highJumpHeight));
        p.setProperty("longJumpPower", Integer.toString(Movement.longJumpPower));
        p.setProperty("scaffoldPerTick", Integer.toString(Movement.scaffoldPerTick));
        p.setProperty("scaffoldTower", Boolean.toString(Movement.scaffoldTower));
        p.setProperty("scaffoldSkip", writeIds(Movement.scaffoldSkip, BuiltInRegistries.BLOCK));
        p.setProperty("speedPauseLiquid", Boolean.toString(Movement.speedPauseLiquid));
        p.setProperty("speedSprintOnly", Boolean.toString(Movement.speedSprintOnly));
        p.setProperty("stepSneakOff", Boolean.toString(Movement.stepSneakOff));
        p.setProperty("airJump", Boolean.toString(Movement2.airJump));
        p.setProperty("airJumpMax", Integer.toString(Movement2.airJumpMax));
        p.setProperty("airJumpPower", Integer.toString(Movement2.airJumpPower));
        p.setProperty("anchor", Boolean.toString(Movement2.anchor));
        p.setProperty("antiVoid", Boolean.toString(Movement2.antiVoid));
        p.setProperty("autoJump", Boolean.toString(Movement2.autoJump));
        p.setProperty("autoJumpMoving", Boolean.toString(Movement2.autoJumpMoving));
        p.setProperty("autoWasp", Boolean.toString(Movement2.autoWasp));
        p.setProperty("clickTp", Boolean.toString(Movement2.clickTp));
        p.setProperty("elytraBoost", Boolean.toString(Movement2.elytraBoost));
        p.setProperty("elytraBoostKey", Boolean.toString(Movement2.elytraBoostKey));
        p.setProperty("elytraFly", Boolean.toString(Movement2.elytraFly));
        p.setProperty("entityControl", Boolean.toString(Movement2.entityControl));
        p.setProperty("fastClimb", Boolean.toString(Movement2.fastClimb));
        p.setProperty("flight", Boolean.toString(Movement2.flight));
        p.setProperty("flightAntiKick", Boolean.toString(Movement2.flightAntiKick));
        p.setProperty("guiMove", Boolean.toString(Movement2.guiMove));
        p.setProperty("guiMoveSneak", Boolean.toString(Movement2.guiMoveSneak));
        p.setProperty("anchorHeight", Integer.toString(Movement2.anchorHeight));
        p.setProperty("anchorPull", Integer.toString(Movement2.anchorPull));
        p.setProperty("antiVoidMode", Integer.toString(Movement2.antiVoidMode));
        p.setProperty("waspChunks", Integer.toString(Movement2.waspChunks));
        p.setProperty("waspSpeed", Integer.toString(Movement2.waspSpeed));
        p.setProperty("clickTpRange", Integer.toString(Movement2.clickTpRange));
        p.setProperty("elytraBoostPower", Integer.toString(Movement2.elytraBoostPower));
        p.setProperty("elytraBoostMax", Integer.toString(Movement2.elytraBoostMax));
        p.setProperty("elytraFlySpeed", Integer.toString(Movement2.elytraFlySpeed));
        p.setProperty("elytraFlyVertical", Integer.toString(Movement2.elytraFlyVertical));
        p.setProperty("entitySpeed", Integer.toString(Movement2.entitySpeed));
        p.setProperty("fastClimbSpeed", Integer.toString(Movement2.fastClimbSpeed));
        p.setProperty("flightMode", Integer.toString(Movement2.flightMode));
        p.setProperty("flightSpeed", Integer.toString(Movement2.flightSpeed));
        p.setProperty("flightVertical", Integer.toString(Movement2.flightVertical));
        p.setProperty("sneakOnlyMoving", Boolean.toString(Movement.sneakOnlyMoving));
        p.setProperty("noSlowFood", Boolean.toString(Movement.noSlowFood));
        p.setProperty("noSlowShield", Boolean.toString(Movement.noSlowShield));
        p.setProperty("noSlowBow", Boolean.toString(Movement.noSlowBow));
        p.setProperty("noSlowOther", Boolean.toString(Movement.noSlowOther));
        p.setProperty("parkourSprintOnly", Boolean.toString(Movement.parkourSprintOnly));
        p.setProperty("safeWalkNotJumping", Boolean.toString(Movement.safeWalkNotJumping));
        p.setProperty("noFallHeight", Integer.toString(Movement.noFallHeight));
        p.setProperty("noSlowPercent", Integer.toString(Movement.noSlowPercent));
        p.setProperty("parkourLead", Integer.toString(Movement.parkourLead));
        p.setProperty("safeWalkDist", Integer.toString(Movement.safeWalkDist));
        p.setProperty("slippy", Boolean.toString(Movement.slippy));
        p.setProperty("slippyAmount", Integer.toString(Movement.slippyAmount));
        p.setProperty("sneak", Boolean.toString(Movement.sneak));
        p.setProperty("speed", Boolean.toString(Movement.speed));
        p.setProperty("speedBps", Integer.toString(Movement.speedBps));
        p.setProperty("speedGroundOnly", Boolean.toString(Movement.speedGroundOnly));
        p.setProperty("speedAutoJump", Boolean.toString(Movement.speedAutoJump));
        p.setProperty("spider", Boolean.toString(Movement.spider));
        p.setProperty("spiderSpeed", Integer.toString(Movement.spiderSpeed));
        p.setProperty("autoSprint", Boolean.toString(Movement.autoSprint));
        p.setProperty("sprintOmni", Boolean.toString(Movement.sprintOmni));
        p.setProperty("step", Boolean.toString(Movement.step));
        p.setProperty("stepHeight", Integer.toString(Movement.stepHeight));
        p.setProperty("trident", Boolean.toString(Movement.trident));
        p.setProperty("tridentPower", Integer.toString(Movement.tridentPower));
        saveStatics(p, PlayerMods.class, "pm.");
        p.setProperty("pm.noInteractBlocks", writeIds(PlayerMods.noInteractBlocks, BuiltInRegistries.BLOCK));
        p.setProperty("pm.noInteractEntities", writeIds(PlayerMods.noInteractEntities, BuiltInRegistries.ENTITY_TYPE));
        saveStatics(p, RenderMods.class, "rm.");
        p.setProperty("rm.blockEspBlocks", writeIds(RenderMods.blockEspBlocks, BuiltInRegistries.BLOCK));
        p.setProperty("rm.xrayBlocks", writeIds(RenderMods.xrayBlocks, BuiltInRegistries.BLOCK));
        saveStatics(p, MiscMods.class, "mm.");
        saveStatics(p, DebrisMiner.class, "dm.");
        saveStatics(p, TunnelMiner.class, "tm.");
        saveStatics(p, Friends.class, "fr.");
        saveStatics(p, KillAura.class, "ka.");
        saveStatics(p, Survival.class, "sv.");
        saveStatics(p, InfoHud.class, "ih.");
        saveStatics(p, Extras.class, "ex.");
        saveStatics(p, CombatMods.class, "cb.");
        saveStatics(p, WorldMods.class, "wo.");
        saveStatics(p, TravelMods.class, "tv.");
        saveStatics(p, HudMods.class, "hm.");
        saveStatics(p, BaseFinder.class, "bf.");
        p.setProperty("wo.nukerBlocks", writeIds(WorldMods.nukerBlocks, BuiltInRegistries.BLOCK));
        p.setProperty("wo.stealItems", writeIds(WorldMods.stealItems, BuiltInRegistries.ITEM));
        Keybinds.save(p);
        InvLayout.save(p);
        p.setProperty("language", Integer.toString(Lang.language));
        p.setProperty("espEnabled", Boolean.toString(Esp.enabled));
        p.setProperty("espColPlayer", Integer.toString(Esp.colPlayer));
        p.setProperty("espColMob", Integer.toString(Esp.colMob));
        p.setProperty("espColItem", Integer.toString(Esp.colItem));
        p.setProperty("espColBlock", Integer.toString(Esp.colBlock));
        p.setProperty("espPlayers", Boolean.toString(Esp.players));
        p.setProperty("espSpectators", Boolean.toString(Esp.spectators));
        p.setProperty("espMobs", Boolean.toString(Esp.mobs));
        p.setProperty("espItems", Boolean.toString(Esp.items));
        p.setProperty("espBlocks", Boolean.toString(Esp.blocks));
        p.setProperty("espBox", Boolean.toString(Esp.box));
        p.setProperty("espTracers", Boolean.toString(Esp.tracers));
        p.setProperty("espLabels", Boolean.toString(Esp.labels));
        p.setProperty("espRangeChunks", Integer.toString(Esp.rangeChunks));
        p.setProperty("espBlockChunks", Integer.toString(Esp.blockChunks));
        p.setProperty("espMobTypes", writeIds(Esp.mobTypes, BuiltInRegistries.ENTITY_TYPE));
        p.setProperty("espItemTypes", writeIds(Esp.itemTypes, BuiltInRegistries.ITEM));
        p.setProperty("espBlockTypes", writeIds(Esp.blockTypes, BuiltInRegistries.BLOCK));
        if (TestClient.selectedBlock != null) {
            p.setProperty("block", BuiltInRegistries.BLOCK.getKey(TestClient.selectedBlock).toString());
        }
        p.setProperty("walkPlace", Boolean.toString(Walker.allowPlace));
        p.setProperty("walkParkour", Boolean.toString(Walker.allowParkour));
        p.setProperty("walkSprint", Boolean.toString(Walker.sprint));
        p.setProperty("walkShowPath", Boolean.toString(Walker.showPath));
        p.setProperty("walkMaxFall", Integer.toString(Walker.maxFall));
        p.setProperty("gx", Integer.toString(TestClient.gx));
        p.setProperty("gy", Integer.toString(TestClient.gy));
        p.setProperty("gz", Integer.toString(TestClient.gz));
        p.setProperty("ax1", Integer.toString(TestClient.ax1));
        p.setProperty("ay1", Integer.toString(TestClient.ay1));
        p.setProperty("az1", Integer.toString(TestClient.az1));
        p.setProperty("ax2", Integer.toString(TestClient.ax2));
        p.setProperty("ay2", Integer.toString(TestClient.ay2));
        p.setProperty("az2", Integer.toString(TestClient.az2));
        return p;
    }

    // ---------- Teilen: alle Einstellungen als Text ----------

    /** Standardwerte (beim Start vor dem Laden gemerkt). */
    private static Properties defaults;
    private static final String SHARE_PREFIX = "1912U1:";
    static final String BACKUP_PROFILE = "Vor dem Import";

    /**
     * Alle Einstellungen als Text zum Kopieren. Drin steht nur, was vom Standard abweicht - so ist der Text
     * kurz, und eine neuere Version nimmt fuer alles andere (auch neue Module) ihre eigenen Standardwerte.
     */
    static String exportText() {
        save();
        Properties now = snapshot();
        Properties out = new Properties();
        for (String k : now.stringPropertyNames()) {
            String v = now.getProperty(k);
            if (defaults == null || !v.equals(defaults.getProperty(k))) out.setProperty(k, v);
        }
        if (defaults != null) {
            for (String k : defaults.stringPropertyNames()) {
                if (!now.containsKey(k)) out.setProperty(k, ""); // z.B. eine Standardtaste, die entfernt wurde
            }
        }
        out.setProperty("_format", "1");
        try {
            if (TestClient.guiKey != null) out.setProperty("_menuKey", TestClient.guiKey.saveString());
        } catch (RuntimeException ignored) {
            // ohne Menue-Taste weiter
        }
        Path song = FabricLoader.getInstance().getConfigDir().resolve(Notebot.FILE_NAME);
        try {
            if (Files.exists(song)) out.setProperty("_song", Files.readString(song, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // ohne eigenes Lied weiter
        }
        try {
            java.io.StringWriter w = new java.io.StringWriter();
            out.store(w, null);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(bytes)) {
                gz.write(w.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            return SHARE_PREFIX + java.util.Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Uebernimmt einen kopierten Text: erst alles auf Standard, dann die Werte aus dem Text. Unbekannte Eintraege
     * (z.B. von Modulen, die es nicht mehr gibt) werden uebersprungen. Vorher wird alles als Profil
     * „Vor dem Import“ gesichert. 0 = geklappt, 1 = Zwischenablage leer, 2 = kein 1912-Text, 3 = Text kaputt.
     */
    static int importText(String text) {
        if (text == null || text.isBlank()) return 1;
        String t = text.replaceAll("\\s", "");
        int at = t.indexOf(SHARE_PREFIX);
        if (at < 0) return 2;
        Properties in = new Properties();
        try {
            byte[] raw = java.util.Base64.getDecoder().decode(t.substring(at + SHARE_PREFIX.length()));
            try (java.util.zip.GZIPInputStream gz = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(raw))) {
                in.load(new java.io.StringReader(new String(gz.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
            }
        } catch (IOException | IllegalArgumentException e) {
            return 3;
        }
        saveProfile(BACKUP_PROFILE);
        Properties merged = new Properties();
        if (defaults != null) merged.putAll(defaults);
        for (String k : in.stringPropertyNames()) {
            if (!k.startsWith("_")) merged.setProperty(k, in.getProperty(k));
        }
        apply(merged);
        save();
        String menuKey = in.getProperty("_menuKey");
        if (menuKey != null && !menuKey.isBlank() && TestClient.guiKey != null) {
            try {
                TestClient.guiKey.setKey(com.mojang.blaze3d.platform.InputConstants.getKey(menuKey));
                net.minecraft.client.KeyMapping.resetMapping();
                net.minecraft.client.Minecraft.getInstance().options.save();
            } catch (RuntimeException ignored) {
                // Taste unbekannt: alte Menue-Taste behalten
            }
        }
        String song = in.getProperty("_song");
        if (song != null) {
            try {
                Files.writeString(FabricLoader.getInstance().getConfigDir().resolve(Notebot.FILE_NAME), song,
                        java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                // Lied nicht schreibbar: egal
            }
        }
        return 0;
    }

    // ---------- Profile ----------

    private static final Path PROFILES = FabricLoader.getInstance().getConfigDir().resolve("1912-profiles");

    /** Erlaubte Zeichen fuer Profilnamen (wird auch der Dateiname). */
    static String cleanProfileName(String name) {
        String n = name == null ? "" : name.strip().replaceAll("[^A-Za-z0-9 _\\-\u00e4\u00f6\u00fc\u00c4\u00d6\u00dc\u00df]", "");
        return n.length() > 24 ? n.substring(0, 24).strip() : n;
    }

    static java.util.List<String> profiles() {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (!Files.isDirectory(PROFILES)) return out;
        try (var stream = Files.list(PROFILES)) {
            stream.map(f -> f.getFileName().toString())
                    .filter(n -> n.endsWith(".properties"))
                    .map(n -> n.substring(0, n.length() - ".properties".length()))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(out::add);
        } catch (IOException e) {
            // keine Profile lesbar
        }
        return out;
    }

    /** Speichert alle aktuellen Einstellungen als Profil. Gibt eine Fehlermeldung zurueck oder null. */
    static String saveProfile(String name) {
        String n = cleanProfileName(name);
        if (n.isEmpty()) return "Bitte erst einen Namen eintragen";
        save();
        try {
            Files.createDirectories(PROFILES);
            Files.copy(FILE, PROFILES.resolve(n + ".properties"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return null;
        } catch (IOException e) {
            return "Speichern fehlgeschlagen";
        }
    }

    /** Laedt ein Profil: ersetzt alle Einstellungen. */
    static String loadProfile(String name) {
        Path f = PROFILES.resolve(cleanProfileName(name) + ".properties");
        if (!Files.exists(f)) return "Profil nicht gefunden";
        try {
            Files.copy(f, FILE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            return "Laden fehlgeschlagen";
        }
        load();
        return null;
    }

    static void deleteProfile(String name) {
        try {
            Files.deleteIfExists(PROFILES.resolve(cleanProfileName(name) + ".properties"));
        } catch (IOException e) {
            // egal
        }
    }

    /** Diese Schalter sollen nach einem Neustart immer aus sein. */
    private static final java.util.Set<String> NOT_SAVED = java.util.Set.of("chestSwap", "fakePlayer", "bookBot", "freecam", "freeLook",
            "notebot");

    /** Speichert alle oeffentlichen, veraenderbaren boolean/int/String-Felder einer Klasse. */
    private static void saveStatics(Properties p, Class<?> c, String prefix) {
        for (java.lang.reflect.Field f : c.getFields()) {
            int mod = f.getModifiers();
            if (!java.lang.reflect.Modifier.isStatic(mod) || java.lang.reflect.Modifier.isFinal(mod)) continue;
            if (NOT_SAVED.contains(f.getName())) continue;
            try {
                Object v = f.get(null);
                if (v instanceof Boolean || v instanceof Integer || v instanceof String) {
                    p.setProperty(prefix + f.getName(), String.valueOf(v));
                }
            } catch (IllegalAccessException ignored) {
                // oeffentliche Felder sind immer lesbar
            }
        }
    }

    private static void loadStatics(Properties p, Class<?> c, String prefix) {
        for (java.lang.reflect.Field f : c.getFields()) {
            int mod = f.getModifiers();
            if (!java.lang.reflect.Modifier.isStatic(mod) || java.lang.reflect.Modifier.isFinal(mod)) continue;
            if (NOT_SAVED.contains(f.getName())) continue;
            String v = p.getProperty(prefix + f.getName());
            if (v == null) continue;
            try {
                if (f.getType() == boolean.class) {
                    f.setBoolean(null, Boolean.parseBoolean(v.trim()));
                } else if (f.getType() == int.class) {
                    f.setInt(null, Integer.parseInt(v.trim()));
                } else if (f.getType() == String.class) {
                    f.set(null, v);
                }
            } catch (IllegalAccessException | NumberFormatException ignored) {
                // ungueltiger Wert: Standard behalten
            }
        }
    }

    /** Liest kommagetrennte IDs; unbekannte IDs (z.B. aus neueren Versionen) werden uebersprungen. */
    private static <T> void readIds(String value, net.minecraft.core.Registry<T> registry, java.util.Set<T> into) {
        if (value == null) return;
        into.clear();
        for (String part : value.split(",")) {
            Identifier id = Identifier.tryParse(part.trim());
            if (id != null && registry.containsKey(id)) into.add(registry.getValue(id));
        }
    }

    private static <T> String writeIds(java.util.Set<T> values, net.minecraft.core.Registry<T> registry) {
        StringJoiner j = new StringJoiner(",");
        for (T v : values) {
            Identifier id = registry.getKey(v);
            if (id != null) j.add(id.toString());
        }
        return j.toString();
    }

    /** Farbe 0xRRGGBB oder -1 (Standard); alles andere wird zu -1. */
    private static int color(int v) {
        return v >= 0 && v <= 0xFFFFFF ? v : -1;
    }

    private static boolean bool(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        return v == null ? def : Boolean.parseBoolean(v.trim());
    }

    private static int num(Properties p, String key, int def) {
        try {
            String v = p.getProperty(key);
            return v == null ? def : Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** "Name1;Name2" -> Set (Namen der Module, wie im Menue auf Deutsch). */
    private static void readNames(String raw, java.util.Set<String> into) {
        into.clear();
        if (raw == null) return;
        for (String n : raw.split(";")) {
            if (!n.isBlank()) into.add(Keybinds.current(n.trim()));
        }
    }
}
