package logisticspipes.crafting;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.FluidStack;

import logisticspipes.crafting.patternStack.PatternItemStack;
import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.interfaces.routing.IProvideFluids;
import logisticspipes.interfaces.routing.IProvideItems;
import logisticspipes.interfaces.routing.IRequestFluid;
import logisticspipes.interfaces.routing.IRequestItems;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.request.IExtraPromise;
import logisticspipes.request.IPromise;
import logisticspipes.request.resources.DictResource;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.routing.FluidExtraPromise;
import logisticspipes.routing.FluidLogisticsPromise;
import logisticspipes.routing.IRouter;
import logisticspipes.routing.LogisticsDictPromise;
import logisticspipes.routing.LogisticsExtraDictPromise;
import logisticspipes.routing.LogisticsExtraPromise;
import logisticspipes.routing.LogisticsPromise;
import logisticspipes.routing.order.IOrderInfoProvider;
import logisticspipes.routing.order.IOrderInfoProvider.ResourceType;
import logisticspipes.routing.order.LogisticsFluidOrder;
import logisticspipes.routing.order.LogisticsItemOrder;
import logisticspipes.routing.order.LogisticsOrder;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;

final class PatternCraftingPersistence {

    private static final String KIND_TAG = "kind";
    private static final String ITEM_KIND = "item";
    private static final String DICT_KIND = "dict";
    private static final String FLUID_KIND = "fluid";
    private static final String PATTERN_ITEM_PROMISE_KIND = "patternItem";
    private static final String PATTERN_FLUID_PROMISE_KIND = "patternFluid";
    private static final String ITEM_EXTRA_PROMISE_KIND = "itemExtra";
    private static final String DICT_EXTRA_PROMISE_KIND = "dictExtra";
    private static final String FLUID_EXTRA_PROMISE_KIND = "fluidExtra";
    private static final String PATTERN_ITEM_EXTRA_PROMISE_KIND = "patternItemExtra";
    private static final String PATTERN_FLUID_EXTRA_PROMISE_KIND = "patternFluidExtra";

    private static final String STACK_TAG = "stack";
    private static final String RESOURCE_TAG = "resource";
    private static final String AMOUNT_TAG = "amount";
    private static final String TYPE_TAG = "type";
    private static final String PROVIDER_TAG = "provider";
    private static final String TARGET_TAG = "target";
    private static final String DESTINATION_TAG = "destination";
    private static final String MODULE_SUFFIX = "Module";
    private static final String ROUTER_SUFFIX = "Router";
    private static final String INFO_TAG = "info";
    private static final String INFO_PATTERN_KIND = "pattern";
    private static final String PATTERN_SLOT_TAG = "patternSlot";
    private static final String INPUT_SLOT_TAG = "inputSlot";
    private static final String ORDER_REFERENCE_PREFIX = "order";
    private static final String DELIVERY_REFERENCE_PREFIX = "delivery";
    private static final String CRAFTING_REFERENCE_PREFIX = "crafting";
    private static final String RESULT_AMOUNT_PER_SET_TAG = "resultAmountPerSet";
    private static final String PROVIDED_TAG = "provided";
    private static final String USE_OD_TAG = "useOd";
    private static final String IGNORE_DMG_TAG = "ignoreDmg";
    private static final String IGNORE_NBT_TAG = "ignoreNbt";
    private static final String USE_CATEGORY_TAG = "useCategory";
    private static final String MATCH_SAME_ITEM_TAG = "matchSameItem";
    private static final String IN_PROGRESS_TAG = "inProgress";
    private static final String MACHINE_PROGRESS_TAG = "machineProgress";
    private static final String WATCHED_TAG = "watched";
    private static final String BYPRODUCT_TAG = "byproduct";
    private static final String BYPRODUCT_TARGET_PREFIX = "byproductTarget";

    private PatternCraftingPersistence() {}

