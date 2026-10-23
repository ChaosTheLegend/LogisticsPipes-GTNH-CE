# Logistics Pipes (GTNH fork) — Crafting Rework Implementation Plan

## 0. How to use this document

Sections are in dependency order: data model, then planning, commit, execution, the crafter pipe, failure handling, and the supporting pieces. Section 12 turns this into milestones with acceptance criteria, so an implementer (human or Claude Code) can work through it one milestone at a time.

Names of existing Logistics Pipes classes and interfaces (routed item info, routing table flags, additional target information) are from memory of upstream LP. Verify them against the fork before relying on them. Everything new in this document is a proposal; names can change freely.

Java syntax in sketches is illustrative. Use whatever language level the fork's buildscript targets (GTNH's buildscript can enable modern syntax via Jabel; otherwise stick to Java 8).

## 1. Overview

### 1.1 Goals

- Plan arbitrarily deep crafting requests into a merged graph (DAG) with aggregated demand, byproduct reuse, and reachability-aware sourcing.
- Execute deterministically. The network's central driver is the single authority over what gets crafted, where, and when. Crafting pipes never craft on their own.
- Make the known deadlocks impossible by construction: partial input sets in machines, head-of-line blocking in pipe queues, shared machines between recipes, and byproduct cycles.
- When items are missing or unreachable, stall instead of failing, show the player why, and resume automatically once the player fixes it.

### 1.2 Non-goals (v1)

- Splitting one plan node across multiple crafters for load balancing.

### 1.2.2 DO NOT IMPLEMENT

- Automatic sub-crafting of items that go missing during execution. The player resolves stalls.
- Counting chanced outputs in planning. They are a bonus at execution time only.
- Optimal (max-flow) source allocation. Greedy most-constrained-first is used.

### 1.3 Components and ownership

| Component | Runs where | Owns |
|---|---|---|
| `CraftingPlanner` | Off-thread, pure logic | Expansion, aggregation, crafter choice, heights |
| `RouteOracle` | Adapter over the router | Per-item reachability between locations, route lookup |
| `StockView` | Snapshot or live | Provider contents minus reservations |
| `CraftingDriver` | On the network identity, main thread | Jobs, tickets, ledger, execution loop |
| `Ledger` + `ReservationIndex` | Inside the driver | Ownership records for every job item |
| `CraftingPipe` (new) | Pipe tile | Ticket table, buffer, set assembly, insertion, output capture |
| UI | Client | Preview and job view |

The planner and the route oracle never move items. Only the driver (by routing) and the crafting pipes (by inserting and extracting) touch physical items.

### 1.4 Job lifecycle

```
Request -> Plan (async) -> Preview -> Commit -> Running -> Completed
                                         |          |
                                         |          +-> sets stall (red/orange) -> resume
                                         +-> Cancel -> Cancelling (wind-down) -> Cancelled
```

## 2. Data model

### 2.1 Resource keys

```java
final class ItemKey {          // exact identity
    Item item; int damage; NBTTagCompound nbt; int nbtHash;
}
final class FluidKey { Fluid fluid; NBTTagCompound nbt; }

enum MatchRule { EXACT, IGNORE_NBT, IGNORE_DAMAGE, ORE_DICT }

final class InputSpec {        // what a pattern slot accepts
    ItemKey key;               // or FluidKey
    MatchRule rule; String oreName;   // oreName only for ORE_DICT
    int qty;
    boolean consumed;          // false for programmed circuits, molds, lenses
}
```

Matching always uses exact item, damage, and NBT unless the input spec's rule says otherwise. Never write job markers into item NBT.

### 2.2 Pattern and crafter

```java
final class Pattern {
    PatternId id;
    List<InputSpec> inputs;           // consumed and non-consumed
    List<FluidSpec> fluidInputs;
    List<ItemStack> outputs;          // guaranteed only; first is the primary output
    List<FluidStack> fluidOutputs;
    List<ChancedOutput> chanced;      // display only, never planned on
    List<ConfigItem> config;          // circuit setting, mold, lens to apply before insert
    int yield(ItemKey key);
}

final class CrafterInfo {
    CrafterId id;                     // stable id of the crafting pipe
    Map<Integer, Pattern> patterns;   // by pattern slot
    BlockingMode mode;                // NONE, BLOCKING, SMART
    int bufferCapacityStacks;
    int priority;
}
```

