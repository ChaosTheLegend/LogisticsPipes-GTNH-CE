package logisticspipes.commands.commands.debug;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import network.rs485.debuggui.api.IDataConnection;
import network.rs485.debuggui.api.IDebugGuiEntry;
import network.rs485.debuggui.api.IObjectIdentification;

import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.debuggui.DebugDataPacket;
import logisticspipes.network.packets.debuggui.DebugPanelOpen;
import logisticspipes.proxy.MainProxy;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierStack;
import lombok.AllArgsConstructor;

public class DebugGuiController {

    static {
        Launch.classLoader.addTransformerExclusion("com.trolltech.qt.");
        Launch.classLoader.addTransformerExclusion("network.rs485.debuggui.");
    }

    private static transient DebugGuiController instance;

    private DebugGuiController() {}

    public static DebugGuiController instance() {
        if (DebugGuiController.instance == null) {
            DebugGuiController.instance = new DebugGuiController();
        }
        return DebugGuiController.instance;
    }

    public void execClient() {
        if (clientController != null) {
            clientController.exec();
        }
        drainPendingClientData();
    }

    public void execServer() {
        for (IDebugGuiEntry entry : serverDebugger.values()) {
            entry.exec();
        }
    }

    private final HashMap<EntityPlayer, IDebugGuiEntry> serverDebugger = new HashMap<>();
    private final HashMap<EntityPlayer, List<Integer>> serverDebugIds = new HashMap<>();
    private final List<IDataConnection> serverList = new LinkedList<>();

    private IDebugGuiEntry clientController = null;
    private final List<Future<IDataConnection>> clientList = new LinkedList<>();
    private final HashMap<Integer, List<byte[]>> pendingClientData = new HashMap<>();

    public void startWatchingOf(Object object, EntityPlayer player) {
        if (object == null) {
            return;
        }
        IDebugGuiEntry entry = serverDebugger.get(player);
        if (entry == null) {
            try {
                entry = IDebugGuiEntry.create();
                serverDebugger.put(player, entry);
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
                return;
            }
        }
        synchronized (serverList) {
            int identification = serverList.indexOf(null);
            if (identification < 0) {
                identification = serverList.size();
            }
            IDataConnection conIn = new DataConnectionServer(identification, player);
            while (serverList.size() <= identification) serverList.add(null);
            List<Integer> debugIds = serverDebugIds.get(player);
            if (debugIds == null) {
                debugIds = new ArrayList<>();
                serverDebugIds.put(player, debugIds);
            }
            debugIds.add(identification);
            MainProxy.sendPacketToPlayer(
                    PacketHandler.getPacket(DebugPanelOpen.class).setName(object.getClass().getSimpleName())
                            .setIdentification(identification),
                    player);
            serverList.set(identification, entry.startServerDebugging(object, conIn, new ObjectIdentification()));
        }
    }

    public void clearServerDebuggers(EntityPlayer player) {
        if (player == null) {
            return;
        }
        serverDebugger.remove(player);
        List<Integer> debugIds = serverDebugIds.remove(player);
        if (debugIds == null) {
            return;
        }
        synchronized (serverList) {
            for (Integer debugId : debugIds) {
                if (debugId == null || debugId < 0 || debugId >= serverList.size()) {
                    continue;
                }
                IDataConnection connection = serverList.get(debugId);
                if (connection != null) {
                    connection.closeCon();
                }
                serverList.set(debugId, null);
            }
        }
    }

    public void createNewDebugGui(String name, int identification) {
        if (clientController == null) {
            try {
                clientController = IDebugGuiEntry.create();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
                return;
            }
        }
        synchronized (clientList) {
            while (clientList.size() <= identification) clientList.add(null);
            closeClientConnection(clientList.get(identification));
            clientList.set(
                    identification,
                    clientController.startClientDebugging(name, new DataConnectionClient(identification)));
            drainPendingClientData(identification);
        }
    }

    public void handleDataPacket(byte[] payload, int identifier, EntityPlayer player) {
        if (MainProxy.isServer(player.getEntityWorld())) {
            synchronized (serverList) {
                if (identifier < 0 || identifier >= serverList.size()) {
                    return;
                }
                IDataConnection connection = serverList.get(identifier);
                if (connection != null) {
                    connection.passData(payload);
                }
            }
        } else {
            synchronized (clientList) {
                if (!passClientData(identifier, payload)) {
                    queueClientData(identifier, payload);
                }
            }
        }
    }