    static boolean writeResource(NBTTagCompound tag, IResource resource) {
        if (resource instanceof FluidResource fluid) {
            tag.setString(KIND_TAG, FLUID_KIND);
            writeFluid(tag, fluid.getFluid(), fluid.getRequestedAmount());
            writeFluidRequester(tag, TARGET_TAG, fluid.getTarget());
            return true;
        }
        if (resource instanceof DictResource dict) {
            tag.setString(KIND_TAG, DICT_KIND);
            writeDictResource(tag, dict);
            writeItemRequester(tag, TARGET_TAG, dict.getTarget());
            return true;
        }
        if (resource instanceof ItemResource item) {
            tag.setString(KIND_TAG, ITEM_KIND);
            writeStack(tag, item.getItemStack());
            writeItemRequester(tag, TARGET_TAG, item.getTarget());
            return true;
        }
        return false;
    }

    static IResource readResource(NBTTagCompound tag) {
        String kind = tag.getString(KIND_TAG);
        if (FLUID_KIND.equals(kind)) {
            FluidIdentifier fluid = readFluid(tag);
            if (fluid == null) {
                throw new RestoreNotReadyException();
            }
            return new FluidResource(fluid, tag.getInteger(AMOUNT_TAG), readFluidRequester(tag, TARGET_TAG));
        }
        if (DICT_KIND.equals(kind)) {
            return readDictResource(tag, readItemRequester(tag, TARGET_TAG));
        }
        if (ITEM_KIND.equals(kind)) {
            ItemIdentifierStack stack = readStack(tag);
            if (stack == null) {
                throw new RestoreNotReadyException();
            }
            return new ItemResource(stack, readItemRequester(tag, TARGET_TAG));
        }
        throw new RestoreNotReadyException();
    }

