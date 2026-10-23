package logisticspipes.crafting.v3;

import static logisticspipes.crafting.v3.Fakes.in;
import static logisticspipes.crafting.v3.Fakes.key;
import static logisticspipes.crafting.v3.Fakes.out;
import static logisticspipes.crafting.v3.Fakes.pattern;
import static logisticspipes.crafting.v3.Fakes.tool;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import logisticspipes.crafting.v3.exec.CraftingJob;
import logisticspipes.crafting.v3.exec.LedgerRecord;
import logisticspipes.crafting.v3.exec.SetState;
import logisticspipes.crafting.v3.exec.Ticket;
import logisticspipes.crafting.v3.exec.TicketState;
import logisticspipes.crafting.v3.model.BlockingMode;
import logisticspipes.crafting.v3.plan.Plan;

class CraftingDriverSimTest {

    /** Electric piston on one shared assembler: motor and piston recipes share the machine (plan section 11.3). */
    private Sim pistonWorld(BlockingMode mode) {
        Sim sim = new Sim();
        Sim.SimCrafter assembler = sim.crafter("assembler", mode, false);
        Sim.SimCrafter lathe = sim.crafter("lathe", mode, false);
        sim.pattern(
                assembler,
                pattern(
                        "piston",
                        out("piston", 1),
                        in("plate", 3),
                        in("cable", 2),
                        in("rod", 2),
                        in("gear", 1),
                        in("motor", 1)),
                10);
        sim.pattern(
                assembler,
                pattern("motor", out("motor", 1), in("rod", 2), in("magrod", 1), in("wire", 4), in("cable", 2)),
                10);
        sim.pattern(lathe, pattern("rod", out("rod", 2), in("ingot", 1)), 4);
        sim.provider("chest", "plate", 64).provider("chest", "cable", 64).provider("chest", "gear", 16)
                .provider("chest", "magrod", 16).provider("chest", "wire", 64).provider("chest", "ingot", 64);
        return sim;
    }

    private static boolean idle(Sim sim) {
        return sim.driver.jobs().isEmpty();
    }

    private static void assertClean(Sim sim) {
        assertTrue(sim.invariantFailures.isEmpty(), () -> sim.invariantFailures.toString());
        assertTrue(sim.driver.jobs().isEmpty(), "job should be gone");
        assertEquals(0, sim.driver.ledger().size(), () -> "records left: " + sim.driver.ledger().all());
        for (Sim.SimCrafter c : sim.crafters.values()) {
            if (!c.legacy) assertTrue(c.runtime.isEmpty(), c.name + " still holds " + c.runtime.contents());
        }
        assertTrue(sim.transit.isEmpty(), "items still travelling");
    }

    @Test
    void electricPistonOnSharedAssemblerCompletes() {
        Sim sim = pistonWorld(BlockingMode.BLOCKING);
        Plan plan = sim.plan("piston", 2);
        assertTrue(plan.canStart(), plan.issues::toString);
        sim.driver.startJob(plan, null);

        assertTrue(sim.runUntil(CraftingDriverSimTest::idle, 5000), "job did not finish");

        assertEquals(2L, sim.requesterGot.get(key("piston")));
        assertClean(sim);
    }

    @Test
    void smartModeStacksSamePattern() {
        Sim sim = pistonWorld(BlockingMode.SMART);
        sim.driver.startJob(sim.plan("piston", 8), null);
        assertTrue(sim.runUntil(CraftingDriverSimTest::idle, 8000), "job did not finish");
        assertEquals(8L, sim.requesterGot.get(key("piston")));
        assertClean(sim);
    }

    @Test
    void stockGoesStraightToRequester() {
        Sim sim = new Sim();
        sim.provider("chest", "ingot", 10);
        sim.driver.startJob(sim.plan("ingot", 7), null);
        assertTrue(sim.runUntil(CraftingDriverSimTest::idle, 200));
        assertEquals(7L, sim.requesterGot.get(key("ingot")));
        assertEquals(3L, sim.providers.get(Fakes.loc("chest")).get(key("ingot")));
        assertClean(sim);
    }

    @Test
    void reservedItemsCannotBeTakenByOthers() {
        Sim sim = pistonWorld(BlockingMode.BLOCKING);
        sim.driver.startJob(sim.plan("piston", 2), null);
        // the job reserved its plates; a second plan sees only the rest
        long free = sim.availableAt(Fakes.loc("chest"), key("plate"));
        assertEquals(64 - 6, free);
        Plan second = sim.plan("plate", 64);
        assertFalse(second.canStart(), "only 58 plates are free");
    }

