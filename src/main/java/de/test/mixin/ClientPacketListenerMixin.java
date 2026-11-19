package de.test.mixin;

import de.test.Safe;
import de.test.BossStack;
import de.test.MiscMods;
import de.test.ChunkScanHooks;
import de.test.PlayerMods;
import de.test.RenderMods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Rotate: Blickrichtung vor einem Server-Teleport merken und danach wiederherstellen. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Unique
    private float test$yaw;
    @Unique
    private float test$pitch;

    @Unique
    private void test$save() {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        test$yaw = p.getYRot();
        test$pitch = p.getXRot();
    }

    @Unique
    private void test$restore() {
        var p = Minecraft.getInstance().player;
        if (p == null || !PlayerMods.noRotate) return;
        if (PlayerMods.noRotateYaw) {
            p.setYRot(test$yaw);
            p.yRotO = test$yaw;
        }
        if (PlayerMods.noRotatePitch) {
            p.setXRot(test$pitch);
            p.xRotO = test$pitch;
        }
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), require = 0)
    private void test$moveHead(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        test$save();
    }

    @Inject(method = "handleMovePlayer", at = @At("TAIL"), require = 0)
    private void test$moveTail(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        test$restore();
    }

    @Inject(method = "handleRotatePlayer", at = @At("HEAD"), require = 0)
    private void test$rotHead(ClientboundPlayerRotationPacket packet, CallbackInfo ci) {
        test$save();
    }

    @Inject(method = "handleRotatePlayer", at = @At("TAIL"), require = 0)
    private void test$rotTail(ClientboundPlayerRotationPacket packet, CallbackInfo ci) {
        test$restore();
    }

    // Sound Blocker: gewaehlte Geraeusche vom Server gar nicht erst abspielen
    @Inject(method = "handleSoundEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void test$sound(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (Safe.test("Sound Blocker", () -> MiscMods.blockSound(packet))) ci.cancel();
    }

    @Inject(method = "handleSoundEntityEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void test$soundEntity(ClientboundSoundEntityPacket packet, CallbackInfo ci) {
        if (Safe.test("Sound Blocker", () -> MiscMods.blockSound(packet))) ci.cancel();
    }

    // Boss Stack: Bossleisten selbst verwalten und zusammengefasst zeichnen
    @Inject(method = "handleBossUpdate", at = @At("HEAD"), cancellable = true, require = 0)
    private void test$boss(ClientboundBossEventPacket packet, CallbackInfo ci) {
        if (Safe.test("Boss Stack", () -> BossStack.onPacket(packet))) ci.cancel();
    }

    // Totem ausgeloest (Ereignis 35): Minecraft behandelt das hier selbst und gibt es nie an
    // LivingEntity.handleEntityEvent weiter - deshalb hier abfangen (TAIL: nur im Haupt-Thread)
    @Inject(method = "handleEntityEvent", at = @At("TAIL"), require = 0)
    private void test$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        if (packet.getEventId() != 35) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.isSameThread()) return;
        Entity e = packet.getEntity(mc.level);
        if (e instanceof LivingEntity le) Safe.run("Totem", () -> RenderMods.onTotemPop(le));
    }

    // ESP: ein Chunk ist (neu) angekommen - die Block-Suchen nehmen ihn sofort dran
    @Inject(method = "updateLevelChunk", at = @At("TAIL"), require = 0)
    private void test$chunkLoaded(int x, int z, ClientboundLevelChunkPacketData data, CallbackInfo ci) {
        Safe.run("ESP", () -> ChunkScanHooks.chunkChanged(x, z));
    }
}
