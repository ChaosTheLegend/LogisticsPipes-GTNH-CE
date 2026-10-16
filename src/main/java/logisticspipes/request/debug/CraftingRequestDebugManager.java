package logisticspipes.request.debug;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.RequestTree;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.LinkedLogisticsOrderList;

public final class CraftingRequestDebugManager {

    private static final int MAX_SNAPSHOTS = 24;
    private static final int MAX_EVENTS = 60000;
    private static final Pattern PIPE_MESSAGE_PATTERN = Pattern.compile("^(pipe=\\([^)]*\\))\\s+(.*)$");
    private static final Pattern TARGET_SLOT_PATTERN = Pattern
            .compile("PatternTargetInformation\\[patternSlot=(-?\\d+)(?:, inputSlot=-?\\d+)?(?:,.*)?]");
    private static final Pattern STAGED_START_PATTERN = Pattern
            .compile("^staged craft start promise=(.*?) amount=(\\d+) request=.* info=(.*?) branch=.*$");
    private static final Pattern STAGED_REGISTERED_PATTERN = Pattern
            .compile("^staged craft registered slot=(\\d+) remainingSets=(\\d+) ingredientBranches=(\\d+).*$");
    private static final Pattern ORDER_CREATED_PATTERN = Pattern
            .compile("^create (item|fluid) output order (?:item|fluid)=(.*?) amount=(\\d+) destination=(.*?) info=.*$");
    private static final Pattern SETS_REQUEST_PATTERN = Pattern.compile(
            "^request ingredients slot=(\\d+) remainingSets=(\\d+) orderableSets=(\\d+) branchSets=(\\d+) selectedSets=(\\d+)$");
    private static final Pattern INGREDIENT_REQUEST_PATTERN = Pattern.compile(
            "^order requested ingredient slot=(\\d+) ingredient=(.*?) target=(.*?) requested=(\\d+) amountPerSet=(\\d+)$");
    private static final Pattern COMPLETED_SLOT_PATTERN = Pattern
            .compile("^request ingredients slot=(\\d+) completed staged order after request$");
    private static final Pattern COORDINATES_PATTERN = Pattern.compile("(\\(-?\\d+,\\s*-?\\d+,\\s*-?\\d+\\))");
    private static final Deque<RequestSnapshot> SNAPSHOTS = new ArrayDeque<>();
    private static final Deque<DebugEvent> EVENTS = new ArrayDeque<>();
    private static int nextRequestId = 1;
    private static int nextEventId = 1;

    private CraftingRequestDebugManager() {}

    /**
     * Records a request-tree state immediately, before later staged crafting ticks mutate related order state.
     */
    public static void record(String title, RequestTree tree, LinkedLogisticsOrderList orders) {
        int requestId;
        synchronized (SNAPSHOTS) {
            requestId = nextRequestId++;
        }
        RequestSnapshot snapshot = new RequestSnapshot(
                requestId,
                title,
                System.currentTimeMillis(),
                tree == null ? "<no request tree>" : tree.toString(),
                formatOrderList(orders));
        synchronized (SNAPSHOTS) {
            SNAPSHOTS.addFirst(snapshot);
            while (SNAPSHOTS.size() > MAX_SNAPSHOTS) {
                SNAPSHOTS.removeLast();
            }
        }
        recordEvent("REQUEST", "request#" + requestId + " " + title + " " + summarizeOrderList(orders));
    }

    public static void recordEvent(String category, String message) {
        synchronized (EVENTS) {
            DebugEvent event = new DebugEvent(
                    nextEventId++,
                    System.currentTimeMillis(),
                    logisticspipes.proxy.MainProxy.getGlobalTick(),
                    category == null || category.isEmpty() ? "EVENT" : category,
                    message == null ? "" : message);
            EVENTS.addFirst(event);
            while (EVENTS.size() > MAX_EVENTS) {
                EVENTS.removeLast();
            }
        }
    }

    public static void recordPipeEvent(PipeItemsPatternCraftingLogistics pipe, String category, String message) {
        recordEvent(category, describePipe(pipe) + " " + message);
    }

    /**
     * Builds the text that is shown in the client-side JFrame.
     */
    public static String buildSnapshot() {
        StringBuilder out = new StringBuilder();
        out.append("Crafting Request Debug Snapshot\n");
        out.append("Generated: ").append(formatTime(System.currentTimeMillis())).append("\n\n");
        appendSummary(out);
        appendCraftingFlow(out);
        appendTimeline(out);
        appendVerboseTimeline(out);
        appendRecordedRequests(out);
        appendActivePatternPipes(out);
        return out.toString();
    }

