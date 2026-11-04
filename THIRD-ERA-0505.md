# VillagePlus 0.50.5 — third-era completion audit

## Changes

- Court convoys retain the destination floor. Alternate path endpoints cannot substitute a point two blocks below the target. A guard must reach the court level before the ten-block player capture radius can finish the approach.
- Ordinary-house replacement can run up to three independent sites, while preserving build -> transfer -> HOME remap -> demolition for each house.
- Crew replenishment accepts actual collision surfaces such as snow layers and slabs; existing builders keep their profession experience.
- Cathedral work is ordered by nearby 8x8 patches within each layer. Up to eight crew members can pick reachable independent work on the same layer. Loading an unfinished cathedral preserves its completed prefix and every remaining change.
- Construction accepts the correct neighbour-dependent fence/wall/stair state instead of repeatedly placing an already completed block. Existing pending jobs use the corrected comparison after reload.
- Loaded settlement planning covers the village's 384-block work radius while retaining loaded/simulating-chunk checks. Distant settlements do not request terrain loading for their estimated development.
- Factories receive a queued cane garden; workers harvest real upper cane blocks and leave roots. Gravel can be supplied and processed into flint. Existing completed factories receive a normal builder job for the garden.
- Factory inputs include logs/planks, raw gold/copper, sugar cane, flint and redstone. The three real blast furnaces process iron, gold and copper. Stock preparation consumes real items for planks, sticks, paper, nuggets and industrial gunpowder.
- Industrial gunpowder is a game recipe: 2 coal + redstone + flint -> 4 gunpowder. This is an abstract crafting recipe, not a real chemical process.
- Available factory orders can proceed when a different order lacks materials. Per-product stock targets prevent one cheap recipe from consuming every input. Porters alternate supply and output trips; inventories remain authoritative.
- Trapped construction workers can open temporary owned headroom for an upward escape; the removed blocks are queued for restoration. Active prison geometry takes precedence over overlapping cathedral work after reload.
- Occupied recorded scaffolds are retained for descent; unoccupied builder scaffolds can be replaced, while unrecorded player scaffolds are preserved.
- Scaffold cleanup checks the entire vertical support column, including workers standing on its top, before removing the base.
- Village statistics distinguish the third-era housing/cathedral transition, factory construction and actual third-era activities.

## Verified

- Actual client convoy fixture: guard and player start directly below a court platform, climb the side stairs by 12 blocks, enter the upper cage and begin the hearing. `/tmp/elevated-court-0505.log`.
- Real worker/furnace/porter flow: spruce wood, cane, iron/gold ore and consumables reach the factory; all four personal weapon types, cartridges and a complete diamond armour set return to treasury; a guard draws real equipment from that stock. No worker positions or recipe counters are advanced by the flow test. The fixture starts with a built factory and finite raw inventories. `/tmp/factory-flow-0505.log`.
- Isolated village/transport regression suite passes, including inventory-preserving housing replacement, castle/cathedral plan protection, guard equipment, prison routing and train collision. `/tmp/era0505-regression-scaffolds.log`.
- Normal-world I -> II passed. Every ordinary residence was rebuilt in the continuing III transition. The cathedral and factory completed and the normal-world transition reached III: `/tmp/era-completion-0505-entry.log`, PASS at 19:38:35. This run includes save/reload checkpoints and daytime held for the last high-rise portion.

## Normal-world soak setup

Seed -2146509050469496627, spectator observer, actual villagers/builders, requested tick rate 10000. Only the normal transition entry points are requested; construction indices and completion flags are not patched. The fixture disables the dedicated-server empty-player pause and watchdog because accelerated runs use an observer outside the player manager. Save/reload checkpoints preserve the real building queue.

After the cathedral passes 100,000 operations, the opt-in fixture holds daytime for the remaining high-rise construction. Earlier housing and cathedral work experienced the normal day/night cycle. This changes only the test work window, not plan indices, block placement or completion flags.

Final scaffold access handles snow removal and approaching a supported upper entry. Construction falls remain a known limitation accepted for this release; this test does not assert zero worker deaths.