### 2.3 Plan structures

```java
final class PlanNode {
    NodeId id; Pattern pattern; CrafterId crafter;
    int crafts; int height;
    List<InputResolution> inputs;     // how each input is sourced
    List<Allocation> allocations;     // who receives this node's outputs
    Map<ItemKey, Integer> surplus;    // leftover after allocations
}

final class InputResolution { InputSpec spec; int totalQty; List<SourceClaim> sources; }

sealed interface SourceClaim permits ProviderClaim, SurplusClaim, CraftClaim {}
record ProviderClaim(LocationId provider, ItemKey key, int qty) implements SourceClaim {}
record SurplusClaim(NodeId producer, ItemKey key, int qty)    implements SourceClaim {}
record CraftClaim(NodeId child, ItemKey key, int qty)         implements SourceClaim {}

record Allocation(NodeId consumer /* null = requester */, ItemKey key, int qty) {}
```

### 2.4 Tickets

A ticket is one dispatchable input set for one crafter: the inputs for `crafts` executions of one pattern (default 1; the driver may batch same-pattern crafts up to a per-crafter limit, for GT parallels).

```java
final class Ticket {
    TicketId id; JobId job; NodeId node; CrafterId crafter; Pattern pattern;
    int crafts; int height; long createdSeq;
    Map<ItemKey, Integer> expectedInputs, expectedOutputs;
    TicketState state;
}

enum TicketState { PLANNED, OPEN, COMPLETE, COMMITTED, INSERTED, DONE, CANCELLED, WRONG_CRAFT }
```

### 2.5 Ledger

```java
final class LedgerRecord {
    RecordId id; JobId job; ItemKey key; int qty;
    RecordState state;
    Location location;     // Provider(pos), Transit(routedItemId), Buffer(crafter), Machine(crafter)
    TicketId boundTo;      // consumer ticket, null for unbound leftovers
    TicketId producedBy;   // for EXPECTED records
}

enum RecordState { RESERVED, IN_TRANSIT, BUFFERED, INSERTED, EXPECTED, LEFTOVER }
```

State transitions:

```
RESERVED   -> IN_TRANSIT (routed out of provider)
IN_TRANSIT -> BUFFERED   (arrived at crafter)
BUFFERED   -> INSERTED   (set inserted into machine)
INSERTED   -> consumed   (record removed when ticket is DONE)
EXPECTED   -> BUFFERED   (output captured at producing crafter)
BUFFERED   -> IN_TRANSIT (routed to a consumer at another crafter)
any        -> LEFTOVER   (unbound surplus, strays, wind-down)
any        -> lost       (removed; triggers re-resolution)
```

`ReservationIndex` keeps `(location, key) -> reservedQty` across all jobs so provider availability is O(1): `available = physical - reserved`.

### 2.6 Display states

`SetState` is what the UI shows per ticket, derived from ticket and record state:

| SetState | Meaning | Color (always paired with icon + label) |
|---|---|---|
| WAITING | Inputs still being crafted | Neutral grey |
| READY / DISPATCHED | Inputs available or moving / in machine | Blue |
| DONE | Finished | Green |
| UNREACHABLE | Inputs exist but no route | Orange |
| MISSING | Inputs don't exist | Red |
| WRONG_CRAFT | Machine produced something else | Purple |

### 2.7 Job

```java
final class CraftingJob {
    JobId id; NetworkId network; Requester requester; ItemKey target; int qty;
    Plan plan; Map<TicketId, Ticket> tickets;
    JobState state;   // PLANNING, PREVIEW, RUNNING, CANCELLING, COMPLETED, CANCELLED
}
```

## 3. Planner

Pure logic, run off-thread on an immutable snapshot. Inputs: the request, `StockView` snapshot, `RouteOracle` snapshot, the pattern registry (crafters by output key), and the requester's location. Output: a `Plan` with missing and unreachable lists.

### 3.1 Phase A — structural expansion

Build the graph of potential nodes without quantities, assuming everything might need crafting.

