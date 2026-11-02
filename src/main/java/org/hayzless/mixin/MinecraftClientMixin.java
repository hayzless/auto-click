package org.hayzless.mixin;

import net.minecraft.client.MinecraftClient;
import org.hayzless.AutoClickClient;
import org.hayzless.module.ModuleManager;
import org.hayzless.module.impl.AutoClickerModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void autoClick$startTick(CallbackInfo info) {
		ModuleManager modules = AutoClickClient.modules();
		if (modules != null) {
			modules.onStartTick((MinecraftClient) (Object) this);
		}
	}

	/**
	 * Frames are the only clock faster than the 20 Hz tick, so injected clicks are dispatched here
	 * rather than all landing on the tick boundary.
	 */
	@Inject(method = "render", at = @At("TAIL"))
	private void autoClick$renderFrame(boolean tick, CallbackInfo info) {
		ModuleManager modules = AutoClickClient.modules();
		if (modules != null) {
			modules.onFrame((MinecraftClient) (Object) this);
		}
	}

	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	private void autoClick$gateAttack(CallbackInfoReturnable<Boolean> info) {
		if (!AutoClickerModule.allowVanillaAttack()) {
			info.setReturnValue(false);
		}
	}

	@Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
	private void autoClick$gateUse(CallbackInfo info) {
		if (!AutoClickerModule.allowVanillaUse()) {
			info.cancel();
		}
	}
}