    private static void appendSummary(StringBuilder out) {
        List<RequestSnapshot> snapshots;
        List<DebugEvent> events;
        synchronized (SNAPSHOTS) {
            snapshots = new ArrayList<>(SNAPSHOTS);
        }
        synchronized (EVENTS) {
            events = new ArrayList<>(EVENTS);
        }
        out.append("== Summary ==\n");
        out.append("Recorded request snapshots: ").append(snapshots.size()).append("/").append(MAX_SNAPSHOTS)
                .append("\n");
        out.append("Timeline events: ").append(events.size()).append("/").append(MAX_EVENTS).append("\n");
        out.append("Active pattern crafting pipes: ").append(countActivePatternPipes()).append("\n");
        if (!events.isEmpty()) {
            DebugEvent newest = events.get(0);
            out.append("Latest event: #").append(newest.id).append(" ").append(newest.category).append(" ")
                    .append(newest.message).append("\n");
        }
        out.append("\n");
    }

    private static void appendCraftingFlow(StringBuilder out) {
        CraftingFlowBuilder flow = new CraftingFlowBuilder();
        for (DebugEvent event : eventsOldestFirst()) {
            flow.accept(event);
        }
        out.append("== Crafting Flow ==\n");
        flow.append(out);
        out.append("\n");
    }

    private static void appendTimeline(StringBuilder out) {
        List<DebugEvent> events = eventsOldestFirst();
        out.append("== Timeline ==\n");
        if (events.isEmpty()) {
            out.append("No crafting flow events have been recorded yet.\n\n");
            return;
        }
        int hidden = 0;
        for (DebugEvent event : events) {
            if (isCompactTimelineEvent(event)) {
                appendEventLine(out, event);
            } else {
                hidden++;
            }
        }
        if (hidden > 0) {
            out.append("(").append(hidden).append(" verbose events hidden here; see Verbose Timeline.)\n");
        }
        out.append("\n");
    }

    private static void appendVerboseTimeline(StringBuilder out) {
        out.append("== Verbose Timeline ==\n");
        List<DebugEvent> events = eventsOldestFirst();
        if (events.isEmpty()) {
            out.append("No crafting flow events have been recorded yet.\n\n");
            return;
        }
        for (DebugEvent event : events) {
            appendEventLine(out, event);
        }
        out.append("\n");
    }

    private static List<DebugEvent> eventsOldestFirst() {
        List<DebugEvent> events;
        synchronized (EVENTS) {
            events = new ArrayList<>(EVENTS);
        }
        Collections.reverse(events);
        return events;
    }

    private static void appendEventLine(StringBuilder out, DebugEvent event) {
        out.append("#").append(event.id).append(" tick=").append(event.tick).append(" ").append(formatTime(event.time))
                .append(" [").append(event.category).append("] ").append(event.message).append("\n");
    }

    private static boolean isCompactTimelineEvent(DebugEvent event) {
        PipeMessage pipeMessage = splitPipeMessage(event.message);
        String message = pipeMessage.body;
        if ("REQUEST".equals(event.category)) {
            return message.startsWith("request#") || message.contains("selectedSets=0")
                    || message.startsWith("lost retry");
        }
        if ("SCHED".equals(event.category)) {
            return message.contains("selectedSets=0") || message.contains("paused: no selectable sets")
                    || message.contains("requested no sets")
                    || message.contains("skipped:");
        }
        if ("STAGED".equals(event.category)) {
            return message.startsWith("staged craft start") || message.startsWith("staged craft rejected");
        }
        return "FLOW".equals(event.category) || "EXTRA".equals(event.category);
    }

