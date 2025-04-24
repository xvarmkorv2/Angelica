package com.gtnewhorizons.angelica.mixins.early.shaders;

import net.coderbot.iris.Iris;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinMinecraft_PipelineManagement {
    @Inject(method = "travelToDimension(I)V", at = @At(value = "HEAD"))
    private void iris$resetPipeline(CallbackInfo ci) {
        if (Iris.getCurrentDimension() != Iris.lastDimension) {
            Iris.logger.info("Reloading pipeline on dimension change: " + Iris.lastDimension + " => "
                    + Iris.getCurrentDimension());
            Iris.lastDimension = Iris.getCurrentDimension();
            // Destroy pipelines when changing dimensions.
            Iris.getPipelineManager().destroyPipeline();

            // NB: We need create the pipeline immediately, so that it is ready by the time
            // that Sodium starts trying to
            // initialize its world renderer.
            if (Minecraft.getMinecraft().theWorld != null) {
                Iris.getPipelineManager().preparePipeline(Iris.getCurrentDimension());
            }
        }
    }
}
