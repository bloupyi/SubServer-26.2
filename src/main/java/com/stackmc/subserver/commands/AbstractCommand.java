package com.stackmc.subserver.commands;

import com.stackmc.subserver.SubServer;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public abstract class AbstractCommand implements TabExecutor {

    private final Map<String, TabExecutor> subCommands = new HashMap<>();
    protected final SubServer plugin;

    public abstract String getPermission();

    public abstract boolean runCommand(CommandSender sender, Command rootCommand, String label, String[] args);

    public void registerSubCommand(String label, TabExecutor subCommand) {
        subCommands.put(label.toLowerCase(), subCommand);
    }

    protected Component getUsage() {
        return Component.text("Usage: /subserver <" + String.join("|", this.subCommands.keySet()) + ">", NamedTextColor.RED);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!sender.hasPermission(this.getPermission()) && !sender.isOp()) {
            sender.sendMessage(Component.text("Tu n'as pas la permission d'utiliser cette commande.", NamedTextColor.RED));
            return true;
        }

        if (args.length > 0) {
            TabExecutor child = subCommands.get(args[0].toLowerCase());
            if (child != null) {
                return child.onCommand(sender, command, args[0], removeHead(args));
            }
        }
        return runCommand(sender, command, label, args);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if(!sender.hasPermission(this.getPermission()) && !sender.isOp()) {
            return Collections.emptyList();
        }

        if (args.length > 0) {
            TabExecutor child = subCommands.get(args[0].toLowerCase());
            if (child != null) {
                return child.onTabComplete(sender, command, args[0], removeHead(args));
            }
            return subCommands.keySet().parallelStream().filter(cmd -> cmd.startsWith(args[0])).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private String[] removeHead(String[] list) {
        String[] newList = new String[list.length - 1];
        System.arraycopy(list, 1, newList, 0, newList.length);
        return newList;
    }
}