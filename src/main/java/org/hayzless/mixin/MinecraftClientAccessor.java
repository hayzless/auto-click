package org.hayzless.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Gives the module the same entry points vanilla input uses, instead of reimplementing them. */
@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
	@Invoker("doAttack")
	boolean autoClick$invokeDoAttack();

	@Invoker("doItemUse")
	void autoClick$invokeDoItemUse();

	@Accessor("itemUseCooldown")
	int autoClick$getItemUseCooldown();

	@Accessor("itemUseCooldown")
	void autoClick$setItemUseCooldown(int ticks);
}
