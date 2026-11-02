package org.hayzless.module.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.EntityHitResult;
import org.hayzless.AutoClickClient;
import org.hayzless.config.AutoClickConfig;
import org.hayzless.input.ClickScheduler;
import org.hayzless.mixin.MinecraftClientAccessor;
import org.hayzless.module.Module;

import java.util.Random;

/**
 * Holds a mouse button and clicks at the configured rate.
 *
 * <p>Vanilla {@code handleInputEvents} only attacks on press events, so holding a button produces no
 * clicks at all and the module has to perform every one of them. Clicks are only ever sent to the
 * entity the tick was locked to: Grim flags interactions with <em>different</em> entities inside one
 * tick, never repeated hits on one entity. The left button is left completely alone unless the
 * crosshair is on an entity, so mining behaves exactly like vanilla.</p>
 */
public final class AutoClickerModule extends Module {
	private static final AutoClickerModule INSTANCE = new AutoClickerModule();

	private final ClickScheduler leftScheduler = new ClickScheduler(System.nanoTime());
	private final ClickScheduler rightScheduler = new ClickScheduler(System.nanoTime() * 31L);
	private final Random random = new Random();

	private boolean leftHeld;
	private boolean rightHeld;
	private int lockedEntityId = -1;
	private long tickStartNanos;
	private double leftTargetCps;
	private double rightTargetCps;
	/** Set while the module itself drives vanilla's click methods, so the gate stays out of the way. */
	private boolean injecting;

	private AutoClickerModule() {
		super("auto-clicker", "Auto Clicker", "Clicks for you while a mouse button is held.");
	}

	public static AutoClickerModule instance() {
		return INSTANCE;
	}

	/** Called from {@code MinecraftClient.doAttack()}; {@code false} drops vanilla's click. */
	public static boolean allowVanillaAttack() {
		return INSTANCE.gateAttack();
	}

	/** Called from {@code MinecraftClient.doItemUse()}; {@code false} drops vanilla's click. */
	public static boolean allowVanillaUse() {
		return INSTANCE.gateUse();
	}

	@Override
	public void onStartTick(MinecraftClient client) {
		AutoClickConfig config = AutoClickClient.config();
		if (isEnabled() != config.enabled) {
			setEnabled(config.enabled);
		}

		tickStartNanos = System.nanoTime();

		boolean usable = isEnabled()
				&& client.player != null
				&& client.world != null
				&& client.currentScreen == null
				&& !client.isPaused();

		leftHeld = usable
				&& config.leftClick
				&& client.options.attackKey.isPressed()
				&& !client.player.isUsingItem()
				&& entityId(client) != -1;
		rightHeld = usable && config.rightClick && client.options.useKey.isPressed();
		lockedEntityId = leftHeld ? entityId(client) : -1;

		if (leftHeld) {
			leftTargetCps = config.randomTargetCps(random);
			leftScheduler.beginTick(leftTargetCps, config.clicksPerTick);
		} else {
			leftScheduler.release();
		}

		if (rightHeld) {
			rightTargetCps = config.randomTargetCps(random);
			rightScheduler.beginTick(rightTargetCps, config.clicksPerTick);
		} else {
			rightScheduler.release();
		}
	}

	@Override
	public void onFrame(MinecraftClient client) {
		if (!leftHeld && !rightHeld) {
			return;
		}

		long sinceTickStart = System.nanoTime() - tickStartNanos;
		if (sinceTickStart < 0L || sinceTickStart >= ClickScheduler.TICK_NANOS) {
			return;
		}

		AutoClickConfig config = AutoClickClient.config();
		MinecraftClientAccessor accessor = (MinecraftClientAccessor) client;

		// One target per tick only: switching entities mid-tick is the one thing Grim reports.
		if (leftHeld && entityId(client) == lockedEntityId) {
			while (leftScheduler.extraClickDue(sinceTickStart)) {
				injecting = true;
				try {
					accessor.autoClick$invokeDoAttack();
				} finally {
					injecting = false;
				}
			}
		}

		if (rightHeld) {
			// doItemUse() does not check the cooldown itself, only vanilla's input handler does, so the
			// option to stay inside vanilla's 5 uses per second has to be honoured here.
			while (!config.respectUseCooldown || accessor.autoClick$getItemUseCooldown() == 0) {
				if (!rightScheduler.extraClickDue(sinceTickStart)) {
					break;
				}

				injecting = true;
				try {
					accessor.autoClick$invokeDoItemUse();
				} finally {
					injecting = false;
				}
			}
		}
	}

	@Override
	public void onDisable() {
		leftHeld = false;
		rightHeld = false;
		lockedEntityId = -1;
		leftScheduler.release();
		rightScheduler.release();
	}

	private boolean gateAttack() {
		if (injecting || !leftHeld) {
			return true;
		}

		return leftScheduler.takeVanillaClick();
	}

	private boolean gateUse() {
		if (injecting || !rightHeld) {
			return true;
		}

		return rightScheduler.takeVanillaClick();
	}

	private static int entityId(MinecraftClient client) {
		return client.crosshairTarget instanceof EntityHitResult hit ? hit.getEntity().getId() : -1;
	}
}
