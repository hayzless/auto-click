# Auto Click

Client-side Fabric module for Minecraft 1.21.11 that clicks for you while you hold a mouse button,
with configuration inside ModMenu.

## What it does

- Holds left click: attacks entities, mines the block under the crosshair and swings at air at the
  configured rate, spread across frames instead of one click per tick, so the cadence can go past the
  20 CPS that the tick alone allows.
- Holds right click: uses items, places blocks and interacts at the configured rate instead of
  vanilla's fixed four tick (5 per second) cooldown.
- Mining speed stays vanilla's: the repeated attack calls are no-ops while a block is already
  breaking, so the module only adds the extra swings.
- Every click in a tick goes to the same entity the tick was locked to, which keeps Grim's
  multi-interaction checks quiet even on servers that enable experimental checks.

Vanilla's own input handler only attacks on discrete press events, not while a button is held, so the
module performs every click itself rather than topping up vanilla clicks.

## Install

1. Fabric Loader 0.20.1 or newer for Minecraft 1.21.11, plus Fabric API.
2. Drop the jar from `build/libs` into `mods/`.
3. Optionally install ModMenu 17.x to reach the config screen. The screen uses vanilla widgets, so
   no Cloth Config or other config library is needed.

## Building

Requires JDK 21 or newer; CI builds on 25. The Gradle wrapper fetches everything else.

```bash
./gradlew build      # compiles, remaps, runs the tests
./gradlew test       # timing model tests only
./gradlew runClient  # dev client with the mod loaded
```

The shipping artifact is `build/libs/auto-click-<version>.jar`. The `-sources` jar beside it is not
needed at runtime.

## Config

`config/auto-click.json`, or ModMenu → Auto Click.

| Setting | Default | Meaning |
| --- | --- | --- |
| `enabled` | `true` | Master switch, also bound to a key you can set in Controls. |
| `leftClick` / `rightClick` | `true` / `true` | Which buttons drive the module. |
| `cpsMin` / `cpsMax` | `10` / `13` | Target range. Every tick draws a rate from it, then jitters each interval by ±15%. |
| `clicksPerTick` | `5` | Hard ceiling per tick. This is what limits the top speed: `clicksPerTick × 20`. |
| `respectUseCooldown` | `false` | When on, right click stays inside vanilla's 5 uses per second. |

## About going past 20 CPS

Yes, but only in the sense of packets. Each injected click calls the same `doAttack()` the vanilla
client uses, and attacks are not rate limited client-side; the 20 CPS ceiling is just the tick rate.
The catch is server-side damage: 1.9+ combat charges per attack, so a sword deals full damage about
1.6 times per second no matter how many swings arrive. Above roughly 20 CPS extra clicks are swing
packets, not damage, and at high rates the frame rate becomes the real ceiling (a 60 fps client can
land at most three clicks per tick).

Grim 2.x currently ships no autoclicker check at all; its `Autoclicker` punishment category is a
placeholder. Its click-adjacent checks are the multi-interaction ones, which ship experimental and
therefore off, and which only concern switching entity or hit position inside a single tick.