    static boolean writePromise(NBTTagCompound tag, IPromise promise) {
        if (promise instanceof PatternFluidCraftingPromise pattern) {
            tag.setString(KIND_TAG, PATTERN_FLUID_PROMISE_KIND);
            writeFluid(tag, pattern.getLiquid(), pattern.getAmount());
            writeFluidProvider(tag, PROVIDER_TAG, pattern.getSender());
            tag.setInteger(PATTERN_SLOT_TAG, pattern.getPatternSlot());
            tag.setInteger(RESULT_AMOUNT_PER_SET_TAG, pattern.getResultAmountPerSet());
            return true;
        }
        if (promise instanceof PatternFluidByproductPromise extra) {
            tag.setString(KIND_TAG, PATTERN_FLUID_EXTRA_PROMISE_KIND);
            writeFluid(tag, extra.getLiquid(), extra.getAmount());
            writeFluidProvider(tag, PROVIDER_TAG, extra.getSender());
            tag.setBoolean(PROVIDED_TAG, extra.isProvided());
            writeByproductTarget(tag, extra.getByproductTarget());
            return true;
        }
        if (promise instanceof FluidExtraPromise extra) {
            tag.setString(KIND_TAG, FLUID_EXTRA_PROMISE_KIND);
            writeFluid(tag, extra.getLiquid(), extra.getAmount());
            writeFluidProvider(tag, PROVIDER_TAG, extra.getSender());
            tag.setBoolean(PROVIDED_TAG, extra.isProvided());
            return true;
        }
        if (promise instanceof FluidLogisticsPromise fluid) {
            tag.setString(KIND_TAG, FLUID_KIND);
            writeFluid(tag, fluid.getLiquid(), fluid.getAmount());
            writeResourceType(tag, fluid.getType());
            writeFluidProvider(tag, PROVIDER_TAG, fluid.getSender());
            return true;
        }
        if (promise instanceof PatternCraftingPromise pattern) {
            tag.setString(KIND_TAG, PATTERN_ITEM_PROMISE_KIND);
            writeStack(tag, pattern.getItemType().makeStack(pattern.getAmount()));
            writeItemProvider(tag, PROVIDER_TAG, (IProvideItems) pattern.getProvider());
            tag.setInteger(PATTERN_SLOT_TAG, pattern.getPatternSlot());
            tag.setInteger(RESULT_AMOUNT_PER_SET_TAG, pattern.getResultAmountPerSet());
            return true;
        }
        if (promise instanceof LogisticsExtraDictPromise extra) {
            tag.setString(KIND_TAG, DICT_EXTRA_PROMISE_KIND);
            writeDictResource(tag, extra.getResource());
            writeItemProvider(tag, PROVIDER_TAG, extra.sender);
            tag.setBoolean(PROVIDED_TAG, extra.isProvided());
            return true;
        }
        if (promise instanceof PatternItemByproductPromise extra) {
            tag.setString(KIND_TAG, PATTERN_ITEM_EXTRA_PROMISE_KIND);
            writeStack(tag, extra.getItemType().makeStack(extra.getAmount()));
            writeItemProvider(tag, PROVIDER_TAG, extra.sender);
            tag.setBoolean(PROVIDED_TAG, extra.isProvided());
            writeByproductTarget(tag, extra.getByproductTarget());
            return true;
        }
        if (promise instanceof LogisticsExtraPromise extra) {
            tag.setString(KIND_TAG, ITEM_EXTRA_PROMISE_KIND);
            writeStack(tag, extra.getItemType().makeStack(extra.getAmount()));
            writeItemProvider(tag, PROVIDER_TAG, extra.sender);
            tag.setBoolean(PROVIDED_TAG, extra.isProvided());
            return true;
        }
        if (promise instanceof LogisticsDictPromise dict) {
            tag.setString(KIND_TAG, DICT_KIND);
            writeDictResource(tag, dict.getResource());
            writeResourceType(tag, dict.getType());
            writeItemProvider(tag, PROVIDER_TAG, dict.sender);
            return true;
        }
        if (promise instanceof LogisticsPromise item) {
            tag.setString(KIND_TAG, ITEM_KIND);
            writeStack(tag, item.getItemType().makeStack(item.getAmount()));
            writeResourceType(tag, item.getType());
            writeItemProvider(tag, PROVIDER_TAG, item.sender);
            return true;
        }
        return false;
    }