    public void clearClientDebuggers() {
        synchronized (clientList) {
            for (Future<IDataConnection> connectionFuture : clientList) {
                closeClientConnection(connectionFuture);
            }
            clientList.clear();
            pendingClientData.clear();
            clientController = null;
        }
    }

    private boolean passClientData(int identifier, byte[] payload) {
        if (identifier < 0 || identifier >= clientList.size()) {
            return false;
        }
        Future<IDataConnection> connectionFuture = clientList.get(identifier);
        if (connectionFuture == null || !connectionFuture.isDone()) {
            return false;
        }
        IDataConnection connection = null;
        try {
            connection = connectionFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        if (connection == null) {
            return false;
        }
        connection.passData(payload);
        return true;
    }

    private void queueClientData(int identifier, byte[] payload) {
        if (identifier < 0) {
            return;
        }
        List<byte[]> pendingData = pendingClientData.get(identifier);
        if (pendingData == null) {
            pendingData = new ArrayList<>();
            pendingClientData.put(identifier, pendingData);
        }
        pendingData.add(payload);
    }

    private void drainPendingClientData() {
        synchronized (clientList) {
            Iterator<Map.Entry<Integer, List<byte[]>>> iterator = pendingClientData.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, List<byte[]>> entry = iterator.next();
                if (drainPendingClientData(entry.getKey(), entry.getValue())) {
                    iterator.remove();
                }
            }
        }
    }

    private void drainPendingClientData(int identifier) {
        List<byte[]> pendingData = pendingClientData.get(identifier);
        if (pendingData != null && drainPendingClientData(identifier, pendingData)) {
            pendingClientData.remove(identifier);
        }
    }

    private boolean drainPendingClientData(int identifier, List<byte[]> pendingData) {
        if (pendingData == null || pendingData.isEmpty()) {
            return true;
        }
        if (identifier < 0 || identifier >= clientList.size()) {
            return false;
        }
        Future<IDataConnection> connectionFuture = clientList.get(identifier);
        if (connectionFuture == null || !connectionFuture.isDone()) {
            return false;
        }
        for (byte[] payload : pendingData) {
            if (!passClientData(identifier, payload)) {
                return false;
            }
        }
        return true;
    }

    private void closeClientConnection(Future<IDataConnection> connectionFuture) {
        if (connectionFuture == null || !connectionFuture.isDone()) {
            return;
        }
        try {
            IDataConnection connection = connectionFuture.get();
            if (connection != null) {
                connection.closeCon();
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @AllArgsConstructor
    private class DataConnectionServer implements IDataConnection {

        private int identification;
        private EntityPlayer player;

        @Override
        public void passData(byte[] packet) {
            MainProxy.sendPacketToPlayer(
                    PacketHandler.getPacket(DebugDataPacket.class).setPayload(packet).setIdentifier(identification),
                    player);
        }

        @Override
        public void closeCon() {
            synchronized (serverList) {
                if (identification >= 0 && identification < serverList.size()) {
                    serverList.set(identification, null);
                }
            }
        }
    }

    @AllArgsConstructor
    private class DataConnectionClient implements IDataConnection {

        private int identification;

        @Override
        public void passData(byte[] packet) {
            MainProxy.sendPacketToServer(
                    PacketHandler.getPacket(DebugDataPacket.class).setPayload(packet).setIdentifier(identification));
        }

        @Override
        public void closeCon() {
            synchronized (clientList) {
                if (identification >= 0 && identification < clientList.size()) {
                    clientList.set(identification, null);
                }
            }
        }
    }

    private static class ObjectIdentification implements IObjectIdentification {

        @Override
        public boolean toStringObject(Object o) {
            return o.getClass() == ForgeDirection.class || o.getClass() == ItemIdentifier.class
                    || o.getClass() == ItemIdentifierStack.class;
        }

        @Override
        public String handleObject(Object o) {
            if (o instanceof World) {
                return ((World) o).getWorldInfo().getWorldName();
            }
            if (o != null && o.getClass().isArray() && Array.getLength(o) > 100) {
                return "(Too big)";
            }
            return null;
        }
    }
}