    private static void appendRecordedRequests(StringBuilder out) {
        List<RequestSnapshot> snapshots;
        synchronized (SNAPSHOTS) {
            snapshots = new ArrayList<>(SNAPSHOTS);
        }
        out.append("== Recorded Request Trees ==\n");
        if (snapshots.isEmpty()) {
            out.append("No request tree has been recorded yet.\n\n");
            return;
        }
        for (int i = 0; i < snapshots.size(); i++) {
            RequestSnapshot snapshot = snapshots.get(i);
            out.append("-- Request #").append(snapshot.id).append(" / snapshot ").append(i).append(" (")
                    .append(i == 0 ? "newest" : "history").append(") --\n");
            out.append("Title: ").append(snapshot.title).append("\n");
            out.append("Captured: ").append(formatTime(snapshot.time)).append("\n\n");
            out.append("Request tree:\n");
            out.append(snapshot.treeText).append("\n");
            out.append("Orders created from tree:\n");
            out.append(snapshot.ordersText).append("\n");
        }
    }

    private static int countActivePatternPipes() {
        if (SimpleServiceLocator.routerManager == null) {
            return 0;
        }
        int count = 0;
        for (IRouter router : SimpleServiceLocator.routerManager.getRouters()) {
            if (router == null || !router.isValidCache()) {
                continue;
            }
            if (router.getCachedPipe() instanceof PipeItemsPatternCraftingLogistics) {
                count++;
            }
        }
        return count;
    }

    private static void appendActivePatternPipes(StringBuilder out) {
        out.append("== Active Pattern Crafting Pipes ==\n");
        if (SimpleServiceLocator.routerManager == null) {
            out.append("Router manager is not available.\n");
            return;
        }
        int count = 0;
        for (IRouter router : SimpleServiceLocator.routerManager.getRouters()) {
            if (router == null || !router.isValidCache()) {
                continue;
            }
            CoreRoutedPipe pipe = router.getCachedPipe();
            if (!(pipe instanceof PipeItemsPatternCraftingLogistics)) {
                continue;
            }
            count++;
            try {
                ((PipeItemsPatternCraftingLogistics) pipe).getPatternModule().appendDebugState(out);
            } catch (RuntimeException e) {
                out.append("Pattern crafting pipe at router ").append(router.getSimpleID())
                        .append(" could not be dumped: ").append(e.getClass().getName()).append(": ")
                        .append(e.getMessage()).append("\n");
            }
            out.append("\n");
        }
        if (count == 0) {
            out.append("No pattern crafting pipes are currently registered.\n");
        }
    }

    private static String formatOrderList(LinkedLogisticsOrderList orders) {
        if (orders == null) {
            return "<no order list>\n";
        }
        StringBuilder out = new StringBuilder();
        appendOrderList(out, orders, "", "root");
        return out.toString();
    }

    private static void appendOrderList(StringBuilder out, LinkedLogisticsOrderList orders, String prefix,
            String label) {
        out.append(prefix).append(label).append(" orders=").append(orders.size()).append(" subtrees=")
                .append(orders.getSubOrders().size()).append(" rootSize=").append(orders.getTreeRootSize())
                .append("\n");
        for (IOrderInfoProvider order : orders) {
            appendOrder(out, order, prefix + "  ");
        }
        for (int i = 0; i < orders.getSubOrders().size(); i++) {
            appendOrderList(out, orders.getSubOrders().get(i), prefix + "  ", "subtree " + i);
        }
    }

    private static void appendOrder(StringBuilder out, IOrderInfoProvider order, String prefix) {
        if (order == null) {
            out.append(prefix).append("- <null order>\n");
            return;
        }
        out.append(prefix).append("- ").append(order.getType()).append(" ").append(order.getAsDisplayItem())
                .append(" -> router ").append(order.getRouterId());
        if (order.isInProgress()) {
            out.append(" in-progress");
        }
        if (order.isFinished()) {
            out.append(" finished");
        }
        out.append("\n");
    }

    private static String summarizeOrderList(LinkedLogisticsOrderList orders) {
        if (orders == null) {
            return "orders=<none>";
        }
        return "orders=" + orders
                .size() + " subtrees=" + orders.getSubOrders().size() + " rootSize=" + orders.getTreeRootSize();
    }

    private static String describePipe(PipeItemsPatternCraftingLogistics pipe) {
        if (pipe == null) {
            return "pipe=<none>";
        }
        String router = "<no-router>";
        try {
            router = String.valueOf(pipe.getRouter().getSimpleID());
        } catch (RuntimeException ignored) {}
        return "pipe=(" + pipe.getX() + "," + pipe.getY() + "," + pipe.getZ() + " router=" + router + ")";
    }

