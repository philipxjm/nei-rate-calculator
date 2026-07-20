# NEI Rate Calculator

An in-game rates and production-chain calculator for [GT: New Horizons](https://github.com/GTNewHorizons/GT-New-Horizons-Modpack), built as an NEI addon. Think [ShadowTheAge's web calculator](https://github.com/ShadowTheAge/gtnh), but inside the game, using your own NEI bookmarks as the plan.

Made for GTNH 2.9.0 (NEI 2.8.101-GTNH, GT5-Unofficial 5.09.52.594). The multiblock math is taken straight from the GT5U sources of that version, so overclocks, coil bonuses and parallels match what the machines actually do.

## What it does

**Press K while hovering a GT recipe in NEI** to open the rate calculator:

- Pick the machine that runs the recipe — singleblock at any tier, or a real multiblock (EBF, Volcanus, Mega BF, LCR/Mega CR, the GT++ Industrial machines, Chemical Plant, Precise Auto-Assembler, and ~40 others).
- Structure choices that matter are configurable per machine: coil tier for heat overclocks, pipe casings for Chemical Plant parallels, anvil tier on the Sledgehammer, solenoids on the LFE, and so on.
- Set energy hatch amps for multi-amp setups. Parallels are limited by the EU budget first and then overclocked, the same order GregTech's own processing logic uses.
- See time per craft, EU/t, crafts per minute, per-minute input/output rates, and how many machines you need for a target rate.

**Press K on a bookmark group** (the chain bracket in NEI's bookmark panel, or any item in it) to open the whole chain as a tree:

- The group's top product becomes the root, with the target rate seeded from the amount you configured on the bookmark.
- The target can be a rate per minute or a fixed number of crafters — toggle the button next to the input field and the tree scales from whichever you set, showing the equivalent in the other unit.
- Every ingredient that something in the group *produces* gets expanded with that recipe. Everything else stays a raw input. Your bookmarks decide where the tree stops.
- Each step in the tree has its own machine, tier, and structure configuration.
- The Totals view sums machine counts, average EU/t, and raw inputs per minute for the whole chain.

There's a settings screen (button inside the calculator) for the default voltage tier, default amps, whether to prefer multiblocks, and a cap on assumed parallels. The K key can be rebound in NEI's keybind options.

## Installing

Drop the jar from [Releases](../../releases) into your `mods` folder. Needs NotEnoughItems and GregTech, which GTNH obviously has. Client-side only.

Since 1.4, the tree is solved as a linear program (the same idea as ShadowTheAge's calculator): all step rates settle simultaneously, so recycling loops net out, byproducts feeding other branches are credited instead of double-counted, and overproduction shows up as a Surplus section in Totals rather than breaking the plan.

## Known gaps

- Mega Vacuum Freezer subspace coolant, DEFC core-tier overclocks, and Chemical Plant solid-casing recipe gating aren't modeled.
- The Mega Distillation Tower is calculated as its code actually behaves (20%/50% slower), not as its tooltip claims — that looks like an upstream bug.
- Sub-tick parallel scaling (overclocking past 1 tick) isn't applied.

## Building

```
./gradlew build
```

Standard GTNH ExampleMod toolchain; the jar lands in `build/libs/`.