    static IPromise readPromise(NBTTagCompound tag) {
        String kind = tag.getString(KIND_TAG);
        if (PATTERN_FLUID_PROMISE_KIND.equals(kind)) {
            return new PatternFluidCraftingPromise(
                    readRequiredFluid(tag),
                    tag.getInteger(AMOUNT_TAG),
                    readFluidProvider(tag, PROVIDER_TAG),
                    tag.getInteger(PATTERN_SLOT_TAG),
                    tag.getInteger(RESULT_AMOUNT_PER_SET_TAG));
        }
        if (PATTERN_FLUID_EXTRA_PROMISE_KIND.equals(kind)) {
            return new PatternFluidByproductPromise(
                    readRequiredFluid(tag),
                    tag.getInteger(AMOUNT_TAG),
                    readFluidProvider(tag, PROVIDER_TAG),
                    tag.getBoolean(PROVIDED_TAG),
                    PatternByproductTarget.readFromNBT(tag, BYPRODUCT_TARGET_PREFIX));
        }
        if (FLUID_EXTRA_PROMISE_KIND.equals(kind)) {
            return new FluidExtraPromise(
                    readRequiredFluid(tag),
                    tag.getInteger(AMOUNT_TAG),
                    readFluidProvider(tag, PROVIDER_TAG),
                    tag.getBoolean(PROVIDED_TAG));
        }
        if (FLUID_KIND.equals(kind)) {
            return new FluidLogisticsPromise(
                    readRequiredFluid(tag),
                    tag.getInteger(AMOUNT_TAG),
                    readFluidProvider(tag, PROVIDER_TAG),
                    readResourceType(tag));
        }
        if (PATTERN_ITEM_PROMISE_KIND.equals(kind)) {
            ItemIdentifierStack stack = readRequiredStack(tag);
            return new PatternCraftingPromise(
                    stack.getItem(),
                    stack.getStackSize(),
                    readItemProvider(tag, PROVIDER_TAG),
                    tag.getInteger(PATTERN_SLOT_TAG),
                    tag.getInteger(RESULT_AMOUNT_PER_SET_TAG));
        }
        if (PATTERN_ITEM_EXTRA_PROMISE_KIND.equals(kind)) {
            ItemIdentifierStack stack = readRequiredStack(tag);
            return new PatternItemByproductPromise(
                    stack.getItem(),
                    stack.getStackSize(),
                    readItemProvider(tag, PROVIDER_TAG),
                    tag.getBoolean(PROVIDED_TAG),
                    PatternByproductTarget.readFromNBT(tag, BYPRODUCT_TARGET_PREFIX));
        }
        if (DICT_EXTRA_PROMISE_KIND.equals(kind)) {
            DictResource resource = readDictResource(tag, null);
            return new LogisticsExtraDictPromise(
                    resource,
                    resource.getRequestedAmount(),
                    readItemProvider(tag, PROVIDER_TAG),
                    tag.getBoolean(PROVIDED_TAG));
        }
        if (ITEM_EXTRA_PROMISE_KIND.equals(kind)) {
            ItemIdentifierStack stack = readRequiredStack(tag);
            return new LogisticsExtraPromise(
                    stack.getItem(),
                    stack.getStackSize(),
                    readItemProvider(tag, PROVIDER_TAG),
                    tag.getBoolean(PROVIDED_TAG));
        }
        if (DICT_KIND.equals(kind)) {
            DictResource resource = readDictResource(tag, null);
            return new LogisticsDictPromise(
                    resource,
                    resource.getRequestedAmount(),
                    readItemProvider(tag, PROVIDER_TAG),
                    readResourceType(tag));
        }
        if (ITEM_KIND.equals(kind)) {
            ItemIdentifierStack stack = readRequiredStack(tag);
            return new LogisticsPromise(
                    stack.getItem(),
                    stack.getStackSize(),
                    readItemProvider(tag, PROVIDER_TAG),
                    readResourceType(tag));
        }
        throw new RestoreNotReadyException();
    }

    static IExtraPromise readExtraPromise(NBTTagCompound tag) {
        IPromise promise = readPromise(tag);
        if (!(promise instanceof IExtraPromise)) {
            throw new RestoreNotReadyException();
        }
        return (IExtraPromise) promise;
    }

    static boolean writeOrder(NBTTagCompound tag, IOrderInfoProvider order) {
        if (order instanceof LogisticsItemOrder itemOrder) {
            tag.setString(KIND_TAG, ITEM_KIND);
            NBTTagCompound resourceTag = new NBTTagCompound();
            writeDictResource(resourceTag, itemOrder.getResource());
            tag.setTag(RESOURCE_TAG, resourceTag);
            writeItemRequester(tag, DESTINATION_TAG, itemOrder.getDestination());
            writeResourceType(tag, itemOrder.getType());
            writeTargetInfo(tag, itemOrder.getInformation());
            writeOrderRuntimeState(tag, itemOrder);
            return true;
        }
        if (order instanceof LogisticsFluidOrder fluidOrder) {
            tag.setString(KIND_TAG, FLUID_KIND);
            writeFluid(tag, fluidOrder.getFluid(), fluidOrder.getAmount());
            writeFluidRequester(tag, DESTINATION_TAG, fluidOrder.getDestination());
            writeResourceType(tag, fluidOrder.getType());
            writeTargetInfo(tag, fluidOrder.getInformation());
            writeOrderRuntimeState(tag, fluidOrder);
            return true;
        }
        return false;
    }

