package org.hayzless;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AutoClick {
	public static final String MOD_ID = "auto-click";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private AutoClick() {
	}

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}
}
