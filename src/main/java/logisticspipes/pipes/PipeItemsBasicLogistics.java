/*
 * "LogisticsPipes" is distributed under the terms of the Minecraft Mod Public License 1.0, or MMPL. Please check the
 * contents of the license located in http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package logisticspipes.pipes;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import logisticspipes.blocks.LogisticsSecurityTileEntity;
import logisticspipes.blocks.powertile.LogisticsPowerJunctionTileEntity;
import logisticspipes.interfaces.IInventoryUtil;
import logisticspipes.logisticspipes.IRoutedItem;
import logisticspipes.logisticspipes.PipeTransportLayer;
import logisticspipes.logisticspipes.TransportLayer;
import logisticspipes.modules.ModuleItemSink;
import logisticspipes.modules.abstractmodules.LogisticsModule;
import logisticspipes.modules.abstractmodules.LogisticsModule.ModulePositionType;
import logisticspipes.pipes.basic.CoreRoutedPipe;
import logisticspipes.proxy.SimpleServiceLocator;
import logisticspipes.textures.Textures;
import logisticspipes.textures.Textures.TextureType;
import logisticspipes.transport.PipeTransportLogistics;
import logisticspipes.utils.AdjacentTile;
import logisticspipes.utils.InventoryHelper;
import logisticspipes.utils.OrientationsUtil;
import logisticspipes.utils.item.ItemIdentifier;

public class PipeItemsBasicLogistics extends CoreRoutedPipe {

    private final ModuleItemSink itemSinkModule;

    public PipeItemsBasicLogistics(Item item) {
        super(new PipeTransportLogistics(true) {

            @Override
            public boolean canPipeConnect(TileEntity tile, ForgeDirection dir) {
                if (super.canPipeConnect(tile, dir)) {
                    return true;
                }
                if (tile instanceof LogisticsSecurityTileEntity) {
                    ForgeDirection ori = OrientationsUtil.getOrientationOfTilewithTile(container, tile);
                    return ori != null && ori != ForgeDirection.UNKNOWN
                            && ori != ForgeDirection.DOWN
                            && ori != ForgeDirection.UP;
                }
                return false;
            }
        }, item);
        itemSinkModule = new ModuleItemSink();
        itemSinkModule.registerHandler(this, this);
    }

    @Override
    public TextureType getNonRoutedTexture(ForgeDirection connection) {
        if (isSecurityProvider(connection)) {
            return Textures.LOGISTICSPIPE_SECURITY_TEXTURE;
        }
        return super.getNonRoutedTexture(connection);
    }

    @Override
    public boolean isLockedExit(ForgeDirection orientation) {
        if (isPowerJunction(orientation) || isSecurityProvider(orientation)) {
            return true;
        }
        return super.isLockedExit(orientation);
    }

    private boolean isPowerJunction(ForgeDirection ori) {
        TileEntity tilePipe = container.getTile(ori);
        if (tilePipe == null || !container.canPipeConnect(tilePipe, ori)) {
            return false;
        }

        return tilePipe instanceof LogisticsPowerJunctionTileEntity;
    }

    private boolean isSecurityProvider(ForgeDirection ori) {
        TileEntity tilePipe = container.getTile(ori);
        if (tilePipe == null || !container.canPipeConnect(tilePipe, ori)) {
            return false;
        }
        return tilePipe instanceof LogisticsSecurityTileEntity;
    }

    @Override
    public TextureType getCenterTexture() {
        return Textures.LOGISTICSPIPE_TEXTURE;
    }

    @Override
    public LogisticsModule getLogisticsModule() {
        return itemSinkModule;
    }

    /**
     * Items that arrive while there's no inventory, or no room in it, are routed elsewhere instead of being pushed out
     * of a random exit. With nowhere to go they wait in the pipe's buffer and are dropped after a few retries.
     */
    @Override
    public TransportLayer getTransportLayer() {
        if (_transportLayer == null) {
            _transportLayer = new PipeTransportLayer(this, this, getRouter()) {

                @Override
                public boolean stillWantItem(IRoutedItem item) {
                    return itemSinkModule.sinksItem(item.getItemIdentifierStack().getItem(), -1, 0, true, false)
                            != null;
                }
            };
        }
        return _transportLayer;
    }

    @Override
    public ItemSendMode getItemSendMode() {
        return ItemSendMode.Normal;
    }

    @Override
    public void setTile(TileEntity tile) {
        super.setTile(tile);
        itemSinkModule.registerPosition(ModulePositionType.IN_PIPE, 0);
    }

    @Override
    public IInventoryUtil getPointedInventory(boolean forExtraction) {
        IInventoryUtil inv = super.getPointedInventory(forExtraction);
        if (inv == null) {
            for (AdjacentTile connected : getConnectedEntities()) {
                if (connected.tile instanceof IInventory) {
                    IInventory iinv = InventoryHelper.getInventory((IInventory) connected.tile);
                    if (iinv != null) {
                        inv = SimpleServiceLocator.inventoryUtilFactory
                                .getInventoryUtil(iinv, connected.orientation.getOpposite());
                        break;
                    }
                }
            }
        }
        return inv;
    }

    @Override
    public Set<ItemIdentifier> getSpecificInterests() {
        Collection<ItemIdentifier> current = itemSinkModule.getSpecificInterests();
        if (current == null) {
            return null;
        }
        return new TreeSet<>(current);
    }

    @Override
    public boolean hasGenericInterests() {
        return itemSinkModule.hasGenericInterests();
    }
}