    static RestoredOrder readOrder(NBTTagCompound tag) {
        String kind = tag.getString(KIND_TAG);
        RestoredOrder order = new RestoredOrder();
        order.type = readResourceType(tag);
        order.info = readTargetInfo(tag.getCompoundTag(INFO_TAG));
        order.inProgress = tag.getBoolean(IN_PROGRESS_TAG);
        order.machineProgress = tag.getByte(MACHINE_PROGRESS_TAG);
        order.watched = tag.getBoolean(WATCHED_TAG);
        order.byproduct = tag.getBoolean(BYPRODUCT_TAG);
        order.byproductTarget = PatternByproductTarget.readFromNBT(tag, BYPRODUCT_TARGET_PREFIX);
        order.craftingReference = PatternCraftingReference.readFromNBT(tag, CRAFTING_REFERENCE_PREFIX);
        if (ITEM_KIND.equals(kind)) {
            order.itemResource = readDictResource(tag.getCompoundTag(RESOURCE_TAG), null);
            order.itemDestination = readItemRequester(tag, DESTINATION_TAG);
            return order;
        }
        if (FLUID_KIND.equals(kind)) {
            order.fluid = readRequiredFluid(tag);
            order.amount = tag.getInteger(AMOUNT_TAG);
            order.fluidDestination = readFluidRequester(tag, DESTINATION_TAG);
            return order;
        }
        throw new RestoreNotReadyException();
    }

    static PatternCraftingReference readOrderCraftingReference(NBTTagCompound tag) {
        return PatternCraftingReference.readFromNBT(tag, CRAFTING_REFERENCE_PREFIX);
    }

    static void writeOrderCraftingReference(NBTTagCompound tag, PatternCraftingReference reference) {
        if (tag != null && reference != null) {
            reference.writeToNBT(tag, CRAFTING_REFERENCE_PREFIX);
        }
    }

    static ItemIdentifierStack readOrderDisplayStack(NBTTagCompound tag) {
        String kind = tag.getString(KIND_TAG);
        if (ITEM_KIND.equals(kind)) {
            return readStack(tag.getCompoundTag(RESOURCE_TAG));
        }
        if (FLUID_KIND.equals(kind)) {
            FluidIdentifier fluid = readFluid(tag);
            int amount = tag.getInteger(AMOUNT_TAG);
            return fluid == null || amount <= 0 ? null : new ItemIdentifierStack(fluid.getItemIdentifier(), amount);
        }
        return null;
    }

    static void writeTargetInfo(NBTTagCompound parent, IAdditionalTargetInformation info) {
        NBTTagCompound tag = new NBTTagCompound();
        if (info instanceof PatternTargetInformation patternInfo) {
            tag.setString(KIND_TAG, INFO_PATTERN_KIND);
            tag.setInteger(PATTERN_SLOT_TAG, patternInfo.patternSlot());
            tag.setInteger(INPUT_SLOT_TAG, patternInfo.inputSlot());
            if (patternInfo.orderReference() != null) {
                patternInfo.orderReference().writeToNBT(tag, ORDER_REFERENCE_PREFIX);
            }
            if (patternInfo.deliveryReference() != null) {
                patternInfo.deliveryReference().writeToNBT(tag, DELIVERY_REFERENCE_PREFIX);
            }
        }
        if (!tag.hasNoTags()) {
            parent.setTag(INFO_TAG, tag);
        }
    }

    static IAdditionalTargetInformation readTargetInfo(NBTTagCompound tag) {
        if (!INFO_PATTERN_KIND.equals(tag.getString(KIND_TAG))) {
            return null;
        }
        int inputSlot = tag.hasKey(INPUT_SLOT_TAG) ? tag.getInteger(INPUT_SLOT_TAG)
                : PatternTargetInformation.NO_INPUT_SLOT;
        return new PatternTargetInformation(
                tag.getInteger(PATTERN_SLOT_TAG),
                inputSlot,
                PatternCraftingReference.readFromNBT(tag, ORDER_REFERENCE_PREFIX),
                PatternCraftingReference.readFromNBT(tag, DELIVERY_REFERENCE_PREFIX));
    }

