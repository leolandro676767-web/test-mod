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
        if (!Files.exists(FILE)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
        } catch (IOException e) {
            System.err.println("[1912] Konnte Einstellungen nicht laden: " + e);
            return;
        }
        TestClient.sprint = bool(p, "sprint", TestClient.sprint);
        TestClient.sprintJump = bool(p, "sprintJump", TestClient.sprintJump);
        TestClient.autoMine = bool(p, "autoMine", TestClient.autoMine);
        TestClient.autoLogout = bool(p, "autoLogout", TestClient.autoLogout);
        TestClient.autoDrop = bool(p, "autoDrop", TestClient.autoDrop);
        TestClient.autoClicker = bool(p, "autoClicker", TestClient.autoClicker);
        TestClient.clickerRandom = Math.max(0, Math.min(100, num(p, "clickerRandom", TestClient.clickerRandom)));
        TestClient.clickerHold = bool(p, "clickerHold", TestClient.clickerHold);
        TestClient.clickerEarly = bool(p, "clickerEarly", TestClient.clickerEarly);
        Theme.scheme = num(p, "themeScheme", Theme.scheme);
        Theme.headHex = num(p, "themeHead", Theme.headHex);
        Theme.accentHex = num(p, "themeAccent", Theme.accentHex);
        Theme.showLogo = bool(p, "themeLogo", Theme.showLogo);
        Theme.animations = bool(p, "themeAnim", Theme.animations);
        Theme.logoSize = num(p, "themeLogoSize", Theme.logoSize);
        Theme.colWidth = num(p, "themeColWidth", Theme.colWidth);
        Theme.clamp();
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
        Properties p = new Properties();
        p.setProperty("sprint", Boolean.toString(TestClient.sprint));
        p.setProperty("sprintJump", Boolean.toString(TestClient.sprintJump));
        p.setProperty("autoMine", Boolean.toString(TestClient.autoMine));
        p.setProperty("autoLogout", Boolean.toString(TestClient.autoLogout));
        p.setProperty("autoDrop", Boolean.toString(TestClient.autoDrop));
        p.setProperty("autoClicker", Boolean.toString(TestClient.autoClicker));
        p.setProperty("clickerRandom", Integer.toString(TestClient.clickerRandom));
        p.setProperty("clickerHold", Boolean.toString(TestClient.clickerHold));
        p.setProperty("clickerEarly", Boolean.toString(TestClient.clickerEarly));
        p.setProperty("themeScheme", Integer.toString(Theme.scheme));
        p.setProperty("themeHead", Integer.toString(Theme.headHex));
        p.setProperty("themeAccent", Integer.toString(Theme.accentHex));
        p.setProperty("themeLogo", Boolean.toString(Theme.showLogo));
        p.setProperty("themeAnim", Boolean.toString(Theme.animations));
        p.setProperty("themeLogoSize", Integer.toString(Theme.logoSize));
        p.setProperty("themeColWidth", Integer.toString(Theme.colWidth));
        StringJoiner drops = new StringJoiner(",");
        for (Item it : TestClient.dropItems) {
            drops.add(BuiltInRegistries.ITEM.getKey(it).toString());
        }
        p.setProperty("dropItems", drops.toString());
        if (TestClient.selectedBlock != null) {
            p.setProperty("block", BuiltInRegistries.BLOCK.getKey(TestClient.selectedBlock).toString());
        }
        p.setProperty("gx", Integer.toString(TestClient.gx));
        p.setProperty("gy", Integer.toString(TestClient.gy));
        p.setProperty("gz", Integer.toString(TestClient.gz));
        p.setProperty("ax1", Integer.toString(TestClient.ax1));
        p.setProperty("ay1", Integer.toString(TestClient.ay1));
        p.setProperty("az1", Integer.toString(TestClient.az1));
        p.setProperty("ax2", Integer.toString(TestClient.ax2));
        p.setProperty("ay2", Integer.toString(TestClient.ay2));
        p.setProperty("az2", Integer.toString(TestClient.az2));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "1912 Client Einstellungen");
            }
        } catch (IOException e) {
            System.err.println("[1912] Konnte Einstellungen nicht speichern: " + e);
        }
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
}
