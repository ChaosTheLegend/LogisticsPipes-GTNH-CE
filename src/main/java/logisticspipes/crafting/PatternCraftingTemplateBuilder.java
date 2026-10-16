package logisticspipes.crafting;

import net.minecraft.item.ItemStack;

import logisticspipes.crafting.pattern.AbstractPattern;
import logisticspipes.crafting.pattern.PatternHandler;
import logisticspipes.crafting.pattern.PatternRecipeSnapshot;
import logisticspipes.crafting.patternStack.IPatternStack;
import logisticspipes.crafting.patternStack.PatternFluidStack;
import logisticspipes.crafting.patternStack.PatternStackHelper;
import logisticspipes.request.BaseCraftingTemplate;
import logisticspipes.request.ICraftingTemplate;
import logisticspipes.request.resources.DictResource;
import logisticspipes.request.resources.FluidResource;
import logisticspipes.request.resources.IResource;
import logisticspipes.request.resources.ItemResource;
import logisticspipes.utils.FluidIdentifierStack;
import logisticspipes.utils.item.ItemIdentifierStack;

/**
 * Builds request-tree crafting templates from configured pattern items.
 * <p>
 * The module owns the live state and request interfaces; this helper only translates pattern inputs and outputs into
 * item or fluid crafting templates with matching byproduct promises.
 */
class PatternCraftingTemplateBuilder {

    private final ModulePatternCrafting module;
    private final PatternHandler patternHandler;

    /**
     * Creates a builder backed by the module request hooks and its current pattern inventory.
     */
    PatternCraftingTemplateBuilder(ModulePatternCrafting module, PatternHandler patternHandler) {
        this.module = module;
        this.patternHandler = patternHandler;
    }

    /**
     * Finds the first configured pattern output that matches the requested resource and returns its crafting template.
     */
    ICraftingTemplate addCrafting(IResource toCraft) {
        for (int slot = 0; slot < patternHandler.size(); slot++) {
            ItemStack pattern = patternHandler.getConfiguredPatternStack(slot);
            if (pattern == null) {
                continue;
            }
            if (!module.isPatternCraftingSupported(pattern)) {
                module.debug(
                        "crafting template skipped slot=%d request=%s: fluid crafting upgrade missing",
                        slot,
                        toCraft);
                continue;
            }
            PatternRecipeSnapshot recipe = patternHandler.getRecipe(slot);
            ICraftingTemplate itemTemplate = buildItemTemplate(toCraft, slot, recipe);
            if (itemTemplate != null) {
                return itemTemplate;
            }
            ICraftingTemplate fluidTemplate = buildFluidTemplate(toCraft, slot, recipe);
            if (fluidTemplate != null) {
                return fluidTemplate;
            }
        }
        return null;
    }

    /**
     * Builds an item crafting template when one output item identity matches the requested resource.
     */
    private ICraftingTemplate buildItemTemplate(IResource toCraft, int slot, PatternRecipeSnapshot recipe) {
        for (int outputSlot = 0; outputSlot < recipe.getResultSlotCount(); outputSlot++) {
            if (outputSlot != recipe.getMainOutputSlot()) {
                continue;
            }
            IPatternStack output = recipe.getOutput(outputSlot);
            ItemIdentifierStack result = PatternStackHelper.asSolidStack(output);
            if (result == null || !toCraft.matches(result.getItem(), IResource.MatchSettings.NORMAL)) {
                continue;
            }
            module.debug("crafting template matched item output slot=%d result=%s request=%s", slot, result, toCraft);
            PatternCraftingTemplate template = new PatternCraftingTemplate(
                    result.clone(),
                    module,
                    0,
                    slot,
                    recipe.getIngredientSlotCount());
            addPatternIngredients(template, recipe, slot);
            template.setOutputTarget(itemByproductTarget(recipe, slot, outputSlot));
            addItemResultByproducts(template, recipe, outputSlot);
            return template;
        }
        return null;
    }

    /**
     * Builds a fluid crafting template when one output fluid display item identity matches the requested resource.
     */
    private ICraftingTemplate buildFluidTemplate(IResource toCraft, int slot, PatternRecipeSnapshot recipe) {
        for (int outputSlot = 0; outputSlot < recipe.getResultSlotCount(); outputSlot++) {
            if (outputSlot != recipe.getMainOutputSlot()) {
                continue;
            }
            IPatternStack output = recipe.getOutput(outputSlot);
            if (!(output instanceof PatternFluidStack result)) {
                continue;
            }
            if (!toCraft.matches(result.getFluid().getItemIdentifier(), IResource.MatchSettings.NORMAL)) {
                continue;
            }
            module.debug("crafting template matched fluid output slot=%d result=%s request=%s", slot, result, toCraft);
            PatternFluidCraftingTemplate template = new PatternFluidCraftingTemplate(
                    new FluidResource(result.getFluid(), result.getAmount(), module),
                    module,
                    0,
                    slot);
            addPatternIngredients(template, recipe, slot);
            template.setOutputTarget(fluidByproductTarget(recipe, slot, outputSlot));
            addFluidResultByproducts(template, recipe, outputSlot);
            return template;
        }
        return null;
    }