    static IAdditionalTargetInformation readTargetInfoFromParent(NBTTagCompound parent) {
        return readTargetInfo(parent.getCompoundTag(INFO_TAG));
    }

    private static void writeOrderRuntimeState(NBTTagCompound tag, IOrderInfoProvider order) {
        tag.setBoolean(IN_PROGRESS_TAG, order.isInProgress());
        tag.setBoolean(WATCHED_TAG, order.isWatched());
        tag.setByte(MACHINE_PROGRESS_TAG, order.getMachineProgress());
        if (order instanceof LogisticsOrder logisticsOrder && logisticsOrder.isByproduct()) {
            tag.setBoolean(BYPRODUCT_TAG, true);
        }
        if (order instanceof LogisticsOrder logisticsOrder) {
            writeByproductTarget(tag, logisticsOrder.getByproductTarget());
        }
        if (order instanceof LogisticsOrder logisticsOrder && logisticsOrder.getCraftingReference() != null) {
            logisticsOrder.getCraftingReference().writeToNBT(tag, CRAFTING_REFERENCE_PREFIX);
        }
    }

    private static void restoreOrderRuntimeState(IOrderInfoProvider order, RestoredOrder state) {
        if (!(order instanceof LogisticsOrder logisticsOrder)) {
            return;
        }
        logisticsOrder.setInProgress(state.inProgress);
        logisticsOrder.setMachineProgress(state.machineProgress);
        logisticsOrder.setByproduct(state.byproduct);
        logisticsOrder.setByproductTarget(state.byproductTarget);
        logisticsOrder.setCraftingReference(state.craftingReference);
        if (state.watched) {
            logisticsOrder.setWatched();
        }
    }

    private static void writeByproductTarget(NBTTagCompound tag, PatternByproductTarget target) {
        if (target != null && target.isConfigured()) {
            target.writeToNBT(tag, BYPRODUCT_TARGET_PREFIX);
        }
    }

    private static void writeDictResource(NBTTagCompound tag, DictResource resource) {
        if (resource == null || !writeStack(tag, resource.getItemStack())) {
            return;
        }
        tag.setBoolean(USE_OD_TAG, resource.use_od);
        tag.setBoolean(IGNORE_DMG_TAG, resource.ignore_dmg);
        tag.setBoolean(IGNORE_NBT_TAG, resource.ignore_nbt);
        tag.setBoolean(USE_CATEGORY_TAG, resource.use_category);
        tag.setBoolean(MATCH_SAME_ITEM_TAG, resource.match_same_item);
    }

    private static DictResource readDictResource(NBTTagCompound tag, IRequestItems target) {
        ItemIdentifierStack stack = readRequiredStack(tag);
        DictResource resource = new DictResource(stack, target);
        resource.use_od = tag.getBoolean(USE_OD_TAG);
        resource.ignore_dmg = tag.getBoolean(IGNORE_DMG_TAG);
        resource.ignore_nbt = tag.getBoolean(IGNORE_NBT_TAG);
        resource.use_category = tag.getBoolean(USE_CATEGORY_TAG);
        resource.match_same_item = tag.getBoolean(MATCH_SAME_ITEM_TAG);
        return resource;
    }

    private static boolean writeStack(NBTTagCompound tag, ItemIdentifierStack stack) {
        if (stack == null || stack.getStackSize() <= 0) {
            return false;
        }
        NBTTagCompound stackTag = new NBTTagCompound();
        PatternItemStack.writeItem(stackTag, stack);
        tag.setTag(STACK_TAG, stackTag);
        return true;
    }