1. Start from the requested key. For each key, look up candidate patterns (v1: highest crafter priority first, then first match).
2. Expand each pattern's inputs recursively. Merge nodes by `(outputKey, patternId)` so each item/pattern appears once.
3. Keep a path set of keys during DFS. A key that reappears on its own path makes that pattern unusable here; try the next candidate pattern, and if none works, mark the key as unresolvable (cycle).
4. Enforce a node cap and time budget; exceeding them returns an error to the player rather than freezing.

### 3.2 Phase B — quantities and resolution (topological order, root first)

Process keys in topological order so all parent demand is known before a key is resolved.

For each key:

1. `demand` = sum of allocations from all parents (plus the root request).
2. Demand is tracked per consumer, because reachability differs per consumer crafter.
3. Resolve per consumer, in this priority:
   1. **Surplus claims.** From producer nodes with surplus of this key, if the producer's crafter can reach the consumer's crafter for this key, and the producer does not depend on the consumer (directly or transitively). The dependency check prevents byproduct cycles.
   2. **Provider claims.** From `StockView.available(key, reachableTo(consumerCrafter))`. When several consumers compete for the same stock, handle the most constrained consumer first (fewest reachable sources), then by lowest height.
   3. **Craft claims.** Whatever remains: `crafts = ceil(remaining / yield)`. Record `crafts * yield - remaining` as surplus on the node. Push child demand: consumed inputs times `crafts`; non-consumed inputs once per crafter.
4. Nodes that end with `crafts == 0` are pruned.

If a need has no reachable source and can't be crafted, record it as missing (exists nowhere) or unreachable (exists, but no route) and continue, so the player sees the complete list.

### 3.3 Crafter selection

When a node is first given craft demand:

1. Candidates are crafters with the pattern, ordered by priority.
2. Discard candidates whose output can't reach every consumer of this node.
3. Pick the first remaining. If its inputs later turn out unreachable, retry once with the next candidate (one level of backtracking per node), then give up with an unreachable entry.

### 3.4 Heights

After Phase B, edges are craft edges plus surplus edges. `height(n) = 0` if `n` has no craft or surplus children, else `1 + max(height(child))`. Assert that the graph is acyclic; a cycle here is a planner bug.

Buckets are `(height, crafter)`. They are priorities, never barriers.

### 3.5 Output and preview

`Plan` contains nodes, edges, all claims, totals of raw materials, and the missing and unreachable lists. The preview offers Start, Start anyway (when there are missing or unreachable items; those sets begin stalled), and Cancel.

## 4. Commit (main thread)

1. **Re-validate provider claims** against live stock and the current `ReservationIndex`. Any shortfall is re-resolved from other reachable providers; if that fails, the affected tickets start as MISSING or UNREACHABLE.
2. **Create tickets** for every node, splitting `crafts` into tickets by the crafter's batch limit. Tickets start PLANNED.
3. **Write ledger records** atomically:
   - provider claims as RESERVED, bound to the consumer ticket;
   - craft and surplus claims as EXPECTED, with `producedBy` and `boundTo`;
   - surplus without a consumer as EXPECTED with `boundTo = null` (becomes LEFTOVER on arrival).
4. **Update `ReservationIndex`**, register the job with the driver, and mark it dirty for saving.

Provider pipes and every other request path on the network must read availability through `ReservationIndex`, so no other job or request can take reserved items.

## 5. Execution loop (`CraftingDriver.tick`)

Runs on the network identity each tick, with a budget (max tickets examined, max routes started, max messages sent) so huge jobs don't cause lag spikes.

### 5.1 Opening tickets (prefetch window)

A PLANNED ticket is opened (Open message sent to its pipe) when:

- its height is at most `minUnfinishedHeight + W` (W = 1 by default), and
- its crafter has buffer budget for the ticket's inputs.

Opening lets inputs start moving toward the crafter before the ticket is ready.

### 5.2 Readiness and routing

For an OPEN ticket, route each bound input record that is not yet at the crafter:

- BUFFERED at the same crafter: nothing to move.
- BUFFERED at another crafter, or LEFTOVER usable by this ticket: route it (buffered items are preferred over provider stock).
- RESERVED at a provider: extract and route.

