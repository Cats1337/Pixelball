package io.github.cats1337.pixelball.mixin;

import io.github.cats1337.pixelball.PixelballBypass;
import io.github.cats1337.pixelball.PixelballConfig;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    @Inject(
            method = "teleportTo",
            at = @At("HEAD"),
            cancellable = true
    )
    private void Pixelball$BlockDisabledDimensions(
            TeleportTarget teleportTarget,
            CallbackInfoReturnable<Entity> cir
    ) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        var destinationKey = teleportTarget.world().getRegistryKey();

        if (destinationKey == World.NETHER
                && PixelballConfig.isDimensionDisabled("nether")
                && !PixelballBypass.canBypassNether(player)) {
            player.sendMessage(Text.literal("§cThe Nether is currently disabled."), true);
            cir.setReturnValue(player);
            return;
        }

        if (destinationKey == World.END
                && PixelballConfig.isDimensionDisabled("end")
                && !PixelballBypass.canBypassEnd(player)) {
            player.sendMessage(Text.literal("§cThe End is currently disabled."), true);
            cir.setReturnValue(player);
        }
    }
}