    private static ItemIdentifierStack readRequiredStack(NBTTagCompound tag) {
        ItemIdentifierStack stack = readStack(tag);
        if (stack == null || stack.getStackSize() <= 0) {
            throw new RestoreNotReadyException();
        }
        return stack;
    }

    private static ItemIdentifierStack readStack(NBTTagCompound tag) {
        return PatternItemStack.readItem(tag.getCompoundTag(STACK_TAG));
    }

    private static void writeFluid(NBTTagCompound tag, FluidIdentifier fluid, int amount) {
        if (fluid == null || amount <= 0) {
            return;
        }
        FluidStack stack = fluid.makeFluidStack(amount);
        NBTTagCompound fluidTag = new NBTTagCompound();
        stack.writeToNBT(fluidTag);
        tag.setTag(STACK_TAG, fluidTag);
        tag.setInteger(AMOUNT_TAG, amount);
    }

    private static FluidIdentifier readRequiredFluid(NBTTagCompound tag) {
        FluidIdentifier fluid = readFluid(tag);
        if (fluid == null) {
            throw new RestoreNotReadyException();
        }
        return fluid;
    }

    private static FluidIdentifier readFluid(NBTTagCompound tag) {
        FluidStack stack = FluidStack.loadFluidStackFromNBT(tag.getCompoundTag(STACK_TAG));
        if (stack == null) {
            return null;
        }
        return FluidIdentifier.get(stack);
    }

    private static void writeResourceType(NBTTagCompound tag, ResourceType type) {
        if (type != null) {
            tag.setString(TYPE_TAG, type.name());
        }
    }

    private static ResourceType readResourceType(NBTTagCompound tag) {
        if (!tag.hasKey(TYPE_TAG)) {
            throw new RestoreNotReadyException();
        }
        try {
            return ResourceType.valueOf(tag.getString(TYPE_TAG));
        } catch (IllegalArgumentException ignored) {
            throw new RestoreNotReadyException();
        }
    }

    private static void writeItemProvider(NBTTagCompound tag, String prefix, IProvideItems provider) {
        if (provider != null) {
            writeRouter(tag, prefix, provider.getRouter(), provider);
        }
    }

    private static IProvideItems readItemProvider(NBTTagCompound tag, String prefix) {
        IRouter router = readRequiredRouter(tag, prefix);
        Object provider = resolveRoutedObject(router, tag.getBoolean(prefix + MODULE_SUFFIX), IProvideItems.class);
        if (provider == null) {
            throw new RestoreNotReadyException();
        }
        return (IProvideItems) provider;
    }

    private static void writeFluidProvider(NBTTagCompound tag, String prefix, IProvideFluids provider) {
        if (provider != null) {
            writeRouter(tag, prefix, provider.getRouter(), provider);
        }
    }

    private static IProvideFluids readFluidProvider(NBTTagCompound tag, String prefix) {
        IRouter router = readRequiredRouter(tag, prefix);
        Object provider = resolveRoutedObject(router, tag.getBoolean(prefix + MODULE_SUFFIX), IProvideFluids.class);
        if (provider == null) {
            throw new RestoreNotReadyException();
        }
        return (IProvideFluids) provider;
    }

    private static void writeItemRequester(NBTTagCompound tag, String prefix, IRequestItems requester) {
        if (requester != null) {
            writeRouter(tag, prefix, requester.getRouter(), requester);
        }
    }

    private static IRequestItems readItemRequester(NBTTagCompound tag, String prefix) {
        IRouter router = readOptionalRouter(tag, prefix);
        if (router == null) {
            return null;
        }
        Object requester = resolveRoutedObject(router, tag.getBoolean(prefix + MODULE_SUFFIX), IRequestItems.class);
        if (requester == null) {
            throw new RestoreNotReadyException();
        }
        return (IRequestItems) requester;
    }