    @Test
    void lostItemsInTransitAreReplaced() {
        Sim sim = pistonWorld(BlockingMode.BLOCKING);
        sim.lossChance = 0.15;
        sim.driver.startJob(sim.plan("piston", 2), null);
        assertTrue(
                sim.runUntil(CraftingDriverSimTest::idle, 20000),
                () -> "job did not finish: " + sim.driver.ledger().all());
        assertEquals(2L, sim.requesterGot.get(key("piston")));
        assertTrue(sim.invariantFailures.isEmpty(), () -> sim.invariantFailures.toString());
    }

    @Test
    void itemsLostFromProviderStallAndResumeWhenRefilled() {
        Sim sim = new Sim();
        Sim.SimCrafter c = sim.crafter("furnace", BlockingMode.BLOCKING, false);
        sim.pattern(c, pattern("ingot", out("ingot", 1), in("ore", 1)), 3);
        sim.provider("chest", "ore", 4);
        sim.driver.startJob(sim.plan("ingot", 4), Fakes.loc("player"));
        // someone empties the chest behind the driver's back
        sim.providers.get(Fakes.loc("chest")).put(key("ore"), 0L);

        sim.runUntil(s -> false, 200);
        CraftingJob job = sim.driver.jobs().iterator().next();
        boolean missing = job.tickets().stream().anyMatch(t -> sim.driver.displayState(t) == SetState.MISSING);
        assertTrue(missing, "should show missing");
        assertFalse(sim.messages.isEmpty(), "player is told about the stall");

        sim.provider("chest", "ore", 4);
        sim.driver.retryStalled();
        assertTrue(sim.runUntil(CraftingDriverSimTest::idle, 2000));
        assertEquals(4L, sim.requesterGot.get(key("ingot")));
        assertClean(sim);
    }

    @Test
    void unreachableProviderStallsUntilReconnected() {
        Sim sim = new Sim();
        Sim.SimCrafter c = sim.crafter("furnace", BlockingMode.BLOCKING, false);
        sim.pattern(c, pattern("ingot", out("ingot", 1), in("ore", 1)), 3);
        sim.provider("chest", "ore", 4);
        sim.driver.startJob(sim.plan("ingot", 4), null);
        sim.routes.block("chest", "furnace");

        sim.runUntil(s -> false, 200);
        CraftingJob job = sim.driver.jobs().iterator().next();
        assertTrue(job.tickets().stream().anyMatch(t -> sim.driver.displayState(t) == SetState.UNREACHABLE));

        sim.routes.blocked.clear();
        sim.driver.retryStalled();
        assertTrue(sim.runUntil(CraftingDriverSimTest::idle, 2000));
        assertEquals(4L, sim.requesterGot.get(key("ingot")));
        assertClean(sim);
    }

    @Test
    void cancelAtEveryStageLeavesNothingBehind() {
        for (int stopAt : new int[] { 0, 1, 3, 6, 10, 20, 40, 80, 150, 300 }) {
            Sim sim = pistonWorld(BlockingMode.BLOCKING);
            CraftingJob job = sim.driver.startJob(sim.plan("piston", 2), null);
            sim.runUntil(s -> false, stopAt);
            if (!sim.driver.cancelJob(job.id)) continue; // already finished
            assertTrue(sim.runUntil(CraftingDriverSimTest::idle, 5000), "wind-down did not finish at " + stopAt);
            // let anything still travelling land
            sim.runUntil(s -> s.transit.isEmpty(), 100);
            for (int i = 0; i < 40; i++) sim.step();
            assertClean(sim);
            assertTrue(sim.driver.reservations().reservedAt(Fakes.loc("chest")).isEmpty());
        }
    }

    @Test
    void saveAndLoadMidJobResumes() {
        for (int saveAt : new int[] { 2, 7, 15, 30, 60, 120 }) {
            Sim sim = pistonWorld(BlockingMode.BLOCKING);
            sim.driver.startJob(sim.plan("piston", 2), null);
            sim.runUntil(s -> false, saveAt);
            if (idle(sim)) continue;
            sim.saveAndReload();
            assertTrue(
                    sim.runUntil(CraftingDriverSimTest::idle, 8000),
                    "did not resume after save at " + saveAt + ": " + sim.driver.ledger().all());
            assertEquals(2L, sim.requesterGot.get(key("piston")), "save at " + saveAt);
            assertClean(sim);
        }
    }

