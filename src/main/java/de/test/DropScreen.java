package de.test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Items waehlen, die automatisch aus dem Inventar gedroppt werden. */
public class DropScreen extends Screen {
    private static final int ROWS = 5;

    private final Screen parent;
    private final List<Item> items = new ArrayList<>();
    private final List<Ui.Btn> btns = new ArrayList<>();
    private int page = 0;
    private int px, py, pw, ph;

    public DropScreen(Screen parent) {
        super(Component.literal("AutoDrop"));
        this.parent = parent;
    }

    private static String nameOf(Item item) {
        return Component.translatable(item.getDescriptionId()).getString();
    }

    /** Alle Items aus dem Inventar plus alle bereits gewaehlten. */
    private void buildList() {
        Set<Item> all = new LinkedHashSet<>();
        if (this.minecraft != null && this.minecraft.player != null) {
            var inv = this.minecraft.player.getInventory();
            for (int i = 0; i < 41; i++) {
                ItemStack st = inv.getItem(i);
                if (!st.isEmpty()) all.add(st.getItem());
            }
        }
        all.addAll(TestClient.dropItems);
        items.clear();
        items.addAll(all);
        items.sort(Comparator.comparing(DropScreen::nameOf, String.CASE_INSENSITIVE_ORDER));
    }

    private static Component enabledLabel() {
        return Component.literal("AutoDrop: " + (TestClient.autoDrop ? "AN" : "AUS"));
    }

    @Override
    protected void init() {
        btns.clear();
        buildList();
        int pages = Math.max(1, (items.size() + ROWS - 1) / ROWS);
        if (page >= pages) page = pages - 1;
        if (page < 0) page = 0;

        int x = this.width / 2 - 100;
        int y = this.height / 2 - 96;
        px = x - 12;
        py = y - 34;
        pw = 224;
        ph = 34 + 192 + 12;

        Button toggle = addWidget(Button.builder(enabledLabel(), b -> {
            TestClient.autoDrop = !TestClient.autoDrop;
            TestConfig.save();
            b.setMessage(enabledLabel());
        }).bounds(x, y, 200, 20).build());
        btns.add(new Ui.Btn(toggle, () -> TestClient.autoDrop ? Ui.STYLE_ON : Ui.STYLE_NORMAL));

        if (items.isEmpty()) {
            Button none = addWidget(Button.builder(Component.literal("Keine Items im Inventar"), b -> { })
                    .bounds(x, y + 24, 200, 20).build());
            none.active = false;
            btns.add(new Ui.Btn(none, () -> Ui.STYLE_NORMAL));
        }
        for (int r = 0; r < ROWS; r++) {
            int idx = page * ROWS + r;
            if (idx >= items.size()) break;
            Item item = items.get(idx);
            Button row = addWidget(Button.builder(Component.literal(nameOf(item)), b -> {
                if (!TestClient.dropItems.remove(item)) {
                    TestClient.dropItems.add(item);
                }
                TestConfig.save();
            }).bounds(x, y + 24 + r * 24, 200, 20).build());
            btns.add(new Ui.Btn(row, () -> TestClient.dropItems.contains(item) ? Ui.STYLE_ON : Ui.STYLE_NORMAL));
        }

        int navY = y + 24 + ROWS * 24;
        Button prev = addWidget(Button.builder(Component.literal("<"), b -> {
            page--;
            this.rebuildWidgets();
        }).bounds(x, navY, 40, 20).build());
        prev.active = page > 0;
        btns.add(new Ui.Btn(prev, () -> Ui.STYLE_NORMAL));

        Button label = addWidget(Button.builder(Component.literal("Seite " + (page + 1) + "/" + pages), b -> { })
                .bounds(x + 44, navY, 112, 20).build());
        label.active = false;
        btns.add(new Ui.Btn(label, () -> Ui.STYLE_NORMAL));

        Button next = addWidget(Button.builder(Component.literal(">"), b -> {
            page++;
            this.rebuildWidgets();
        }).bounds(x + 160, navY, 40, 20).build());
        next.active = page < pages - 1;
        btns.add(new Ui.Btn(next, () -> Ui.STYLE_NORMAL));

        Button back = addWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(x, navY + 28, 200, 20).build());
        btns.add(new Ui.Btn(back, () -> Ui.STYLE_PRIMARY));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        Ui.panel(g, this.font, px, py, pw, ph, this.title.getString(), new ArrayList<>());
        for (Ui.Btn b : btns) {
            Ui.button(g, this.font, b.button, mouseX, mouseY, b.style.getAsInt());
        }
    }

    @Override
    public void onClose() {
        TestConfig.save();
        this.minecraft.gui.setScreen(parent);
    }
}
