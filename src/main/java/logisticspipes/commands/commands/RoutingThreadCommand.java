package logisticspipes.commands.commands;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import logisticspipes.commands.abstracts.ICommandHandler;
import logisticspipes.routing.astar.LPJunctionNetwork;

public class RoutingThreadCommand implements ICommandHandler {

    @Override
    public String[] getNames() {
        return new String[] { "routingthread", "rt" };
    }

    @Override
    public boolean isCommandUsableBy(ICommandSender sender) {
        return true;
    }

    @Override
    public String[] getDescription() {
        return new String[] { "Display Routing thread status information" };
    }

    @Override
    public void executeCommand(ICommandSender sender, String[] args) {
        for (String line : LPJunctionNetwork.describe()) {
            sender.addChatMessage(new ChatComponentText(line));
        }
    }
}
