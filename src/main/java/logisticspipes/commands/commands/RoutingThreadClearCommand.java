package logisticspipes.commands.commands;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import logisticspipes.commands.abstracts.ICommandHandler;
import logisticspipes.routing.astar.LPJunctionNetwork;

public class RoutingThreadClearCommand implements ICommandHandler {

    @Override
    public String[] getNames() {
        return new String[] { "routingthread-clear", "rt-clear" };
    }

    @Override
    public boolean isCommandUsableBy(ICommandSender sender) {
        return true;
    }

    @Override
    public String[] getDescription() {
        return new String[] { "Reset the routing statistics shown by rt (cached routes are kept)" };
    }

    @Override
    public void executeCommand(ICommandSender sender, String[] args) {
        LPJunctionNetwork.resetStats();
        sender.addChatMessage(new ChatComponentText("Routing statistics reset."));
    }
}
