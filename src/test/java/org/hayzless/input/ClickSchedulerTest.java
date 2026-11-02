package org.hayzless.input;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the timing model the way the client drives it: one allowance per tick, then a stream of
 * frames inside that tick. Vanilla only clicks on discrete presses, so the held case expects no
 * vanilla clicks at all.
 */
class ClickSchedulerTest {
	private static final long SEED = 424242L;
	private static final int SECONDS = 600;

	@Test
	void averagesRequestedRateWhileHeld() {
		Simulation simulation = new Simulation(12.0, 13.0, 5, 12, false, SEED).run(20 * SECONDS);

		assertEquals(12.5, simulation.clicksPerSecond(), 1.0, "mean of the configured 12-13 CPS range");
	}

	@Test
	void goesPastTwentyCps() {
		Simulation simulation = new Simulation(50.0, 50.0, 5, 12, false, SEED).run(20 * SECONDS);

		assertEquals(50.0, simulation.clicksPerSecond(), 1.5, "50 CPS target");
		assertTrue(simulation.maxClicksInATick <= 5, "per-tick ceiling was respected");
	}

	@Test
	void reachesTheConfiguredCeiling() {
		Simulation simulation = new Simulation(100.0, 100.0, 5, 12, false, SEED).run(20 * SECONDS);

		assertEquals(100.0, simulation.clicksPerSecond(), 2.0, "5 clicks per tick is 100 CPS");
	}

	@Test
	void clampsToClicksPerTickSetting() {
		Simulation simulation = new Simulation(100.0, 100.0, 3, 12, false, SEED).run(20 * SECONDS);

		assertEquals(60.0, simulation.clicksPerSecond(), 1.0, "3 clicks per tick caps a 100 CPS request at 60");
		assertTrue(simulation.maxClicksInATick <= 3, "never more than three clicks in one tick");
	}

	@Test
	void neverExceedsMaxPerTickForAnyRate() {
		double[] rates = {1.0, 8.0, 19.0, 20.0, 21.0, 37.5, 64.0, 100.0};

		for (double rate : rates) {
			for (int maxPerTick = 1; maxPerTick <= 6; maxPerTick++) {
				Simulation simulation = new Simulation(rate, rate, maxPerTick, 12, false, SEED).run(20 * 30);
				assertTrue(simulation.maxClicksInATick <= maxPerTick,
						"rate " + rate + " exceeded " + maxPerTick + " clicks in a tick");
			}
		}
	}

	@Test
	void tapsAreNotDoubledUp() {
		// A human tapping every tick already supplies one click, so the module must add none.
		Simulation simulation = new Simulation(20.0, 20.0, 5, 12, true, SEED).run(20 * SECONDS);

		assertEquals(20.0, simulation.clicksPerSecond(), 0.5, "a tick tap plus a 20 CPS budget is still 20 CPS");
		// Fractional credit can shift the odd click between neighbouring ticks, never add a second source.
		assertTrue(simulation.maxClicksInATick <= 2, "a tap must not stack on top of a full allowance");
	}

	@Test
	void tapsBelowTheBudgetStillFillTheBudget() {
		Simulation simulation = new Simulation(30.0, 30.0, 5, 12, true, SEED).run(20 * SECONDS);

		assertEquals(30.0, simulation.clicksPerSecond(), 1.5, "tap plus injected clicks should reach 30 CPS");
	}

	@Test
	void slowRatesDoNotNeedInjectedClicks() {
		Simulation simulation = new Simulation(10.0, 10.0, 6, 12, false, SEED).run(20 * SECONDS);

		assertEquals(10.0, simulation.clicksPerSecond(), 0.8, "10 CPS target");
	}

	@Test
	void handlesSubOneCps() {
		Simulation simulation = new Simulation(0.5, 0.5, 5, 12, false, SEED).run(20 * 200);

		assertEquals(100.0, simulation.clicks.size(), 15.0, "0.5 CPS over 200 seconds");
	}

	@Test
	void producesJitteredIntervals() {
		Simulation simulation = new Simulation(60.0, 60.0, 5, 240, false, SEED).run(20 * 20);

		List<Double> intervals = new ArrayList<>();
		for (int i = 1; i < simulation.clicks.size(); i++) {
			intervals.add((simulation.clicks.get(i) - simulation.clicks.get(i - 1)) / 1_000_000.0);
		}

		Set<Double> distinct = new HashSet<>(intervals);
		double mean = intervals.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
		double variance = intervals.stream().mapToDouble(value -> (value - mean) * (value - mean)).average().orElseThrow();
		double coefficient = Math.sqrt(variance) / mean;

		assertTrue(distinct.size() > 20, "intervals should not snap to a handful of tick multiples");
		assertTrue(coefficient > 0.05, "intervals should vary, coefficient was " + coefficient);
	}

	@Test
	void isDeterministicForAGivenSeed() {
		Simulation first = new Simulation(37.0, 37.0, 5, 12, false, SEED).run(20 * 5);
		Simulation second = new Simulation(37.0, 37.0, 5, 12, false, SEED).run(20 * 5);

		assertEquals(first.clicks, second.clicks);
	}

	@Test
	void releasingClearsBankedCredit() {
		ClickScheduler scheduler = new ClickScheduler(SEED);

		for (int tick = 0; tick < 100; tick++) {
			scheduler.beginTick(100.0, 5);
			scheduler.takeVanillaClick();
		}

		scheduler.release();
		scheduler.beginTick(1.0, 5);

		assertFalse(scheduler.takeVanillaClick(), "releasing should not bank clicks for the next press");
	}

	private static final class Simulation {
		private final ClickScheduler scheduler;
		private final double minCps;
		private final double maxCps;
		private final int maxPerTick;
		private final int framesPerTick;
		private final boolean vanillaClickEveryTick;
		private final Random targetRandom;
		private final List<Long> clicks = new ArrayList<>();

		private int maxClicksInATick;
		private long ticks;

		private Simulation(double minCps, double maxCps, int maxPerTick, int framesPerTick,
				boolean vanillaClickEveryTick, long seed) {
			this.scheduler = new ClickScheduler(seed);
			this.minCps = minCps;
			this.maxCps = maxCps;
			this.maxPerTick = maxPerTick;
			this.framesPerTick = framesPerTick;
			this.vanillaClickEveryTick = vanillaClickEveryTick;
			this.targetRandom = new Random(seed ^ 0x9E3779B97F4A7C15L);
		}

		private Simulation run(int tickCount) {
			for (int tick = 0; tick < tickCount; tick++) {
				step();
			}

			return this;
		}

		private void step() {
			double target = minCps + targetRandom.nextDouble() * (maxCps - minCps);
			scheduler.beginTick(target, maxPerTick);

			long tickBase = ticks * ClickScheduler.TICK_NANOS;
			int clickCount = 0;

			if (vanillaClickEveryTick && scheduler.takeVanillaClick()) {
				clickCount++;
				clicks.add(tickBase);
			}

			for (int frame = 1; frame < framesPerTick; frame++) {
				long offset = frame * ClickScheduler.TICK_NANOS / framesPerTick;

				if (scheduler.extraClickDue(offset)) {
					clickCount++;
					clicks.add(tickBase + offset);
				}
			}

			maxClicksInATick = Math.max(maxClicksInATick, clickCount);
			ticks++;
		}

		private double clicksPerSecond() {
			return clicks.size() / (ticks / ClickScheduler.TICKS_PER_SECOND);
		}
	}
}