Each routed item carries a job/ticket hint in its routing info (check what the fork's routed items can carry; upstream had an additional target information hook). The record becomes IN_TRANSIT with the routed item's id.

EXPECTED inputs whose producer hasn't finished leave the ticket WAITING.

### 5.3 Arrivals

The pipe reports `Arrived(ticketId, key, qty)` after matching items to its open tickets (section 6.3). The pipe may assign an item to a different open ticket than the hint said; the driver rebinds records to match the pipe's assignment. The pipe is authoritative for what physically arrived.

### 5.4 Commit decisions

The pipe reports `Complete(ticketId)` when a ticket's inputs are all buffered. Among COMPLETE tickets, the driver commits per crafter:

1. lowest height first, then oldest;
2. consecutive same-pattern tickets grouped, so SMART mode can stack them;
3. at most one outstanding commit per crafter, except same-pattern stacking in SMART mode.

### 5.5 Outputs

On `Output(ticketId, stacks)`:

- Matching expected outputs: EXPECTED records become BUFFERED at this crafter, bound to their consumers. The consumer ticket's readiness is re-evaluated.
- Surplus without a consumer becomes LEFTOVER (job-scoped until the job ends).
- Unplanned chanced outputs become LEFTOVER. If a RESERVED record of the same key, not yet in transit, belongs to a ticket this crafter can reach, rebind it to the leftover and release the provider reservation.
- The root node's output is routed to the requester.
- A mismatch with the ticket's expected outputs is a wrong craft (section 7).

### 5.6 Invariants

Checked on every state-changing event; assertions in dev builds, log + stall the job in production:

- For each job and key: the sum of record quantities by state matches the plan's expectation for the current progress.
- Every OPEN or later ticket's expected inputs equal the quantities of records bound to it.
- `ReservationIndex` equals the sum of RESERVED records per location and key.

## 6. Crafter pipe and ticket protocol

### 6.1 Messages

All messages carry `ticketId` and a sequence number. Handling is idempotent: a duplicate is acknowledged and ignored.

| Message | Direction | Meaning |
|---|---|---|
| `Open(ticket)` | driver → pipe | Create the needs entry for this ticket |
| `Arrived(ticket, key, qty)` | pipe → driver | Items assigned to a ticket |
| `Stray(key, qty)` | pipe → driver | Items that match no open ticket |
| `Complete(ticket)` | pipe → driver | All inputs buffered |
| `Commit(ticket)` | driver → pipe | Insert this set as soon as the blocking mode allows |
| `Inserted(ticket)` | pipe → driver | Set is in the machine |
| `Output(ticket, stacks)` | pipe → driver | Outputs captured and attributed |
| `Cancel(ticket)` | driver → pipe | Drop the ticket; its items become leftovers |
| `Sync(states)` | pipe → driver | Full ticket state, sent after load (section 9) |

Unacknowledged `Commit` (no `Inserted` within a timeout) and missing `Complete` (ledger says all inputs arrived) are resent.

### 6.2 Pipe state

- Ticket table: open tickets with remaining needs per key.
- Buffer inventory, capacity reported to the driver.
- Insertion queue of committed tickets.
- Attribution queue of inserted tickets per pattern, oldest first.

The pipe never creates needs, never inserts without a `Commit`, and never crafts from leftovers on its own. Legacy direct requests to a crafting pipe are wrapped as small jobs and go through the driver.

### 6.3 Matching arrivals to tickets

On arrival of a stack, fill needs of open tickets:

1. only needs whose input spec matches the stack;
2. most constrained first (exact specs before fuzzy or ore-dictionary ones);
3. then lowest height, then oldest ticket;
4. split the stack across needs as required.

Anything left over is a stray: kept in the buffer as LEFTOVER if there is space (report `Stray`), otherwise sent back to the network.

Needs are tracked per pattern per ticket, not per slot. Slot placement happens at insertion.

### 6.4 Insertion procedure

On `Commit`, the ticket enters the insertion queue. Any queued ticket may go first; there is no head-of-line blocking.

1. **Wait for the blocking mode:**
   - NONE: insert immediately. Only safe on crafters the planner flagged single-recipe for this job.
   - BLOCKING: insert when the machine is empty, ignoring configuration slots, non-consumed items, and fluids the pattern marks as retained.
   - SMART: insert when empty, or when the machine is processing the same pattern as the last inserted ticket and no other pattern is outstanding.
2. **Configure the machine:** apply the pattern's config (programmed circuit setting, mold, lens) before inserting inputs. If configuration can't be applied, the ticket goes to WRONG_CRAFT with a config error.
3. **Insert atomically:** simulate inserting every input stack and fluid first; if any part doesn't fit, insert nothing and retry later.
4. Report `Inserted`.

A ticket blocked longer than a threshold gets a warning in the job view, not a failure.

### 6.5 Output capture and attribution

The pipe extracts outputs from the machine (verify how the fork's crafting pipe currently gets outputs back, and keep that path). Captured outputs are attributed to the oldest INSERTED ticket of the matching pattern on this crafter. BLOCKING mode guarantees one pattern at a time and SMART stacks only identical sets, so attribution is unambiguous.

If captured items don't match the attributed ticket's expected outputs, report the ticket as a wrong craft.

### 6.6 Fluids

Fluids use the same model with `FluidKey` and mB quantities: records, needs, atomic insertion into tanks or hatches, and output capture.

## 7. Failure handling

### 7.1 Cases

| Case | Detection | Action | Resulting state |
|---|---|---|---|
| Provider items lost | Extraction returns less than reserved | Retry the same provider (items may have moved slots), then other reachable providers with unreserved stock | MISSING (red) |
| Items unreachable | No route at routing time | Retry with an alternative route, then other providers | UNREACHABLE (orange) |
| In-transit item lost | Routed item destroyed or dropped event, or arrival timeout | Same as provider lost | MISSING |
| Buffered items lost | Pipe broken | Records removed, tickets reopened; if the crafter is gone, re-select a crafter with the pattern | MISSING or UNREACHABLE |
| Wrong craft | Output mismatch or config error | Stop committing to that crafter for this job; unexpected items become LEFTOVER | WRONG_CRAFT (purple) |
| Insertion stuck | Blocked past threshold | Warning only | unchanged |

No automatic sub-crafting of missing items in any case.

### 7.2 Retry scheduler

- **Event triggers** (retry affected tickets immediately and reset backoff): inventory change on a provider holding a stalled key, routing table change, chunk load containing network members.
- **Timer:** start at 2 s, double up to a 30 s cap. One job's stalled tickets are retried together in one pass.
- Each retry re-evaluates the cause, so a ticket can move between red and orange in either direction.

### 7.3 Cleanup

- When a stalled ticket is filled from a different source, release its original reservation.
- When a chunk loads, reconcile the records located there against actual contents. Records no longer needed become LEFTOVER.

### 7.4 Stall scope

Independent branches keep running while a ticket is stalled; only its dependents wait. (Kept as an open question; see section 13.)

### 7.5 Player-made deadlock

The player may fix a stall with a manual request that needs items this job reserved. The stall detail shows reserved amounts, and cancelling the job releases everything.

## 8. Completion and cancellation

### 8.1 Completion

When the root output reaches the requester:

1. release all RESERVED records and non-consumed config items;
2. LEFTOVER records are sent to the item/fluid-sinks/basic pipes from buffers;
3. remove the job and its tickets; save.

### 8.2 Cancellation (wind-down)

1. Stop opening and committing tickets.
2. Send `Cancel` for OPEN and COMPLETE tickets; their buffered items become LEFTOVER.
3. Let INSERTED tickets finish; their outputs become LEFTOVER.
4. Let IN_TRANSIT items arrive; they become LEFTOVER.
5. Release all RESERVED records.
6. Send all LEFTOVERs to item/fluid-sinks
7. The job becomes CANCELLED once nothing is INSERTED or IN_TRANSIT.

### 8.3 Leftover providers

Each crafting pipe exposes its LEFTOVER buffer contents through the provider interface, at high priority so leftovers are used before general stock. This is also the recovery path for items with no reachable route back to storage.

## 9. Persistence, chunk loading, topology

### 9.1 Saving

- Driver state (jobs, tickets, ledger, reservation index) is saved with the network identity, keyed by network id, for example in world saved data.
- Each crafting pipe saves its ticket table, buffer, and queues in its tile NBT.

### 9.2 Loading and reconciliation

1. IN_TRANSIT records re-attach to routed items by id (routed items are saved inside pipes). Records that can't re-attach go through the lost path.
2. Each pipe sends `Sync` with its ticket states. The driver is authoritative for intent (which tickets exist and should run); the pipe is authoritative for physical contents (what is buffered or inserted). Differences are resolved accordingly.

### 9.3 Chunk loading

Locations in unloaded chunks are unreachable, so their tickets show orange and resume on chunk load. Player documentation should recommend keeping crafting networks chunk loaded.

### 9.4 Topology and filter changes

A routing table change invalidates the reachability cache and revalidates routes for all pending records and open tickets.

### 9.5 Network split and merge

On a split, the job stays with the identity that contains the requester, and records on the other side become unreachable.
On a merge, the surviving identity's driver takes over both sets of jobs.

## 10. UI

### 10.1 Preview

Tree of nodes with quantities, chosen crafter, and sources per input; totals of raw materials; missing (red) and unreachable (orange) lists; buttons Start, Start anyway, Cancel.

### 10.2 Job view

Tickets grouped by node, colored by `SetState`, always with an icon and text label next to the color. A third neutral color marks tickets that are just waiting on their inputs.

### 10.3 Stall detail

Item, quantity, waiting ticket and crafter location, cause (missing, unreachable, wrong craft), and the quantities this job has reserved.

### 10.4 Sync

Send only changed ticket states to clients with the job view open.

## 11. Testing strategy

### 11.1 Planner unit tests (no Minecraft)

Using `FakeStockView`, `FakeRouteOracle`, and a fake pattern registry:

- electric piston: shared rods, motor before piston, correct allocations;
- aggregation rounding: three parents needing 1 each from a yield-2 recipe produce 2 crafts and 1 surplus;
- partial stock: 40 from stock, 60 crafted;
- recipe loop detected and the alternative pattern used;
- byproduct cycle refused by the dependency check;
- one-way reachability: an unreachable provider is not claimed;
- most-constrained allocation: the crafter with a single reachable source gets that source;
- non-consumed inputs are not multiplied by craft count;
- heights include surplus edges.

### 11.2 Driver simulation tests

A discrete tick simulator with fake pipes and fake machines (duration, capacity, optional wrong-recipe injection):

- full runs with invariant checks every tick;
- duplicated and dropped messages;
- items lost from a provider, in transit, and from a buffer;
- unreachable then reconnected; missing then refilled;
- cancellation at every stage;
- save and load mid-job.

### 11.3 In-game test worlds

- GT assembler shared by motor and piston recipes;
- programmed circuit configuration;
- a multiblock with several pipes feeding one input bus;
- one-way pipes and filters;
- a chunk boundary crossing the network.

## 12. Milestones

| # | Milestone | Acceptance criteria |
|---|---|---|
| M1 | Data model and planner | All planner unit tests in 11.1 pass |
| M2 | `StockView`, `ReservationIndex`, ledger, commit | Commit writes correct records; other requests can't take reserved items |
| M3 | Crafting pipe: ticket table, matching, blocking modes, configuration, atomic insertion, output capture | Pipe-level tests with a fake driver; no insert without commit |
| M4 | Driver loop, protocol, routing integration, prefetch | Electric piston job completes in-game on a shared assembler |
| M5 | Failure handling, retry scheduler, wrong craft detection | Simulation tests in 11.2 pass for loss and reachability cases |
| M6 | Completion, cancellation, leftover providers | Cancel at any stage leaves no reserved or orphaned items |
| M7 | Persistence and reconciliation | Save and load mid-job resumes correctly |
| M8 | UI: preview, job view, stall details | All states visible with colors and labels |

## 13. Resolved questions:

- **Stall scope:** keep running independent branches
- **Leftover reuse after cancel:** always release them as general leftovers
- **Buffer capacity:** use the crafting buffer made for the pattern crafting pipes, this should be enough to dispatch all sets
- **Recipe choice:** v1 takes the first pattern by priority;
- **Start anyway:** job with missing items at plan time must have no missing items to start
- **Fluid delivery:** base fluid handling on the pattern crafting pipe implementation
- **Crafting modules/Crafting pipes** they can take part in a new crafting tree, however they DO NOT support blocking mode and are marked for removal, all nodes using them should have a warning sign in UI
