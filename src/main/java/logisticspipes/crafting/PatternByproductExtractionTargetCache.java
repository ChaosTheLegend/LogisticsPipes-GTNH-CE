package logisticspipes.crafting;

import java.util.HashMap;
import java.util.Map;

import logisticspipes.interfaces.routing.IAdditionalTargetInformation;
import logisticspipes.pipes.PipeItemsPatternCraftingLogistics;
import logisticspipes.routing.IRouter;
import logisticspipes.utils.FluidIdentifier;
import logisticspipes.utils.item.ItemIdentifier;

/**
 * Resolves and caches only the extraction satellites explicitly assigned to pattern output slots.
 */
final class PatternByproductExtractionTargetCache {

    private static final int TARGET_CACHE_TICKS = 40;

    private final PipeItemsPatternCraftingLogistics pipe;
    private final Map<PatternByproductTarget, CachedTarget> targets = new HashMap<>();

    PatternByproductExtractionTargetCache(PipeItemsPatternCraftingLogistics pipe) {
        this.pipe = pipe;
    }

    PatternByproductExtractionResult extractItem(PatternByproductTarget configuredTarget, ItemIdentifier item,
            int amount, int destination, IAdditionalTargetInformation info) {
        if (configuredTarget == null || configuredTarget.isFluid() || item == null || amount <= 0) {
            return PatternByproductExtractionResult.empty();
        }
        PatternByproductExtractionTarget target = resolve(configuredTarget);
        return target == null ? PatternByproductExtractionResult.empty()
                : target.extractItemByproduct(item, amount, destination, info);
    }

    PatternByproductExtractionResult extractFluid(PatternByproductTarget configuredTarget, FluidIdentifier fluid,
            int amount, int destination, IAdditionalTargetInformation info) {
        if (configuredTarget == null || !configuredTarget.isFluid() || fluid == null || amount <= 0) {
            return PatternByproductExtractionResult.empty();
        }
        PatternByproductExtractionTarget target = resolve(configuredTarget);
        return target == null ? PatternByproductExtractionResult.empty()
                : target.extractFluidByproduct(fluid, amount, destination, info);
    }

    private PatternByproductExtractionTarget resolve(PatternByproductTarget configuredTarget) {
        if (!configuredTarget.isConfigured() || pipe.getWorld() == null) {
            return null;
        }
        configuredTarget = configuredTarget.withSourceReference(null);
        long now = pipe.getWorld().getTotalWorldTime();
        IRouter requester = pipe.getRouter();
        CachedTarget cached = targets.get(configuredTarget);
        if (cached != null && now < cached.validUntil && cached.validUntil - now <= TARGET_CACHE_TICKS) {
            if (cached.target == null) {
                return null;
            }
            if (cached.target.canExtractByproductsFor(requester)) {
                return cached.target;
            }
        }
        PatternByproductExtractionTarget resolved = find(configuredTarget);
        if (resolved != null && !resolved.canExtractByproductsFor(requester)) {
            resolved = null;
        }
        targets.put(configuredTarget, new CachedTarget(resolved, now + TARGET_CACHE_TICKS));
        return resolved;
    }

    private PatternByproductExtractionTarget find(PatternByproductTarget configuredTarget) {
        if (configuredTarget.isFluid()) {
            PipeFluidPatternSatelliteLogistics satellite = configuredTarget.getSatelliteUuid().isEmpty() ? null
                    : PipeFluidPatternSatelliteLogistics.findByUuid(configuredTarget.getSatelliteUuid());
            return satellite != null || configuredTarget.getSatelliteId() <= 0 ? satellite
                    : PipeFluidPatternSatelliteLogistics.findById(configuredTarget.getSatelliteId());
        }
        PipeItemsPatternSatelliteLogistics satellite = configuredTarget.getSatelliteUuid().isEmpty() ? null
                : PipeItemsPatternSatelliteLogistics.findByUuid(configuredTarget.getSatelliteUuid());
        return satellite != null || configuredTarget.getSatelliteId() <= 0 ? satellite
                : PipeItemsPatternSatelliteLogistics.findById(configuredTarget.getSatelliteId());
    }

    private static final class CachedTarget {

        private final PatternByproductExtractionTarget target;
        private final long validUntil;

        private CachedTarget(PatternByproductExtractionTarget target, long validUntil) {
            this.target = target;
            this.validUntil = validUntil;
        }
    }
}
