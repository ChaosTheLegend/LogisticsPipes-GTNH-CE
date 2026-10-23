package logisticspipes.crafting.v3;

import static logisticspipes.crafting.v3.Fakes.crafter;
import static logisticspipes.crafting.v3.Fakes.in;
import static logisticspipes.crafting.v3.Fakes.key;
import static logisticspipes.crafting.v3.Fakes.loc;
import static logisticspipes.crafting.v3.Fakes.out;
import static logisticspipes.crafting.v3.Fakes.pattern;
import static logisticspipes.crafting.v3.Fakes.tool;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import logisticspipes.crafting.v3.model.CrafterInfo;
import logisticspipes.crafting.v3.model.ResourceMatcher;
import logisticspipes.crafting.v3.plan.CraftingPlanner;
import logisticspipes.crafting.v3.plan.InputResolution;
import logisticspipes.crafting.v3.plan.Plan;
import logisticspipes.crafting.v3.plan.PlanIssue;
import logisticspipes.crafting.v3.plan.PlanNode;
import logisticspipes.crafting.v3.plan.PlanRequest;
import logisticspipes.crafting.v3.plan.SourceClaim;

class CraftingPlannerTest {

    private final Fakes.Registry registry = new Fakes.Registry();
    private final Fakes.Stock stock = new Fakes.Stock();
    private final Fakes.Routes routes = new Fakes.Routes();

    private Plan plan(String item, long qty) {
        return new CraftingPlanner(registry, stock, routes)
                .plan(new PlanRequest(ResourceMatcher.exact(key(item)), qty, loc("requester")));
    }

    private static PlanNode nodeFor(Plan plan, String output) {
        for (PlanNode n : plan.nodes.values()) {
            if (n.pattern.primaryOutput().equals(key(output))) return n;
        }
        return null;
    }

    private static InputResolution input(PlanNode node, String item) {
        for (InputResolution r : node.inputs) {
            if (r.spec.key().equals(key(item))) return r;
        }
        return null;
    }

    private static long sum(InputResolution res, Class<? extends SourceClaim> type) {
        long s = 0;
        for (SourceClaim c : res.sources) if (type.isInstance(c)) s += c.qty();
        return s;
    }

    @Test
    void electricPistonSharesRodsAndOrdersMotorFirst() {
        CrafterInfo assembler = crafter("assembler");
        CrafterInfo lathe = crafter("lathe");
        registry.add(
                pattern(
                        "piston",
                        out("piston", 1),
                        in("plate", 3),
                        in("cable", 2),
                        in("rod", 2),
                        in("gear", 1),
                        in("motor", 1)),
                assembler);
        registry.add(
                pattern("motor", out("motor", 1), in("rod", 2), in("magrod", 1), in("wire", 4), in("cable", 2)),
                assembler);
        registry.add(pattern("rod", out("rod", 2), in("ingot", 1)), lathe);
        stock.put("chest", "plate", 64).put("chest", "cable", 64).put("chest", "gear", 8).put("chest", "magrod", 8)
                .put("chest", "wire", 64).put("chest", "ingot", 64);

        Plan plan = plan("piston", 2);

        assertTrue(plan.canStart(), plan.issues::toString);
        PlanNode piston = nodeFor(plan, "piston");
        PlanNode motor = nodeFor(plan, "motor");
        PlanNode rod = nodeFor(plan, "rod");
        assertEquals(2, piston.crafts);
        assertEquals(2, motor.crafts);
        // 4 rods for pistons + 4 for motors, yield 2: one shared rod node with 4 crafts
        assertEquals(4, rod.crafts);
        assertEquals(1, plan.nodes.values().stream().filter(n -> n.pattern.id().equals("rod")).count());
        assertEquals(8, rod.totalAllocated(key("rod")));
        assertTrue(motor.height < piston.height);
        assertTrue(rod.height < motor.height);
        assertEquals(2, piston.totalAllocated(key("piston")));
        assertEquals(4, plan.rawTotals.get(key("ingot")));
    }

    @Test
    void aggregationRoundsUpAndKeepsSurplus() {
        CrafterInfo c = crafter("c");
        registry.add(pattern("top", out("top", 1), in("a", 1), in("b", 1), in("d", 1)), c);
        registry.add(pattern("a", out("a", 1), in("x", 1)), c);
        registry.add(pattern("b", out("b", 1), in("x", 1)), c);
        registry.add(pattern("d", out("d", 1), in("x", 1)), c);
        registry.add(pattern("x", out("x", 2), in("raw", 1)), c);
        stock.put("chest", "raw", 10);

        Plan plan = plan("top", 1);

        PlanNode x = nodeFor(plan, "x");
        assertEquals(2, x.crafts);
        assertEquals(1L, x.surplus.get(key("x")));
        assertEquals(3, x.totalAllocated(key("x")));
    }

    @Test
    void partialStockIsUsedBeforeCrafting() {
        registry.add(pattern("x", out("x", 1), in("raw", 1)), crafter("c"));
        stock.put("chest", "x", 40).put("chest", "raw", 100);

        Plan plan = plan("x", 100);

        assertEquals(40, sum(plan.root, SourceClaim.Provider.class));
        assertEquals(60, sum(plan.root, SourceClaim.Craft.class));
        assertEquals(60, nodeFor(plan, "x").crafts);
    }

