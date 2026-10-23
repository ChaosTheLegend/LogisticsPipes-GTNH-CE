package logisticspipes.crafting.legacy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import logisticspipes.crafting.legacy.module.ModuleCrafter;
import logisticspipes.crafting.legacy.pipe.PipeItemsCraftingLogistics;
import logisticspipes.crafting.legacy.template.ICraftingTemplate;
import logisticspipes.crafting.v3.exec.CraftDelivery;
import logisticspipes.crafting.v3.exec.CrafterPort;
import logisticspipes.crafting.v3.exec.Ticket;
import logisticspipes.crafting.v3.mc.CraftingService;
import logisticspipes.crafting.v3.mc.FluidKey;
import logisticspipes.crafting.v3.mc.IV3Crafter;
import logisticspipes.crafting.v3.mc.ItemKey;
import logisticspipes.crafting.v3.mc.PatternIds;
import logisticspipes.crafting.v3.model.BlockingMode;
import logisticspipes.crafting.v3.model.CrafterInfo;
import logisticspipes.crafting.v3.model.InputSpec;
import logisticspipes.crafting.v3.model.MatchRule;
import logisticspipes.crafting.v3.model.Pattern;
import logisticspipes.crafting.v3.model.ResourceAmount;
import logisticspipes.crafting.v3.model.ResourceKey;
import logisticspipes.crafting.v3.model.ResourceMatcher;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IRequest;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.PipeLogisticsChassi;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.request.IExtraPromise;
import logisticspipes.request.resources.DictResource;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Old crafting modules and crafting pipes as v3 crafters (plan section 13: they take part in the crafting tree, but
 * have no buffer and no blocking mode, and every node on them carries a removal warning).
 * <p>
 * Their recipes are read through their own {@code addCrafting} templates, so fuzzy flags and satellites keep working.
 * Ingredients are routed straight to them (or their satellites) and go into the machine on arrival; outputs leave
 * through a normal crafting order the driver places once a set is in.
 */
public final class LegacyCrafters {

    /** Legacy crafters rank below every pattern crafting pipe. */
    public static final int PRIORITY_OFFSET = -1_000_000;

    private LegacyCrafters() {}

    public static List<IV3Crafter> of(CoreRoutedPipe pipe) {
        List<ModuleCrafter> modules = new ArrayList<>();
        if (pipe instanceof PipeItemsCraftingLogistics) {
            modules.add(((PipeItemsCraftingLogistics) pipe).getLogisticsModule());
        } else if (pipe instanceof PipeLogisticsChassi) {
            PipeLogisticsChassi chassis = (PipeLogisticsChassi) pipe;
            for (int i = 0; i < chassis.getChassiSize(); i++) {
                LogisticsModule module = chassis.getLogisticsModule().getSubModule(i);
                if (module instanceof ModuleCrafter) modules.add((ModuleCrafter) module);
            }
        }
        if (modules.isEmpty()) return Collections.emptyList();
        return Collections.singletonList(new Legacy(pipe, modules));
    }

    private static final class Legacy implements IV3Crafter, CrafterPort {

        private final CoreRoutedPipe pipe;
        private final List<ModuleCrafter> modules;
        /** Pattern slot -> module that crafts it, rebuilt with the patterns. */
        private final Map<Integer, ModuleCrafter> moduleBySlot = new LinkedHashMap<>();

        Legacy(CoreRoutedPipe pipe, List<ModuleCrafter> modules) {
            this.pipe = pipe;
            this.modules = modules;
        }

        @Override
        public CrafterInfo crafterInfo() {
            int priority = 0;
            for (ModuleCrafter m : modules) priority = Math.max(priority, m.getPriority());
            return new CrafterInfo(
                    pipe.getRouter().getId(),
                    "Legacy crafter at " + pipe.getX() + ", " + pipe.getY() + ", " + pipe.getZ(),
                    BlockingMode.NONE,
                    PRIORITY_OFFSET + priority,
                    true,
                    1);
        }

        @Override
        public Map<Integer, Pattern> v3Patterns() {
            Map<Integer, Pattern> result = new LinkedHashMap<>();
            moduleBySlot.clear();
            UUID self = pipe.getRouter().getId();
            for (int m = 0; m < modules.size(); m++) {
                ModuleCrafter module = modules.get(m);
                List<ItemIdentifierStack> results = module.getConfiguredCraftResults();
                if (results == null) continue;
                for (int r = 0; r < results.size(); r++) {
                    Pattern p = toPattern(module, results.get(r), self, m, r);
                    if (p == null) continue;
                    int slot = m * 64 + r;
                    result.put(slot, p);
                    moduleBySlot.put(slot, module);
                }
            }
            return result;
        }

