package com.stackmc.subserver.commands.subs;

import com.stackmc.subserver.SubServer;
import com.stackmc.subserver.instance.Instance;
import lombok.RequiredArgsConstructor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class SendSubCommand implements TabExecutor {
    private final SubServer plugin;

    @Override
    public boolean onCommand(CommandSender sender, Command rootCommand, String label, String[] args) {
        if(args.length < 2) {
            sender.sendMessage("§cUsage: /subserver send <joueur> <instance>");
            return false;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if(target == null) {
            sender.sendMessage("§cCe joueur n'est pas connecté.");
            return false;
        }

        Instance instance = Instance.findInstance(args[1]);
        if(instance == null) {
            sender.sendMessage("§cCette instance n'existe pas (ou plusieurs portent ce nom).");
            return false;
        }

        if (!instance.joinInstance(target)) {
            sender.sendMessage("§cL'instance " + instance.getName() + " est fermée ou pleine.");
            return false;
        }
        sender.sendMessage("§a" + target.getName() + " a été envoyé sur l'instance " + instance.getName() + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command rootCommand, String label, String[] args) {
        if(args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if(args.length == 2) {
            return Instance.getInstances().stream()
                    .map(Instance::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
