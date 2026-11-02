package org.hayzless.module;

import net.minecraft.client.MinecraftClient;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Registry that fans the client's tick and frame hooks out to every enabled module. */
public final class ModuleManager {
	private final Map<String, Module> modules = new LinkedHashMap<>();

	public <T extends Module> T register(T module) {
		modules.put(module.id(), module);
		return module;
	}

	public Collection<Module> all() {
		return modules.values();
	}

	public Module byId(String id) {
		return modules.get(id);
	}

	public void onStartTick(MinecraftClient client) {
		for (Module module : modules.values()) {
			if (module.isEnabled()) {
				module.onStartTick(client);
			}
		}
	}

	public void onFrame(MinecraftClient client) {
		for (Module module : modules.values()) {
			if (module.isEnabled()) {
				module.onFrame(client);
			}
		}
	}
}