    /**
     * Adds every non-requested output from an item-producing pattern as an extra item or fluid byproduct.
     */
    private void addItemResultByproducts(PatternCraftingTemplate template, PatternRecipeSnapshot recipe,
            int resultOutputSlot) {
        for (int outputSlot = 0; outputSlot < recipe.getResultSlotCount(); outputSlot++) {
            if (outputSlot == resultOutputSlot) {
                continue;
            }
            IPatternStack byproductStack = recipe.getOutput(outputSlot);
            ItemIdentifierStack byproduct = PatternStackHelper.asSolidStack(byproductStack);
            if (byproduct != null) {
                template.addByproduct(
                        byproduct.clone(),
                        itemByproductTarget(recipe, template.getPatternSlot(), outputSlot));
                continue;
            }
            if (byproductStack instanceof PatternFluidStack fluidByproduct) {
                template.addFluidByproduct(
                        new FluidIdentifierStack(fluidByproduct.getFluid(), fluidByproduct.getAmount()),
                        fluidByproductTarget(recipe, template.getPatternSlot(), outputSlot));
            }
        }
    }

    /**
     * Adds every secondary output from a fluid-producing pattern as an extra item or fluid byproduct.
     */
    private void addFluidResultByproducts(PatternFluidCraftingTemplate template, PatternRecipeSnapshot recipe,
            int resultOutputSlot) {
        for (int outputSlot = 0; outputSlot < recipe.getResultSlotCount(); outputSlot++) {
            if (outputSlot == resultOutputSlot) {
                continue;
            }
            IPatternStack byproductStack = recipe.getOutput(outputSlot);
            ItemIdentifierStack byproduct = PatternStackHelper.asSolidStack(byproductStack);
            if (byproduct != null) {
                template.addByproduct(
                        byproduct.clone(),
                        itemByproductTarget(recipe, template.getPatternSlot(), outputSlot));
                continue;
            }
            if (byproductStack instanceof PatternFluidStack fluidByproduct) {
                template.addFluidByproduct(
                        new FluidIdentifierStack(fluidByproduct.getFluid(), fluidByproduct.getAmount()),
                        fluidByproductTarget(recipe, template.getPatternSlot(), outputSlot));
            }
        }
    }

    private PatternByproductTarget itemByproductTarget(PatternRecipeSnapshot recipe, int patternSlot, int outputSlot) {
        return byproductTarget(
                patternSlot,
                outputSlot,
                recipe.getByproductSatelliteId(outputSlot),
                recipe.getByproductSatelliteUuid(outputSlot),
                false);
    }

    private PatternByproductTarget fluidByproductTarget(PatternRecipeSnapshot recipe, int patternSlot, int outputSlot) {
        return byproductTarget(
                patternSlot,
                outputSlot,
                recipe.getFluidByproductSatelliteId(outputSlot),
                recipe.getFluidByproductSatelliteUuid(outputSlot),
                true);
    }

    private PatternByproductTarget byproductTarget(int patternSlot, int outputSlot, int satelliteId,
            String satelliteUuid, boolean fluid) {
        return new PatternByproductTarget(patternSlot, outputSlot, satelliteId, satelliteUuid, fluid, null);
    }

    /**
     * Adds every local item or fluid ingredient from a pattern to a request-tree template.
     */
    private void addPatternIngredients(BaseCraftingTemplate template, PatternRecipeSnapshot recipe, int slot) {
        for (int inputSlot = 0; inputSlot < recipe.getIngredientSlotCount(); inputSlot++) {
            IPatternStack ingredient = recipe.getInput(inputSlot);
            if (ingredient == null || ingredient.getAmount() <= 0) {
                continue;
            }
            ItemIdentifierStack item = PatternStackHelper.asSolidStack(ingredient);
            if (item != null) {
                module.debug("template ingredient slot=%d inputSlot=%d item=%s", slot, inputSlot, item);
                template.addIngredient(
                        createItemIngredientResource(item, recipe.getPattern()),
                        new PatternTargetInformation(slot, inputSlot));
                continue;
            }
            if (ingredient instanceof PatternFluidStack fluid) {
                module.debug("template ingredient slot=%d inputSlot=%d fluid=%s", slot, inputSlot, fluid);
                template.addIngredient(
                        new FluidResource(fluid.getFluid(), fluid.getAmount(), module),
                        new PatternTargetInformation(slot, inputSlot));
            }
        }
    }

    /**
     * Converts one pattern item ingredient into the resource that the request tree should solve.
     * <p>
     * OreDict and NBT flags are intentionally applied only here, so later staged fulfilment follows the concrete tree
     * that was selected.
     */
    private IResource createItemIngredientResource(ItemIdentifierStack item, AbstractPattern pattern) {
        ItemIdentifierStack ingredient = item.clone();
        boolean useOreDict = pattern.isOreDictSubstitutionEnabled()
                && ingredient.getItem().getDictIdentifiers() != null;
        boolean ignoreNbt = pattern.isIgnoreNbtEnabled();
        if (!useOreDict && !ignoreNbt) {
            return new ItemResource(ingredient, module);
        }
        DictResource resource = new DictResource(ingredient, module);
        resource.use_od = useOreDict;
        resource.ignore_nbt = ignoreNbt;
        resource.match_same_item = useOreDict || ignoreNbt;
        return resource;
    }
}
