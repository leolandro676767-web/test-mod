package de.test;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.level.block.Block;

/** Block-Modus: jeden Block des Spiels aus der Liste waehlen, der dann abgebaut wird. */
public class BlockPickScreen extends PickerScreen {
    public BlockPickScreen(Screen parent) {
        super(parent, "Block w\u00e4hlen", Kind.BLOCK, single(), null, null);
    }

    /** Einzelauswahl: ein neuer Block ersetzt den alten. */
    private static Selection single() {
        return new Selection() {
            @Override
            public boolean contains(Object value) {
                return value == TestClient.selectedBlock;
            }

            @Override
            public void add(Object value) {
                TestClient.selectedBlock = (Block) value;
            }

            @Override
            public void remove(Object value) {
                if (value == TestClient.selectedBlock) TestClient.selectedBlock = null;
            }

            @Override
            public List<Object> values() {
                List<Object> out = new ArrayList<>();
                if (TestClient.selectedBlock != null) out.add(TestClient.selectedBlock);
                return out;
            }
        };
    }
}
