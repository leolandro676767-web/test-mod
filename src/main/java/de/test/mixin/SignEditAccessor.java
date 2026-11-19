package de.test.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Auto Sign: an die vier Zeilen eines Schilds kommen, die beim Schliessen an den Server gehen. */
@Mixin(AbstractSignEditScreen.class)
public interface SignEditAccessor {
    @Accessor("messages")
    String[] test$messages();
}
