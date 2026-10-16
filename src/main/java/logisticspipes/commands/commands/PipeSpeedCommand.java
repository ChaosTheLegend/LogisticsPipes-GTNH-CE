package logisticspipes.commands.commands;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import logisticspipes.LPConstants;
import logisticspipes.commands.LogisticsPipesCommand;
import logisticspipes.commands.abstracts.ICommandHandler;

public class PipeSpeedCommand implements ICommandHandler {

    @Override
    public String[] getNames() {
        return new String[] { "pipespeed", "ps" };
    }

    @Override
    public boolean isCommandUsableBy(ICommandSender sender) {
        return LogisticsPipesCommand.isOP(sender);
    }

    @Override
    public String[] getDescription() {
        return new String[] { "Displays or sets the normal pipe speed", "Usage: /lp pipespeed [speed]" };
    }

    @Override
    public void executeCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.addChatMessage(new ChatComponentText("Normal pipe speed: " + LPConstants.PIPE_NORMAL_SPEED));
            return;
        }
        if (args.length != 1) {
            sender.addChatMessage(new ChatComponentText("Usage: /lp pipespeed [speed]"));
            return;
        }

        final float speed;
        try {
            speed = Float.parseFloat(args[0]);
        } catch (NumberFormatException ignored) {
            sender.addChatMessage(new ChatComponentText("Pipe speed must be a positive number"));
            return;
        }

        if (Float.isNaN(speed) || Float.isInfinite(speed) || speed <= 0) {
            sender.addChatMessage(new ChatComponentText("Pipe speed must be a positive finite number"));
            return;
        }

        LPConstants.PIPE_NORMAL_SPEED = speed;
        sender.addChatMessage(new ChatComponentText("Normal pipe speed set to " + speed));
    }
}