    private static String formatTime(long time) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(time));
    }

    public static void clear() {
        synchronized (SNAPSHOTS) {
            SNAPSHOTS.clear();
        }

        synchronized (EVENTS) {
            EVENTS.clear();
        }
    }

    private static PipeMessage splitPipeMessage(String message) {
        Matcher matcher = PIPE_MESSAGE_PATTERN.matcher(message == null ? "" : message);
        if (matcher.matches()) {
            return new PipeMessage(matcher.group(1), matcher.group(2));
        }
        return new PipeMessage(null, message == null ? "" : message);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int parseTargetSlot(String info) {
        Matcher matcher = TARGET_SLOT_PATTERN.matcher(info == null ? "" : info);
        return matcher.find() ? parseInt(matcher.group(1), -1) : -1;
    }

    private static String simplifyTarget(String target) {
        if (target == null || target.equals("null")) {
            return "requester";
        }
        String coordinates = "";
        Matcher coordinateMatcher = COORDINATES_PATTERN.matcher(target);
        if (coordinateMatcher.find()) {
            coordinates = " " + coordinateMatcher.group(1);
        }
        if (target.startsWith("ModuleItemCrafting@")) {
            return "pattern module" + coordinates;
        }
        if (target.contains("PipeBlockRequestTable")) {
            return "request table" + coordinates;
        }
        int at = target.indexOf('@');
        String type = at >= 0 ? target.substring(0, at) : target;
        int dot = type.lastIndexOf('.');
        if (dot >= 0) {
            type = type.substring(dot + 1);
        }
        return type + coordinates;
    }

    private static String formatBriefEvent(DebugEvent event, String body) {
        return "tick " + event.tick + ": " + body;
    }

    private static class CraftingFlowBuilder {

        private final List<String> requests = new ArrayList<>();
        private final List<FlowCraft> roots = new ArrayList<>();
        private final List<FlowCraft> crafts = new ArrayList<>();
        private final List<String> notableEvents = new ArrayList<>();

        private void accept(DebugEvent event) {
            PipeMessage pipeMessage = splitPipeMessage(event.message);
            String message = pipeMessage.body;
            if ("REQUEST".equals(event.category) && message.startsWith("request#")) {
                requests.add(formatBriefEvent(event, message));
                return;
            }
            if ("STAGED".equals(event.category)) {
                if (acceptStagedEvent(event, pipeMessage, message)) {
                    return;
                }
            }
            if ("ORDER".equals(event.category)) {
                if (acceptOrderEvent(pipeMessage, message)) {
                    return;
                }
            }
            if ("REQUEST".equals(event.category) || "SCHED".equals(event.category)) {
                if (acceptRequestEvent(pipeMessage, message)) {
                    return;
                }
            }
            if ("FLOW".equals(event.category) || "EXTRA".equals(event.category)) {
                notableEvents.add(formatBriefEvent(event, pipeMessage.messageWithPipe()));
            }
        }

        private boolean acceptStagedEvent(DebugEvent event, PipeMessage pipeMessage, String message) {
            Matcher start = STAGED_START_PATTERN.matcher(message);
            if (start.matches()) {
                int amount = parseInt(start.group(2), 0);
                int parentSlot = parseTargetSlot(start.group(3));
                FlowCraft parent = parentSlot >= 0 ? findLatestCraftBySlot(pipeMessage.pipeKey, parentSlot) : null;
                FlowCraft craft = new FlowCraft(pipeMessage.pipeKey, event.tick, start.group(1), amount, parentSlot);
                if (parent == null) {
                    roots.add(craft);
                } else {
                    parent.children.add(craft);
                }
                crafts.add(craft);
                return true;
            }
            Matcher registered = STAGED_REGISTERED_PATTERN.matcher(message);
            if (registered.matches()) {
                FlowCraft craft = findLastPendingCraft(pipeMessage.pipeKey);
                if (craft == null) {
                    return false;
                }
                craft.patternSlot = parseInt(registered.group(1), -1);
                craft.remainingSets = parseInt(registered.group(2), -1);
                craft.ingredientBranches = parseInt(registered.group(3), -1);
                craft.ingredientsComplete = craft.remainingSets <= 0 || craft.ingredientBranches == 0;
                return true;
            }
            if (message.startsWith("staged craft rejected")) {
                notableEvents.add(formatBriefEvent(event, pipeMessage.messageWithPipe()));
                return true;
            }
            return false;
        }

        private boolean acceptOrderEvent(PipeMessage pipeMessage, String message) {
            Matcher order = ORDER_CREATED_PATTERN.matcher(message);
            if (!order.matches()) {
                return false;
            }
            FlowCraft craft = findLastPendingCraft(pipeMessage.pipeKey);
            if (craft == null) {
                return false;
            }
            craft.target = simplifyTarget(order.group(4));
            return true;
        }

        private boolean acceptRequestEvent(PipeMessage pipeMessage, String message) {
            Matcher sets = SETS_REQUEST_PATTERN.matcher(message);
            if (sets.matches()) {
                FlowCraft craft = findLatestCraftBySlot(pipeMessage.pipeKey, parseInt(sets.group(1), -1));
                if (craft == null) {
                    return false;
                }
                craft.setRequests.add(
                        new FlowSetRequest(
                                parseInt(sets.group(2), 0),
                                parseInt(sets.group(3), 0),
                                parseInt(sets.group(4), 0),
                                parseInt(sets.group(5), 0)));
                return true;
            }
            Matcher ingredient = INGREDIENT_REQUEST_PATTERN.matcher(message);
            if (ingredient.matches()) {
                int requested = parseInt(ingredient.group(4), 0);
                if (requested <= 0) {
                    return true;
                }
                FlowCraft craft = findLatestCraftBySlot(pipeMessage.pipeKey, parseInt(ingredient.group(1), -1));
                if (craft == null) {
                    return false;
                }
                String target = ingredient.group(3);
                craft.ingredients.add(
                        new FlowIngredientRequest(
                                ingredient.group(2),
                                target == null || target.equals("null") ? "local buffer" : simplifyTarget(target),
                                requested,
                                parseInt(ingredient.group(5), 0)));
                return true;
            }
            Matcher completed = COMPLETED_SLOT_PATTERN.matcher(message);
            if (completed.matches()) {
                FlowCraft craft = findLatestCraftBySlot(pipeMessage.pipeKey, parseInt(completed.group(1), -1));
                if (craft != null) {
                    craft.ingredientsComplete = true;
                    return true;
                }
            }
            return message.startsWith("branch item request") || message.startsWith("branch fluid request");
        }

        private FlowCraft findLastPendingCraft(String pipeKey) {
            for (int i = crafts.size() - 1; i >= 0; i--) {
                FlowCraft craft = crafts.get(i);
                if (samePipe(pipeKey, craft.pipeKey) && craft.patternSlot < 0) {
                    return craft;
                }
            }
            return null;
        }

        private FlowCraft findLatestCraftBySlot(String pipeKey, int patternSlot) {
            for (int i = crafts.size() - 1; i >= 0; i--) {
                FlowCraft craft = crafts.get(i);
                if (samePipe(pipeKey, craft.pipeKey) && craft.patternSlot == patternSlot
                        && (!craft.ingredientsComplete)) {
                    return craft;
                }
            }
            return null;
        }

        private boolean samePipe(String left, String right) {
            if (left == null) {
                return right == null;
            }
            return left.equals(right);
        }

        private void append(StringBuilder out) {
            if (!requests.isEmpty()) {
                out.append("Requests:\n");
                for (String request : requests) {
                    out.append("- ").append(request).append("\n");
                }
                out.append("\n");
            }
            if (roots.isEmpty()) {
                out.append("No staged pattern crafting flow has been recorded yet.\n");
            } else {
                out.append("Staged pattern crafts:\n");
                for (FlowCraft root : roots) {
                    root.append(out, "");
                }
            }
            if (!notableEvents.isEmpty()) {
                out.append("\nNotable extraction/extra events:\n");
                for (String event : notableEvents) {
                    out.append("- ").append(event).append("\n");
                }
            }
        }
    }

    private static class FlowCraft {

        private final String pipeKey;
        private final int tick;
        private final String output;
        private final int amount;
        private final int parentSlot;
        private final List<FlowSetRequest> setRequests = new ArrayList<>();
        private final List<FlowIngredientRequest> ingredients = new ArrayList<>();
        private final List<FlowCraft> children = new ArrayList<>();
        private String target;
        private int patternSlot = -1;
        private int remainingSets = -1;
        private int ingredientBranches = -1;
        private boolean ingredientsComplete = false;

        private FlowCraft(String pipeKey, int tick, String output, int amount, int parentSlot) {
            this.pipeKey = pipeKey;
            this.tick = tick;
            this.output = output;
            this.amount = amount;
            this.parentSlot = parentSlot;
        }

        private void append(StringBuilder out, String prefix) {
            out.append(prefix).append("- ").append(amount).append("x ").append(output);
            if (patternSlot >= 0) {
                out.append(" [slot ").append(patternSlot).append("]");
            }
            if (parentSlot >= 0) {
                out.append(" for parent slot ").append(parentSlot);
            } else if (target != null) {
                out.append(" -> ").append(target);
            }
            out.append(" (tick ").append(tick);
            if (pipeKey != null) {
                out.append(", ").append(pipeKey);
            }
            out.append(")\n");
            appendSetSummary(out, prefix + "  ");
            appendIngredients(out, prefix + "  ");
            for (FlowCraft child : children) {
                child.append(out, prefix + "  ");
            }
        }

        private void appendSetSummary(StringBuilder out, String prefix) {
            if (remainingSets >= 0 || ingredientBranches >= 0) {
                out.append(prefix).append("pattern sets: remaining=").append(remainingSets).append(", branches=")
                        .append(ingredientBranches).append(ingredientsComplete ? ", ingredient requests complete" : "")
                        .append("\n");
            }
            for (FlowSetRequest request : setRequests) {
                out.append(prefix).append("requested sets: selected=").append(request.selectedSets).append("/")
                        .append(request.remainingSets).append(" capacity=").append(request.orderableSets)
                        .append(" branch=").append(request.branchSets).append("\n");
            }
        }

        private void appendIngredients(StringBuilder out, String prefix) {
            if (ingredients.isEmpty()) {
                return;
            }
            Map<String, FlowIngredientRequest> merged = new LinkedHashMap<>();
            for (FlowIngredientRequest ingredient : ingredients) {
                String key = ingredient.ingredient + "\n" + ingredient.target + "\n" + ingredient.amountPerSet;
                FlowIngredientRequest existing = merged.get(key);
                if (existing == null) {
                    merged.put(key, ingredient.copy());
                } else {
                    existing.requested += ingredient.requested;
                }
            }
            out.append(prefix).append("ingredients requested:\n");
            for (FlowIngredientRequest ingredient : merged.values()) {
                out.append(prefix).append("  - ").append(ingredient.ingredient).append(": requested=")
                        .append(ingredient.requested).append(" amountPerSet=").append(ingredient.amountPerSet)
                        .append(" -> ").append(ingredient.target).append("\n");
            }
        }
    }

    private static class FlowSetRequest {

        private final int remainingSets;
        private final int orderableSets;
        private final int branchSets;
        private final int selectedSets;

        private FlowSetRequest(int remainingSets, int orderableSets, int branchSets, int selectedSets) {
            this.remainingSets = remainingSets;
            this.orderableSets = orderableSets;
            this.branchSets = branchSets;
            this.selectedSets = selectedSets;
        }
    }

    private static class FlowIngredientRequest {

        private final String ingredient;
        private final String target;
        private int requested;
        private final int amountPerSet;

        private FlowIngredientRequest(String ingredient, String target, int requested, int amountPerSet) {
            this.ingredient = ingredient;
            this.target = target;
            this.requested = requested;
            this.amountPerSet = amountPerSet;
        }

        private FlowIngredientRequest copy() {
            return new FlowIngredientRequest(ingredient, target, requested, amountPerSet);
        }
    }

    private static class PipeMessage {

        private final String pipeKey;
        private final String body;

        private PipeMessage(String pipeKey, String body) {
            this.pipeKey = pipeKey;
            this.body = body;
        }

        private String messageWithPipe() {
            return pipeKey == null ? body : pipeKey + " " + body;
        }
    }

    private static class RequestSnapshot {

        private final int id;
        private final String title;
        private final long time;
        private final String treeText;
        private final String ordersText;

        private RequestSnapshot(int id, String title, long time, String treeText, String ordersText) {
            this.id = id;
            this.title = title;
            this.time = time;
            this.treeText = treeText;
            this.ordersText = ordersText;
        }
    }

    private static class DebugEvent {

        private final int id;
        private final long time;
        private final int tick;
        private final String category;
        private final String message;

        private DebugEvent(int id, long time, int tick, String category, String message) {
            this.id = id;
            this.time = time;
            this.tick = tick;
            this.category = category;
            this.message = message;
        }
    }
}