    @Test
    void wrongCraftStallsThenCanBeCancelled() {
        Sim sim = new Sim();
        Sim.SimCrafter c = sim.crafter("furnace", BlockingMode.BLOCKING, false);
        sim.pattern(c, pattern("ingot", out("ingot", 1), in("ore", 1)), 3);
        sim.provider("chest", "ore", 4);
        c.machine.wrongOutput = key("slag");
        CraftingJob job = sim.driver.startJob(sim.plan("ingot", 2), null);

        sim.runUntil(s -> false, 300);
        assertTrue(job.tickets().stream().anyMatch(t -> t.state() == TicketState.WRONG_CRAFT));
        assertTrue(job.tickets().stream().anyMatch(t -> sim.driver.displayState(t) == SetState.WRONG_CRAFT));

        sim.driver.cancelJob(job.id);
        assertTrue(sim.runUntil(CraftingDriverSimTest::idle, 8000));
    }

    @Test
    void nonConsumedToolIsRequestedOnce() {
        Sim sim = new Sim();
        Sim.SimCrafter press = sim.crafter("press", BlockingMode.BLOCKING, false);
        sim.pattern(press, pattern("plate", out("plate", 1), in("ingot", 1), tool("mold")), 3);
        sim.provider("chest", "ingot", 16).provider("chest", "mold", 1);
        sim.driver.startJob(sim.plan("plate", 5), null);
        assertTrue(sim.runUntil(CraftingDriverSimTest::idle, 3000), () -> sim.driver.ledger().all().toString());
        assertEquals(5L, sim.requesterGot.get(key("plate")));
        assertTrue(sim.invariantFailures.isEmpty(), () -> sim.invariantFailures.toString());
    }

    @Test
    void legacyCrafterTakesPartInTheTree() {
        Sim sim = new Sim();
        Sim.SimCrafter old = sim.crafter("oldcrafter", BlockingMode.NONE, true);
        Sim.SimCrafter assembler = sim.crafter("assembler", BlockingMode.BLOCKING, false);
        sim.pattern(old, pattern("rod", out("rod", 2), in("ingot", 1)), 3);
        sim.pattern(assembler, pattern("frame", out("frame", 1), in("rod", 4)), 5);
        sim.provider("chest", "ingot", 10);
        Plan plan = sim.plan("frame", 2);
        assertTrue(
                plan.issues.stream()
                        .anyMatch(i -> i.kind() == logisticspipes.crafting.v3.plan.PlanIssue.Kind.LEGACY_CRAFTER));
        sim.driver.startJob(plan, null);
        assertTrue(sim.runUntil(CraftingDriverSimTest::idle, 3000), () -> sim.driver.ledger().all().toString());
        assertEquals(2L, sim.requesterGot.get(key("frame")));
        assertTrue(sim.invariantFailures.isEmpty(), () -> sim.invariantFailures.toString());
    }

    @Test
    void surplusGoesToStorageAtTheEnd() {
        Sim sim = new Sim();
        Sim.SimCrafter lathe = sim.crafter("lathe", BlockingMode.BLOCKING, false);
        sim.pattern(lathe, pattern("rod", out("rod", 2), in("ingot", 1)), 3);
        sim.provider("chest", "ingot", 10);
        sim.driver.startJob(sim.plan("rod", 3), null);
        assertTrue(sim.runUntil(CraftingDriverSimTest::idle, 2000));
        assertEquals(3L, sim.requesterGot.get(key("rod")));
        assertEquals(1L, sim.storage.get(key("rod")));
        assertClean(sim);
    }

    @Test
    void duplicateMessagesAreIgnored() {
        Sim sim = new Sim();
        Sim.SimCrafter c = sim.crafter("furnace", BlockingMode.BLOCKING, false);
        sim.pattern(c, pattern("ingot", out("ingot", 1), in("ore", 1)), 3);
        sim.provider("chest", "ore", 4);
        CraftingJob job = sim.driver.startJob(sim.plan("ingot", 3), null);
        for (int i = 0; i < 400 && !idle(sim); i++) {
            sim.step();
            for (Ticket t : job.tickets()) {
                if (t.isDelivery()) continue;
                // replays: repeated opens, commits and inserted reports must not change anything
                if (t.state() == TicketState.OPEN) c.port.open(t);
                if (t.state() == TicketState.COMMITTED) c.port.commit(t);
                if (t.state() == TicketState.INSERTED) sim.driver.onInserted(t.id);
            }
        }
        assertTrue(idle(sim));
        assertEquals(3L, sim.requesterGot.get(key("ingot")));
        assertClean(sim);
    }