    private static void writeFluidRequester(NBTTagCompound tag, String prefix, IRequestFluid requester) {
        if (requester != null) {
            writeRouter(tag, prefix, requester.getRouter(), requester);
        }
    }

    private static IRequestFluid readFluidRequester(NBTTagCompound tag, String prefix) {
        IRouter router = readOptionalRouter(tag, prefix);
        if (router == null) {
            return null;
        }
        Object requester = resolveRoutedObject(router, tag.getBoolean(prefix + MODULE_SUFFIX), IRequestFluid.class);
        if (requester == null) {
            throw new RestoreNotReadyException();
        }
        return (IRequestFluid) requester;
    }

    private static void writeRouter(NBTTagCompound tag, String prefix, IRouter router, Object routedObject) {
        if (router == null || router.getId() == null) {
            return;
        }
        tag.setString(prefix + ROUTER_SUFFIX, router.getId().toString());
        tag.setBoolean(prefix + MODULE_SUFFIX, router.getLogisticsModule() == routedObject);
    }

    private static IRouter readOptionalRouter(NBTTagCompound tag, String prefix) {
        if (!tag.hasKey(prefix + ROUTER_SUFFIX)) {
            return null;
        }
        IRouter router = readRouter(tag, prefix);
        if (router == null) {
            throw new RestoreNotReadyException();
        }
        return router;
    }

    private static IRouter readRequiredRouter(NBTTagCompound tag, String prefix) {
        IRouter router = readOptionalRouter(tag, prefix);
        if (router == null) {
            throw new RestoreNotReadyException();
        }
        return router;
    }

    private static IRouter readRouter(NBTTagCompound tag, String prefix) {
        try {
            int id = SimpleServiceLocator.routerManager
                    .getIDforUUID(UUID.fromString(tag.getString(prefix + ROUTER_SUFFIX)));
            if (id <= 0) {
                return null;
            }
            return SimpleServiceLocator.routerManager.getRouter(id);
        } catch (IllegalArgumentException ignored) {
            throw new RestoreNotReadyException();
        }
    }

    private static Object resolveRoutedObject(IRouter router, boolean preferModule, Class<?> type) {
        LogisticsModule module = router.getLogisticsModule();
        CoreRoutedPipe pipe = router.getPipe();
        if (preferModule && type.isInstance(module)) {
            return module;
        }
        if (type.isInstance(pipe)) {
            return pipe;
        }
        if (type.isInstance(module)) {
            return module;
        }
        return null;
    }

    static final class RestoredOrder {

        private DictResource itemResource;
        private IRequestItems itemDestination;
        private FluidIdentifier fluid;
        private int amount;
        private IRequestFluid fluidDestination;
        private ResourceType type;
        private IAdditionalTargetInformation info;
        private boolean inProgress;
        private boolean watched;
        private boolean byproduct;
        private PatternByproductTarget byproductTarget;
        private PatternCraftingReference craftingReference;
        private byte machineProgress;

        PatternCraftingReference craftingReference() {
            return craftingReference;
        }

        IOrderInfoProvider create(PipeItemsPatternCraftingLogistics pipe, ModulePatternCrafting module) {
            IOrderInfoProvider order;
            if (itemResource != null) {
                if (type == ResourceType.EXTRA) {
                    order = pipe.getItemOrderManager().addExtra(itemResource);
                } else {
                    order = pipe.getItemOrderManager().addOrder(itemResource, itemDestination, type, info);
                }
            } else {
                if (type == ResourceType.EXTRA) {
                    order = pipe.getPatternFluidOrderManager().addExtra(fluid, amount);
                } else {
                    order = pipe.getPatternFluidOrderManager().addOrder(
                            new FluidLogisticsPromise(fluid, amount, module, type),
                            fluidDestination,
                            type,
                            info);
                }
            }
            restoreOrderRuntimeState(order, this);
            return order;
        }
    }

    static class RestoreNotReadyException extends RuntimeException {
    }
}
