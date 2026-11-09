package logisticspipes.modules;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.item.ItemStack;

import gregtech.api.enums.OrePrefixes;
import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.common.tileentities.automation.MTETypeFilter;
import logisticspipes.utils.item.ItemIdentifier;

/**
 * The GregTech part of the tag sink's GT prefix mode, as the GT type filter sink did it. Only loaded when GregTech is
 * (the mode is offered only then).
 */
final class TagSinkGregTech {

    private TagSinkGregTech() {}

    /**
     * @return the prefixes the names stand for, unknown names left out
     */
    static Set<Object> prefixes(Collection<String> names) {
        Set<Object> prefixes = new HashSet<>();
        for (String name : names) {
            OrePrefixes prefix = OrePrefixes.getPrefix(name);
            if (prefix != null) {
                prefixes.add(prefix);
            }
        }
        return prefixes;
    }

    /**
     * @return true if the item has one of the prefixes; "ore" takes every kind of ore block
     */
    static boolean matches(Set<Object> prefixes, ItemIdentifier identifier) {
        ItemStack stack = identifier.makeNormalStackNoTag(1);
        ItemData data = GTOreDictUnificator.getItemData(stack);
        for (Object o : prefixes) {
            OrePrefixes prefix = (OrePrefixes) o;
            if (prefix == OrePrefixes.ore && data != null
                    && data.mPrefix != null
                    && MTETypeFilter.OREBLOCK_PREFIXES.contains(data.mPrefix)) {
                return true;
            }
            if (prefix.contains(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the prefix of the item, empty if it has none
     */
    static String prefixOf(ItemIdentifier identifier) {
        ItemData data = GTOreDictUnificator.getAssociation(identifier.makeNormalStackNoTag(1));
        if (data != null && data.hasValidPrefixData()) {
            return data.mPrefix.toString();
        }
        return "";
    }
}