    @Test
    void recipeLoopUsesAlternativePattern() {
        CrafterInfo c = crafter("c");
        // block <-> ingot loop; ingot also smelts from ore
        registry.add(pattern("block", out("block", 1), in("ingot", 9)), c);
        registry.add(pattern("ingotFromBlock", out("ingot", 9), in("block", 1)), c);
        registry.add(pattern("ingotFromOre", out("ingot", 1), in("ore", 1)), c);
        stock.put("chest", "ore", 64);

        Plan plan = plan("block", 1);

        assertTrue(plan.canStart(), plan.issues::toString);
        assertEquals("ingotFromOre", nodeFor(plan, "ingot").pattern.id());
        assertEquals(9, nodeFor(plan, "ingot").crafts);
    }

    @Test
    void loopWithoutAlternativeIsReported() {
        CrafterInfo c = crafter("c");
        registry.add(pattern("block", out("block", 1), in("ingot", 9)), c);
        registry.add(pattern("ingotFromBlock", out("ingot", 9), in("block", 1)), c);

        Plan plan = plan("block", 1);

        assertFalse(plan.canStart());
        assertTrue(plan.hasIssues(PlanIssue.Kind.CYCLE) || plan.hasIssues(PlanIssue.Kind.MISSING));
    }

    @Test
    void byproductCycleIsRefused() {
        CrafterInfo c = crafter("c");
        // centrifuge makes dust and byproduct "slag"; dust prep itself needs slag. The centrifuge's slag surplus
        // must not feed its own ancestor chain.
        registry.add(pattern("top", out("top", 1), in("dust", 1)), c);
        registry.add(pattern("dust", Arrays.asList(out("dust", 1), out("slag", 1)), in("prep", 1)), c);
        registry.add(pattern("prep", out("prep", 1), in("slag", 1)), c);
        stock.put("chest", "slag", 5);

        Plan plan = plan("top", 1);

        PlanNode prep = nodeFor(plan, "prep");
        // dust needs prep, so dust's surplus slag feeding prep would be a cycle
        assertEquals(0, sum(input(prep, "slag"), SourceClaim.Surplus.class));
        assertEquals(1, sum(input(prep, "slag"), SourceClaim.Provider.class));
        assertEquals(1L, nodeFor(plan, "dust").surplus.get(key("slag")));
    }

    @Test
    void byproductFeedsIndependentConsumer() {
        CrafterInfo c = crafter("c");
        registry.add(pattern("top", out("top", 1), in("dust", 1), in("slag", 1)), c);
        registry.add(pattern("dust", Arrays.asList(out("dust", 1), out("slag", 1)), in("prep", 1)), c);
        registry.add(pattern("prep", out("prep", 1), in("raw", 1)), c);
        stock.put("chest", "raw", 5);

        Plan plan = plan("top", 1);

        assertTrue(plan.canStart(), plan.issues::toString);
        // slag's only recipe is the dust node itself, so the one dust craft covers both outputs
        assertEquals(1, nodeFor(plan, "dust").crafts);
        InputResolution slag = input(nodeFor(plan, "top"), "slag");
        assertEquals(1, slag.resolvedQty());
        assertEquals(0, sum(slag, SourceClaim.Provider.class));
        assertEquals(1, plan.rawTotals.get(key("raw")));
    }

    @Test
    void unreachableProviderIsNotClaimed() {
        registry.add(pattern("x", out("x", 1), in("raw", 1)), crafter("c"));
        stock.put("far", "raw", 10).put("near", "raw", 10);
        routes.block("far", "c");

        Plan plan = plan("x", 5);

        InputResolution raw = input(nodeFor(plan, "x"), "raw");
        for (SourceClaim claim : raw.sources) {
            assertEquals(loc("near"), ((SourceClaim.Provider) claim).provider());
        }
        assertEquals(5, raw.resolvedQty());
    }

    @Test
    void onlyUnreachableStockIsReportedAsUnreachable() {
        registry.add(pattern("x", out("x", 1), in("raw", 1)), crafter("c"));
        stock.put("far", "raw", 10);
        routes.block("far", "c");

        Plan plan = plan("x", 5);

        assertTrue(plan.hasIssues(PlanIssue.Kind.UNREACHABLE));
        assertFalse(plan.hasIssues(PlanIssue.Kind.MISSING));
        assertTrue(plan.canStart());
    }

    @Test
    void missingItemsBlockStart() {
        registry.add(pattern("x", out("x", 1), in("raw", 1)), crafter("c"));

        Plan plan = plan("x", 5);

        assertTrue(plan.hasIssues(PlanIssue.Kind.MISSING));
        assertFalse(plan.canStart());
    }

