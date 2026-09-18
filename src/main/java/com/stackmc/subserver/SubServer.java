package com.stackmc.subserver;

import com.infernalsuite.asp.api.loaders.SlimeLoader;
import com.stackmc.subserver.commands.SubServerCommand;
import com.stackmc.subserver.worldgen.TreeSlimeLoader;
import com.stackmc.subserver.instance.Instance;
import com.stackmc.subserver.instance.InstanceFactory;
import com.stackmc.subserver.listeners.EventListener;
import com.stackmc.subserver.listeners.InstanceListener;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.stackmc.subserver.worldgen.SWMUtils.getWorldSlimeFolder;

public final class SubServer extends JavaPlugin {

    private final List<Listener> listeners = new ArrayList<>();
    @Getter private final InstanceFactory instanceFactory = new InstanceFactory(this);
    @Getter public static SlimeLoader loader;

    /** true = les joueurs se voient d'une instance a l'autre (sinon isolation par instance). */
    @Getter private boolean crossInstanceVisibility;
    /** true = le chat est global entre instances (sinon chat limite a l'instance). */
    @Getter private boolean crossInstanceChat;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.crossInstanceVisibility = getConfig().getBoolean("cross-instance.visibility", false);
        this.crossInstanceChat = getConfig().getBoolean("cross-instance.chat", false);
        loader = new TreeSlimeLoader(new File(getWorldSlimeFolder()));
        this.listeners.add(new InstanceListener(this));
        this.listeners.add(new EventListener(this));
        registerListeners();
        registerCommands();

        instanceFactory.startLoop();
    }

    @Override
    public void onDisable() {
        instanceFactory.stopLoop();
        List<Instance> instancesSnapshot = new ArrayList<>(Instance.getInstances());
        instancesSnapshot.forEach(Instance::close);
        Instance.getInstances().clear();
    }

    public void registerCommands(){
        this.getCommand("subserver").setExecutor(new SubServerCommand(this));
    }

    public void registerListeners(){
        this.listeners.forEach(listener -> Bukkit.getPluginManager().registerEvents(listener, this));
    }
}