        private static Pattern toPattern(ModuleCrafter module, ItemIdentifierStack result, UUID self, int m, int r) {
            ICraftingTemplate template;
            try {
                template = module.addCrafting(new ItemResource(result, null));
            } catch (RuntimeException e) {
                return null;
            }
            if (template == null || template.getResultStack() == null) return null;
            List<InputSpec> inputs = new ArrayList<>();
            StringBuilder sig = new StringBuilder("legacy:").append(self).append(':').append(m).append(':').append(r);
            for (var component : template.getComponents(1)) {
                IResource res = component.getValue1();
                InputSpec spec = toInput(res, self);
                if (spec == null) return null;
                inputs.add(spec);
                sig.append('|').append(PatternIds.signature(spec.key())).append('~').append(spec.matcher().rule())
                        .append('x').append(spec.qty()).append('@').append(spec.target());
            }
            List<ResourceAmount> outputs = new ArrayList<>();
            ItemIdentifier out = template.getResultStack().getItemIdentifier();
            outputs.add(new ResourceAmount(ItemKey.of(out), template.getResultStack().getStackSize()));
            for (IExtraPromise extra : template.getByproducts(1)) {
                if (extra.getItemType() != null && extra.getAmount() > 0) {
                    outputs.add(new ResourceAmount(ItemKey.of(extra.getItemType()), extra.getAmount()));
                }
            }
            if (inputs.isEmpty()) return null;
            return new Pattern(PatternIds.hash(sig.toString()), inputs, outputs, null);
        }

        private static InputSpec toInput(IResource res, UUID self) {
            if (res instanceof FluidResource) {
                FluidResource fluid = (FluidResource) res;
                ResourceKey key = FluidKey.of(fluid.getFluid());
                return new InputSpec(
                        ResourceMatcher.exact(key),
                        fluid.getRequestedAmount(),
                        true,
                        targetOf(fluid.getTarget(), self));
            }
            if (res instanceof DictResource) {
                DictResource dict = (DictResource) res;
                MatchRule rule = dict.use_od ? MatchRule.ORE_DICT
                        : dict.ignore_nbt ? MatchRule.IGNORE_NBT
                                : dict.ignore_dmg ? MatchRule.IGNORE_DAMAGE : MatchRule.EXACT;
                return new InputSpec(
                        ResourceMatcher.of(ItemKey.of(dict.getItem()), rule, null),
                        dict.getItemStack().getStackSize(),
                        true,
                        targetOf(dict.getTarget(), self));
            }
            if (res instanceof ItemResource) {
                ItemResource item = (ItemResource) res;
                return new InputSpec(
                        ResourceMatcher.exact(ItemKey.of(item.getItem())),
                        item.getItemStack().getStackSize(),
                        true,
                        targetOf(item.getTarget(), self));
            }
            return null;
        }

        private static UUID targetOf(IRequest target, UUID self) {
            if (target == null || target.getRouter() == null) return null;
            UUID id = target.getRouter().getId();
            return self.equals(id) ? null : id;
        }

        @Override
        public CrafterPort port() {
            return this;
        }

        // ---------------------------------------------------------------- CrafterPort: legacy behaviour

        @Override
        public boolean isLegacy() {
            return true;
        }

        @Override
        public BlockingMode mode() {
            return BlockingMode.NONE;
        }

        @Override
        public void open(Ticket ticket) {}

        @Override
        public void commit(Ticket ticket) {}

        @Override
        public void cancel(long ticket, long job) {}

        @Override
        public long pooled(long job, ResourceKey key) {
            return 0;
        }

        @Override
        public long assign(long job, ResourceKey key, long qty, Ticket ticket, int need) {
            return 0;
        }

        @Override
        public long ship(long job, ResourceKey key, long qty, UUID destination, CraftDelivery info) {
            return 0;
        }

        @Override
        public void flushJob(long job) {}

        @Override
        public boolean legacyOrder(ResourceKey key, long qty, UUID destination, CraftDelivery info) {
            if (!(key instanceof ItemKey)) return false;
            CoreRoutedPipe dest = CraftingService.pipeAt(destination);
            if (!(dest instanceof IRequestItems)) return false;
            ItemIdentifier item = ((ItemKey) key).id;
            for (ModuleCrafter module : modules) {
                List<ItemIdentifierStack> results = module.getConfiguredCraftResults();
                if (results == null) continue;
                for (ItemIdentifierStack r : results) {
                    if (!r.getItem().equals(item)) continue;
                    IAdditionalTargetInformation delivery = CraftingService.INSTANCE.deliveryFor(info, destination);
                    module.fullFill(
                            new LogisticsPromise(
                                    item,
                                    (int) Math.min(Integer.MAX_VALUE, qty),
                                    module,
                                    ResourceType.CRAFTING),
                            (IRequestItems) dest,
                            delivery);
                    return true;
                }
            }
            return false;
        }
    }
}
