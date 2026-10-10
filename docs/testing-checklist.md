# Testing checklist

Everything in `docs/` for the router rework and the item transport rewrite that is written but still needs testing,
gathered in one place. Each item links to the doc that has the details. Bug IDs (B22, ...) are from
[bug-list.md](bug-list.md). Passed items are removed; what failed went to the bug list, with a retest line here.

Use spark for every performance item and write down before/after numbers. Scale target: ~50k pipes, with routing well
under one 50 ms tick.

**Last pass: 2026-10-01, dedicated server.** Clump transport runs on a dedicated server without client-class crashes.
The 2026-09-30 pass covered delivery, client animation, most of the pipe-break rules, pipe rendering, full/missing
inventory routing and joining networks.

---

## 1. Item transport rewrite (clumps)

Details: [item-transport-rewrite.md](item-transport-rewrite.md).

**Delivery and animation**
- [ ] Packets only go to players in the same dimension within 64 blocks of either end of the corridor.

**Breaking pipes under a clump in flight**
- [ ] B22: breaking the plain transport pipe a clump is in drops its items (works for routed pipes only).
- [ ] B23: a clump that turns back at a break is routed on right away, and goes to a default route when there's no
      path. New items sent into a broken corridor take the new path or a default route instead of waiting in the
      retry buffer.
- [ ] Crafters re-request the items that were dropped.
- [ ] B27: at a junction of plain transport pipes, items take a random nearby exit instead of being routed (once
      that's built).

**Save / unload**
- [ ] Save and reload with clumps in flight: the items arrive after the reload (restored clumps are pathless, wait ≥20
      ticks and are routed again).
- [ ] Unload the target chunk mid-flight: the items arrive once it loads again.
- [ ] Unload the source chunk right after a departure: nothing joins the clump that is now on disk.
- [ ] Breaking a pipe that holds restored clumps drops them and calls `itemWasLost` (crafters re-request).

**Config and performance**
- [ ] `itemClumpTransport=false` restores the old behaviour.
- [ ] Spark: server tick time with a large number of items in flight, clumps vs. `itemClumpTransport=false`.
- [ ] Busy destination with many items in transit: `countOnRoute` / in-transit bookkeeping no longer shows up
      (`InTransitTracker` replaced the O(n) queue).

## 2. Lag fixes

Details: [lag-investigation.md](lag-investigation.md), and the known issues in [pattern-crafting.md](pattern-crafting.md) §10.

- [ ] **Idle pipe cost (§4):** `LPConstants.DEBUG` now reads `logisticspipes.enableDebug`. Measure the per-pipe cost
      with debug off. It was 20–50 µs per pipe per tick; check it is now well under that, including plain transport pipes.
- [ ] Debug off really is off in a normal run: `/lp debug` and `PowerJunctionCheatPacket` are refused, no
      `TOOLTIP_INFO`.
- [ ] Debug on with `-Dlogisticspipes.enableDebug=true` still works for a debug session.
- [ ] **Power view reuse (§4):** pipes still show powered/unpowered correctly, and update when a power junction is
      added, removed, runs dry or gets reconnected.
- [ ] **Load lag (B10):** loading a large network no longer freezes the client or server (the router managers use a
      position index now). Profile it with spark on the client and the server.
- [ ] Confirm the ranking of TPS causes in lag-investigation §1 with spark (it was found by reading the code only).
- [ ] If spark still shows a per-pipe floor, check the follow-ups at the end of §4 (BC tile part, `isDirty`, ticking
      empty transport pipes, periodic corridor re-scan).

The particle fixes (§2) aren't written yet. For now, `enableParticleFX=false` in the server config turns all LP
particles and laser packets off (section 7).

## 6. Pipe rendering

The pipe body is baked into chunk geometry (solid pass only) and the TESR returns early for pipes with nothing dynamic
to draw. Looks, updates, TESR early return and Angelica all passed on 2026-09-30.

- [ ] Toggling "Use New Renderer" switches between the old and new models.
- [ ] The settings tab shows only "Use New Renderer" and "Max. Distance for Pipe Content". "Pipe render distance",
      "fallback renderer" and "VBO renderer" are gone.
- [ ] B7: a neighbour that changes shape without changing the connection state (e.g. one non-solid block replaced by
      another with different bounds) updates the stretched side.

## 8. Routing to full or missing inventories

`ModuleItemSink` caches room in its target inventory for 20 ticks and stops advertising itself when there's no room or
no inventory. Full inventories, basic pipes with no inventory, joining networks and B18–B21 all passed on 2026-09-30.

- [ ] B24: a full AE interface stops being advertised (drawers and barrels already do).
- [ ] B2: a basic pipe with its connections set by shift+wrench only pushes into the chosen inventory (once that's
      built).
- [ ] Items in transit don't leak: after the network is idle, `countOnRoute` is back to 0 for every sink (e.g. sinks
      don't stay "full" with nothing in flight).
- [ ] Spark: mspt in the 20k-pipe world with all storage full, before vs. after.
- [ ] Spark: no noticeable routing cost from recomputing "unreachable" routes after graph changes (they used to be
      served stale).
