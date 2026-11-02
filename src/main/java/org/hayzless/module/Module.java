package org.hayzless.module;

import net.minecraft.client.MinecraftClient;

/** A single toggleable client feature. */
public abstract class Module {
	private final String id;
	private final String name;
	private final String description;
	private boolean enabled;

	protected Module(String id, String name, String description) {
		this.id = id;
		this.name = name;
		this.description = description;
	}

	public final String id() {
		return id;
	}

	public final String name() {
		return name;
	}

	public final String description() {
		return description;
	}

	public final boolean isEnabled() {
		return enabled;
	}

	public final void setEnabled(boolean value) {
		if (enabled == value) {
			return;
		}

		enabled = value;

		if (value) {
			onEnable();
		} else {
			onDisable();
		}
	}

	public final void toggle() {
		setEnabled(!enabled);
	}

	public void onEnable() {
	}

	public void onDisable() {
	}

	/** Runs at the head of the client tick, before vanilla reads held input. */
	public void onStartTick(MinecraftClient client) {
	}

	/** Runs once per rendered frame, which is where sub-tick work belongs. */
	public void onFrame(MinecraftClient client) {
	}
}
