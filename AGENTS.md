# AGENTS.md

Working notes for anyone (human or agent) changing this mod. Read this before touching the click
pipeline: most of the rules below were learned by reading the decompiled 1.21.11 client, not by
guessing.

## What this is

A client-only Fabric mod for Minecraft 1.21.11. Holding left click on an entity or right click
attacks/uses at a configured rate, with ModMenu for configuration. There is no server-side component
and no networking of its own; it only calls the same client methods vanilla input calls.

It is a hobby client module. The design target is plausible input that does not trip anti-cheat
checks, not stealth against the operating system or the player's machine.

## Toolchain

| Thing | Version | Where |
| --- | --- | --- |
| Minecraft | 1.21.11 | `gradle.properties` |
| Yarn mappings | `1.21.11+build.6` | `gradle.properties` |
| Fabric Loader | 0.20.1 | `gradle.properties` |
| Loom | 1.19.3 | resolved from `loom_version` |
| Fabric API | 0.141.6+1.21.11 | `gradle.properties` |
| ModMenu | 17.0.1 | `modmenu_version` — the last 1.21.11 line, v18+ is the 26.x scheme |
| Java | 21 release target, compiled by JDK 25 in CI | `build.gradle`, `.github/workflows/build.yml` |

Do not use language or library APIs newer than Java 21 even though CI runs 25.

Cloth Config is deliberately not a dependency. The config screen is hand-built from vanilla widgets
so ModMenu stays the only optional install.

## Commands

```bash
./gradlew build      # compiles, remaps, runs the unit tests
./gradlew test       # tests only
./gradlew runClient  # dev client, logs to stdout
./gradlew genSources # decompile Minecraft (needs the 2G heap already set in gradle.properties)
```

`gradle.properties` sets `org.gradle.jvmargs=-Xmx2G`. At the template default of 1G, `genSources`
dies with "JVM garbage collector is thrashing".

### Reading the vanilla source

Mappings give signatures but not behaviour, and this project depends on behaviour. After
`genSources`, the decompiled client is the newest `-sources.jar` under the mapped maven cache:

```bash
find .gradle/loom-cache/minecraftMaven -name "*sources.jar" -print
unzip -p <that jar> net/minecraft/client/MinecraftClient.java | less
```

Read `handleInputEvents`, `doAttack`, `doItemUse` and `handleBlockBreaking` before changing click
logic. Signatures alone are routinely misleading here.

## Layout

```
org/hayzless/
  AutoClick.java              mod id, logger, Identifier helper
  AutoClickClient.java        client entrypoint: config, module registry, toggle keybind
  config/AutoClickConfig.java JSON config, clamping, legacy key migration
  input/ClickScheduler.java   timing model, no Minecraft imports, unit tested
  module/Module.java          base class (enable state, tick/frame hooks)
  module/ModuleManager.java   registry, fans hooks out to enabled modules
  module/impl/AutoClickerModule.java  gating, injection, entity lock
  mixin/MinecraftClientMixin.java     tick/render hooks + doAttack/doItemUse gates
  mixin/MinecraftClientAccessor.java  @Invoker/@Accessor for private client members
  screen/AutoClickConfigScreen.java   config UI
  modmenu/ModMenuIntegration.java     ModMenu entrypoint
```

Adding a module: extend `Module`, register it in `AutoClickClient.onInitializeClient`, add a
config section and a row on the screen. `ModuleManager` already dispatches `onStartTick` and
`onFrame` only to enabled modules.

## Invariants

These are not style preferences. Breaking one either kills the feature or trips a server check.

1. **Vanilla attacks on press events, not held state.** `handleInputEvents` uses
   `while (attackKey.wasPressed())`; `KeyBinding.timesPressed` only increments on press. A held
   button therefore produces *no* vanilla attack. The module must perform every click itself —
   an earlier version only injected the surplus above one click per tick and delivered nothing at
   12 CPS.
2. **Left click is only active with an entity under the crosshair.** Block breaking lives in
   `handleBlockBreaking`, driven by `attackKey.isPressed()`, and is untouched. Never gate or inject
   while the crosshair is on a block or air.
3. **`doAttack()` returns early when `attackCooldown > 0`.** That field is set to 10 only for a
   MISS or null target, never for an entity hit, so repeated entity attacks do reach the wire. Air
   swings are capped at 2 CPS by vanilla and are not worth chasing.
4. **`itemUseCooldown` is set inside `doItemUse()`, but only checked by `handleInputEvents`.**
   Injected calls bypass the 4-tick gate. `respectUseCooldown` honours it manually, which is the
   only place that behaviour exists.
5. **One entity per tick.** All clicks in a tick target `lockedEntityId`. Grim's `MultiInteractA`
   and `MultiInteractB` only fire on a *different* entity or a different `INTERACT_AT` position
   inside one tick; repeated hits on one entity are never flagged. Both ship experimental, hence off
   by default, but keep the lock anyway.
6. **Reentrancy.** `injecting` must wrap every `@Invoker` call, otherwise the gate cancels the
   module's own click.
7. **`ClickScheduler` stays free of Minecraft types.** That is what makes it testable without a
   game. Rate maths belongs there, not in the module.
8. **Mixins are client-only.** Keep `environment: client` in `fabric.mod.json` and the mixins in the
   `client` array of `auto-click.mixins.json`, or a dedicated server will crash loading client
   classes.
9. **Keybind API is 1.21.9+.** `KeyBinding.Category.create(Identifier)` replaces string categories,
   and registering the same category twice throws.

## Timing model

`ClickScheduler` converts CPS into a per-tick `allowance`. Fractional credit carries between ticks
so a 12.5 CPS target is exact over time; the leftover credit is clamped after subtracting the
allowance, not before, otherwise jitter biases the average down. Clicks the client does not perform
itself are spread across the tick on a nanosecond clock, which is the only way to exceed 20 CPS.
Each interval is jittered ±15%.

Top speed is `clicksPerTick × 20`, and in practice the frame rate: a 60 fps client can land at most
about three clicks per tick. Left-click damage is a separate limit — 1.9+ combat charges per attack,
so a sword deals full damage roughly 1.6 times per second regardless of click rate.

## Tests

`ClickSchedulerTest` runs seeded simulations at 12 or 240 frames per tick, covering mean rate at
12.5/50/100 CPS, per-tick ceilings across 48 rate/ceiling combinations, sub-1 CPS, jitter, determinism
and release behaviour. Two tests exist specifically because of invariant 1: taps must not stack on
top of a full allowance, and taps below the budget must still be filled. Keep both when touching the
rate maths.

There is no in-game test harness; Loom here exposes no client-gametest task. Held-click behaviour is
covered by the scheduler tests plus a reading of the vanilla call chain, so a manual hold-click in a
world is still the honest final check.

## Gotchas

- `run/` is the dev client workspace (config, logs); it is not part of the mod.
- `build/libs/auto-click-<version>.jar` is the shipping artifact; `-sources.jar` is not.
- The config file lives at `config/auto-click.json` and is rewritten on load, dropping keys from
  older builds.
- Grim 2.x ships no autoclicker check; its `Autoclicker` punishment category is a placeholder. Do
  not add compensating heuristics for a check that does not exist, and do not raise `clicksPerTick`
  past 6 — that is already 120 CPS of packets.

## Style

Tabs, four-wide. Short `final` classes, package-private helpers, comments that explain *why* a
vanilla detail forced the code shape. JSON files are tab-indented to match `fabric.mod.json`.
