package de.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

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
            System.err.println("[Test] Konnte Einstellungen nicht laden: " + e);
            return;
        }
        TestClient.sprint = bool(p, "sprint", TestClient.sprint);
        TestClient.sprintJump = bool(p, "sprintJump", TestClient.sprintJump);
        TestClient.autoMine = bool(p, "autoMine", TestClient.autoMine);
        TestClient.autoLogout = bool(p, "autoLogout", TestClient.autoLogout);
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
        p.setProperty("ax1", Integer.toString(TestClient.ax1));
        p.setProperty("ay1", Integer.toString(TestClient.ay1));
        p.setProperty("az1", Integer.toString(TestClient.az1));
        p.setProperty("ax2", Integer.toString(TestClient.ax2));
        p.setProperty("ay2", Integer.toString(TestClient.ay2));
        p.setProperty("az2", Integer.toString(TestClient.az2));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream out = Files.newOutputStream(FILE)) {
                p.store(out, "Test Mod Einstellungen");
            }
        } catch (IOException e) {
            System.err.println("[Test] Konnte Einstellungen nicht speichern: " + e);
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
