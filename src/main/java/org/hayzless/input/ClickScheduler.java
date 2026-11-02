package org.hayzless.input;

import java.util.Random;

/**
 * Decides when clicks happen for a single mouse button.
 *
 * <p>The vanilla client only attacks on discrete press events ({@code attackKey.wasPressed()}), so a
 * held button produces no attacks of its own. This class hands out a per-tick <em>allowance</em> of
 * clicks; a tap that already produced a vanilla click consumes part of it, and the remainder is
 * injected on a nanosecond clock so clicks are spread across the tick instead of landing on the
 * tick boundary.</p>
 *
 * <p>Deliberately free of Minecraft types so the timing model can be exercised by a plain unit
 * test.</p>
 */
public final class ClickScheduler {
	public static final double TICKS_PER_SECOND = 20.0;
	public static final long TICK_NANOS = 50_000_000L;

	/** How far a single interval may stray from the target, as a fraction of it. */
	private static final double JITTER = 0.15;
	/** Never schedule two injected clicks closer together than this. */
	private static final long MIN_EXTRA_GAP_NANOS = 2_000_000L;

	private final Random random;

	/** Clicks carried over from previous ticks, so a fractional rate stays exact over time. */
	private double credit;
	private int allowance;
	private int clicksThisTick;

	private long extraGapNanos;
	private long nextExtraAtNanos;
	private boolean extrasArmed;

	public ClickScheduler(long seed) {
		this.random = new Random(seed);
	}

	/**
	 * Starts a tick and computes how many clicks this tick is allowed to carry.
	 *
	 * @param targetCps  clicks per second to aim for this tick
	 * @param maxPerTick hard ceiling on clicks in a single tick
	 */
	public void beginTick(double targetCps, int maxPerTick) {
		clicksThisTick = 0;

		double perTick = Math.max(0.0, targetCps) / TICKS_PER_SECOND;
		credit += perTick * jitter();
		allowance = Math.min(Math.max(0, (int) Math.floor(credit)), maxPerTick);
		// Clamp what is left over rather than what is owed, so jitter does not bias the average down.
		credit = Math.min(credit - allowance, maxPerTick);

		if (allowance > 0) {
			// Any click vanilla does not perform itself is spread out over the tick.
			extraGapNanos = Math.max(TICK_NANOS / allowance, MIN_EXTRA_GAP_NANOS);
			nextExtraAtNanos = 0L;
			extrasArmed = true;
		} else {
			extrasArmed = false;
		}
	}

	/**
	 * Admits or drops the click the vanilla client is about to perform.
	 *
	 * @return {@code true} if this tick's allowance can pay for it
	 */
	public boolean takeVanillaClick() {
		if (clicksThisTick >= allowance) {
			return false;
		}

		clicksThisTick++;
		return true;
	}

	/**
	 * Reports whether an injected click is due.
	 *
	 * @param nanosSinceTickStart time elapsed in the current tick
	 * @return {@code true} once per injection this tick is willing to spend
	 */
	public boolean extraClickDue(long nanosSinceTickStart) {
		if (!extrasArmed || clicksThisTick >= allowance || nanosSinceTickStart < nextExtraAtNanos) {
			return false;
		}

		clicksThisTick++;
		nextExtraAtNanos += Math.max(extraGapNanos * jitter(), MIN_EXTRA_GAP_NANOS);

		if (nextExtraAtNanos >= TICK_NANOS) {
			extrasArmed = false;
		}

		return true;
	}

	/** Clears carried-over credit so releasing a button never banks clicks for later. */
	public void release() {
		credit = 0.0;
		allowance = 0;
		clicksThisTick = 0;
		extrasArmed = false;
	}

	private double jitter() {
		return 1.0 + (random.nextDouble() * 2.0 - 1.0) * JITTER;
	}
}