    @Test
    void mostConstrainedConsumerGetsItsOnlySource() {
        CrafterInfo a = crafter("a");
        CrafterInfo b = crafter("b");
        registry.add(pattern("top", out("top", 1), in("pa", 1), in("pb", 1)), crafter("t"));
        registry.add(pattern("pa", out("pa", 1), in("raw", 10)), a);
        registry.add(pattern("pb", out("pb", 1), in("raw", 10)), b);
        // a can only reach chest1; b can reach both. Total stock is exactly enough.
        stock.put("chest1", "raw", 10).put("chest2", "raw", 10);
        routes.block("chest2", "a");

        Plan plan = plan("top", 1);

        assertTrue(plan.canStart(), plan.issues::toString);
        InputResolution aRaw = input(nodeFor(plan, "pa"), "raw");
        assertEquals(10, aRaw.resolvedQty());
        assertEquals(loc("chest1"), ((SourceClaim.Provider) aRaw.sources.get(0)).provider());
    }

    @Test
    void nonConsumedInputsAreNotMultiplied() {
        registry.add(pattern("plate", out("plate", 1), in("ingot", 1), tool("mold")), crafter("press"));
        stock.put("chest", "ingot", 64).put("chest", "mold", 1);

        Plan plan = plan("plate", 32);

        PlanNode plate = nodeFor(plan, "plate");
        assertEquals(32, input(plate, "ingot").totalQty);
        assertEquals(1, input(plate, "mold").totalQty);
        assertTrue(plan.canStart());
    }

    @Test
    void heightsIncludeSurplusEdges() {
        CrafterInfo c = crafter("c");
        // a is made by "split", which also leaves a surplus b. mid needs b and takes that surplus instead of using
        // b's own recipe, so mid must sit above split even though there is no craft edge between them.
        registry.add(pattern("top", out("top", 1), in("mid", 1), in("a", 1)), c);
        registry.add(pattern("mid", out("mid", 1), in("b", 1)), c);
        registry.add(pattern("bdirect", out("b", 1), in("raw", 1)), c);
        registry.add(pattern("split", Arrays.asList(out("a", 1), out("b", 1)), in("raw", 1)), c);
        stock.put("chest", "raw", 10);

        Plan plan = plan("top", 1);

        PlanNode split = nodeFor(plan, "a");
        PlanNode mid = nodeFor(plan, "mid");
        assertNotNull(mid);
        assertEquals(1, sum(input(mid, "b"), SourceClaim.Surplus.class));
        assertNull(nodeFor(plan, "b"), "b's own recipe is not needed");
        assertEquals(0, split.height);
        assertEquals(1, mid.height);
        assertEquals(2, nodeFor(plan, "top").height);
    }

    @Test
    void requesterOnlyNeedsStock() {
        stock.put("chest", "x", 10);

        Plan plan = plan("x", 5);

        assertTrue(plan.nodes.isEmpty());
        assertEquals(5, sum(plan.root, SourceClaim.Provider.class));
        assertNull(plan.error);
    }

    @Test
    void crafterThatCannotReachConsumerIsSkipped() {
        CrafterInfo far = crafter("far", 10);
        CrafterInfo near = crafter("near", 0);
        registry.add(pattern("x", out("x", 1), in("raw", 1)), far, near);
        stock.put("chest", "raw", 10);
        routes.block("far", "requester");

        Plan plan = plan("x", 1);

        assertEquals(near, nodeFor(plan, "x").crafter);
    }

    @Test
    void craftOnlyIgnoresStockOfTheRequestedItem() {
        registry.add(pattern("x", out("x", 1), in("raw", 1)), crafter("c"));
        stock.put("chest", "x", 40).put("chest", "raw", 100);

        Plan plan = new CraftingPlanner(registry, stock, routes)
                .plan(new PlanRequest(ResourceMatcher.exact(key("x")), 10, loc("requester"), true));

        assertEquals(0, sum(plan.root, SourceClaim.Provider.class));
        assertEquals(10, nodeFor(plan, "x").crafts);
    }

    @Test
    void satelliteInputsAreCheckedAgainstTheSatellite() {
        CrafterInfo c = crafter("c");
        registry.add(
                pattern(
                        "x",
                        out("x", 1),
                        in("raw", 1),
                        new logisticspipes.crafting.v3.model.InputSpec(
                                ResourceMatcher.exact(key("fluid")),
                                1,
                                true,
                                loc("sat"))),
                c);
        stock.put("tank", "fluid", 10).put("chest", "raw", 10);
        // the tank reaches the satellite but not the crafter itself
        routes.block("tank", "c");

        Plan plan = plan("x", 2);

        assertTrue(plan.canStart(), plan.issues::toString);
        assertFalse(plan.hasIssues(PlanIssue.Kind.UNREACHABLE));
    }

    @Test
    void nodeCapAbortsPlanning() {
        CrafterInfo c = crafter("c");
        for (int i = 0; i < 50; i++) {
            registry.add(pattern("p" + i, out("i" + i, 1), in("i" + (i + 1), 1)), c);
        }
        Plan plan = new CraftingPlanner(registry, stock, routes, 10, 5000)
                .plan(new PlanRequest(ResourceMatcher.exact(key("i0")), 1, loc("requester")));
        assertTrue(plan.isFailed());
    }
}
