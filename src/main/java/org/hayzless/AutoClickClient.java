package org.hayzless;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.hayzless.config.AutoClickConfig;
import org.hayzless.module.ModuleManager;
import org.hayzless.module.impl.AutoClickerModule;
import org.lwjgl.glfw.GLFW;

public final class AutoClickClient implements ClientModInitializer {
	public static final KeyBinding.Category KEY_CATEGORY = KeyBinding.Category.create(AutoClick.id("main"));

	private static AutoClickConfig config = new AutoClickConfig();
	private static ModuleManager modules = new ModuleManager();
	private static KeyBinding toggleKey;

	@Override
	public void onInitializeClient() {
		config = AutoClickConfig.load();

		modules = new ModuleManager();
		modules.register(AutoClickerModule.instance()).setEnabled(config.enabled);

		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.auto-click.toggle",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_UNKNOWN,
				KEY_CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.wasPressed()) {
				config.enabled = !config.enabled;
				config.save();
			}
		});

		AutoClick.LOGGER.info("Auto Click ready ({} CPS by default)", config.cpsMin + "-" + config.cpsMax);
	}

	public static AutoClickConfig config() {
		return config;
	}

	public static ModuleManager modules() {
		return modules;
	}
}