    @Test
    void removedCrafterIsReplaced() {
        Sim sim = new Sim();
        Sim.SimCrafter a = sim.crafter("furnaceA", BlockingMode.BLOCKING, false);
        Sim.SimCrafter b = sim.crafter("furnaceB", BlockingMode.BLOCKING, false);
        sim.pattern(a, pattern("ingot", out("ingot", 1), in("ore", 1)), 30);
        sim.pattern(b, pattern("ingot", out("ingot", 1), in("ore", 1)), 30);
        sim.provider("chest", "ore", 20);
        sim.driver.startJob(sim.plan("ingot", 4), null);
        sim.runUntil(s -> false, 10);
        // break furnace A: its pipe drops everything
        a.loaded = false;
        a.runtime.clear();
        sim.driver.onCrafterRemoved(a.id);
        assertTrue(sim.runUntil(CraftingDriverSimTest::idle, 5000), () -> sim.driver.ledger().all().toString());
        assertEquals(4L, sim.requesterGot.get(key("ingot")));
        assertTrue(sim.invariantFailures.isEmpty(), () -> sim.invariantFailures.toString());
    }

    @Test
    void byproductOutputFeedsAnotherTicket() {
        Sim sim = new Sim();
        Sim.SimCrafter centrifuge = sim.crafter("centrifuge", BlockingMode.BLOCKING, false);
        Sim.SimCrafter mixer = sim.crafter("mixer", BlockingMode.BLOCKING, false);
        sim.pattern(centrifuge, pattern("dust", Arrays.asList(out("dust", 1), out("tiny", 1)), in("ore", 1)), 3);
        sim.pattern(mixer, pattern("alloy", out("alloy", 1), in("dust", 2), in("tiny", 2)), 3);
        sim.provider("chest", "ore", 10);
        sim.driver.startJob(sim.plan("alloy", 1), null);
        assertTrue(sim.runUntil(CraftingDriverSimTest::idle, 2000), () -> sim.driver.ledger().all().toString());
        assertEquals(1L, sim.requesterGot.get(key("alloy")));
        assertClean(sim);
        assertEquals(8L, sim.providers.get(Fakes.loc("chest")).get(key("ore")));
    }

    @Test
    void unlistedByproductDoesNotLockBlockingMode() {
        Sim sim = new Sim();
        Sim.SimCrafter macerator = sim.crafter("macerator", BlockingMode.BLOCKING, false);
        sim.pattern(macerator, pattern("dust", out("dust", 1), in("ore", 1)), 3);
        macerator.machine.extraOutput = key("stonedust");
        sim.provider("chest", "ore", 10);
        sim.driver.startJob(sim.plan("dust", 5), null);
        assertTrue(sim.runUntil(CraftingDriverSimTest::idle, 3000), () -> sim.driver.ledger().all().toString());
        assertEquals(5L, sim.requesterGot.get(key("dust")));
        // the byproducts were taken out and ended in storage, nothing was flagged as a wrong craft
        assertEquals(5L, sim.storage.get(key("stonedust")));
        assertClean(sim);
    }

    @Test
    void legacySatelliteGetsItsInputs() {
        Sim sim = new Sim();
        Sim.SimCrafter old = sim.crafter("oldcrafter", BlockingMode.NONE, true);
        // the legacy crafter's second ingredient goes to a satellite next to the same machine
        java.util.UUID satellite = Fakes.loc("satellite");
        sim.pattern(
                old,
                new logisticspipes.crafting.v3.model.Pattern(
                        "legacyframe",
                        java.util.Arrays.asList(
                                in("rod", 2),
                                new logisticspipes.crafting.v3.model.InputSpec(
                                        logisticspipes.crafting.v3.model.ResourceMatcher.exact(key("plate")),
                                        1,
                                        true,
                                        satellite)),
                        java.util.Arrays.asList(out("frame", 1)),
                        null),
                3);
        // the satellite is part of the legacy crafter: what arrives there goes into the same machine
        Sim.SimCrafter sat = sim.crafter("satellite", BlockingMode.NONE, true);
        sat.machineOverride = old.machine;
        sim.provider("chest", "rod", 4).provider("chest", "plate", 2);
        sim.driver.startJob(sim.plan("frame", 2), null);
        assertTrue(sim.runUntil(CraftingDriverSimTest::idle, 3000), () -> sim.driver.ledger().all().toString());
        assertEquals(2L, sim.requesterGot.get(key("frame")));
        assertTrue(sim.invariantFailures.isEmpty(), () -> sim.invariantFailures.toString());
    }

    @Test
    void unusedRecordsAreNeverBoundToUnknownTickets() {
        Sim sim = pistonWorld(BlockingMode.BLOCKING);
        sim.driver.startJob(sim.plan("piston", 3), null);
        for (int i = 0; i < 200; i++) {
            sim.step();
            for (LedgerRecord r : sim.driver.ledger().all()) {
                if (r.isBound()) assertTrue(sim.driver.ticket(r.ticket()) != null);
            }
        }
    }
}
