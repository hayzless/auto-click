package org.hayzless.screen;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.hayzless.AutoClickClient;
import org.hayzless.config.AutoClickConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Config screen reached from ModMenu. Built from vanilla widgets only, so ModMenu is the single
 * optional dependency and no extra config library has to be installed.
 */
public final class AutoClickConfigScreen extends Screen {
	private static final int PANEL_WIDTH = 340;
	private static final int PANEL_HEIGHT = 306;
	private static final int CONTENT_WIDTH = 300;
	private static final int ROW_HEIGHT = 26;
	private static final int ROW_TOP = 44;

	private static final int PANEL_BACKGROUND = 0xF0141418;
	private static final int PANEL_BORDER = 0xFF2E2E38;
	private static final int TEXT_LABEL = 0xFF9A9AA6;
	private static final int TEXT_VALUE = 0xFFF2F2F5;
	private static final int TEXT_ACCENT = 0xFF6CE0B0;
	private static final int TEXT_HINT = 0xFF6E6E7A;

	private final Screen parent;
	private final List<RowText> rows = new ArrayList<>();

	private int panelLeft;
	private int panelTop;

	public AutoClickConfigScreen(Screen parent) {
		super(Text.literal("Auto Click"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		rows.clear();

		AutoClickConfig config = AutoClickClient.config();
		panelLeft = (width - PANEL_WIDTH) / 2;
		panelTop = (height - PANEL_HEIGHT) / 2;

		int x = panelLeft + 20;
		int half = (CONTENT_WIDTH - 10) / 2;

		addDrawableChild(toggle(x, y(0), CONTENT_WIDTH, "Auto Clicker", config.enabled,
				() -> config.enabled = !config.enabled));

		addDrawableChild(toggle(x, y(1), half, "Left click", config.leftClick,
				() -> config.leftClick = !config.leftClick));
		addDrawableChild(toggle(x + half + 10, y(1), half, "Right click", config.rightClick,
				() -> config.rightClick = !config.rightClick));

		addStepper(x, y(2), "Min CPS", config.cpsMin, AutoClickConfig.MIN_CPS, config.cpsMax,
				value -> config.cpsMin = value);
		addStepper(x, y(3), "Max CPS", config.cpsMax, config.cpsMin, AutoClickConfig.MAX_CPS,
				value -> config.cpsMax = value);
		addStepper(x, y(4), "Max clicks / tick", config.clicksPerTick,
				AutoClickConfig.MIN_CLICKS_PER_TICK, AutoClickConfig.MAX_CLICKS_PER_TICK,
				value -> config.clicksPerTick = value);

		addDrawableChild(toggle(x, y(5), CONTENT_WIDTH, "Vanilla use cooldown (5/s)", config.respectUseCooldown,
				() -> config.respectUseCooldown = !config.respectUseCooldown));

		addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close())
				.dimensions(x, y(7), CONTENT_WIDTH, 20)
				.build());
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		context.fill(panelLeft - 1, panelTop - 1, panelLeft + PANEL_WIDTH + 1, panelTop + PANEL_HEIGHT + 1, PANEL_BORDER);
		context.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, PANEL_BACKGROUND);

		super.render(context, mouseX, mouseY, delta);

		context.drawCenteredTextWithShadow(textRenderer, title, width / 2, panelTop + 16, TEXT_VALUE);

		for (RowText row : rows) {
			context.drawTextWithShadow(textRenderer, Text.literal(row.label()), row.x() + 4, row.y() + 6, TEXT_LABEL);
			context.drawCenteredTextWithShadow(textRenderer, Text.literal(row.value()), row.x() + 248, row.y() + 6, TEXT_VALUE);
		}

		AutoClickConfig config = AutoClickClient.config();
		Text summary = Text.literal(config.cpsMin + "-" + config.cpsMax + " CPS  ·  ceiling " + config.ceilingCps() + " CPS");
		context.drawCenteredTextWithShadow(textRenderer, summary, width / 2, panelTop + 250, TEXT_ACCENT);

		Text hint = Text.literal("Hold left or right click in game");
		context.drawCenteredTextWithShadow(textRenderer, hint, width / 2, panelTop + 266, TEXT_HINT);
	}

	@Override
	public void close() {
		AutoClickClient.config().save();

		if (client != null) {
			client.setScreen(parent);
		}
	}

	private int y(int row) {
		return panelTop + ROW_TOP + row * ROW_HEIGHT;
	}

	private ButtonWidget toggle(int x, int y, int width, String label, boolean state, Runnable action) {
		return ButtonWidget.builder(toggleLabel(label, state), button -> {
			action.run();
			commit();
		}).dimensions(x, y, width, 20).build();
	}

	private void addStepper(int x, int y, String label, int value, int min, int max, IntConsumer setter) {
		addDrawableChild(ButtonWidget.builder(Text.literal("-"), button -> {
			setter.accept(Math.max(min, value - 1));
			commit();
		}).dimensions(x + 196, y, 24, 20).build());

		addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> {
			setter.accept(Math.min(max, value + 1));
			commit();
		}).dimensions(x + 276, y, 24, 20).build());

		rows.add(new RowText(label, Integer.toString(value), x, y));
	}

	private void commit() {
		AutoClickClient.config().save();
		clearAndInit();
	}

	private static Text toggleLabel(String label, boolean state) {
		return Text.literal(label + ": " + (state ? "ON" : "OFF"));
	}

	private record RowText(String label, String value, int x, int y) {
	}
}
