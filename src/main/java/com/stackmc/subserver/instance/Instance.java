package com.stackmc.subserver.instance;

import com.infernalsuite.asp.api.exceptions.CorruptedWorldException;
import com.infernalsuite.asp.api.exceptions.NewerFormatException;
import com.infernalsuite.asp.api.exceptions.UnknownWorldException;
import com.infernalsuite.asp.api.exceptions.WorldLoadedException;
import com.stackmc.subserver.SubServer;
import com.stackmc.subserver.events.InstanceChatEvent;
import com.stackmc.subserver.events.InstanceJoinEvent;
import com.stackmc.subserver.events.InstanceQuitEvent;
import com.stackmc.subserver.worldgen.SWMUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Getter
public class Instance {

    @Getter private static final Set<Instance> instances = new HashSet<>();

    public static Instance getInstance(World world) {
        return instances.stream()
                .filter(instance -> instance.getWorlds()
                        .stream()
                        .anyMatch(instanciableWorld -> instanciableWorld.getWorld().equals(world)))
                .findAny()
                .orElse(null);
    }

    public static Instance getInstance(String name) {
        return instances.stream().filter(instance -> instance.getName().contains(name)).findAny().orElse(null);
    }

    private final String name;
    private final SubServer plugin;
    private final InstanceType type;
    private final List<InstanciableWorld> worlds = new ArrayList<>();
    private final Set<OfflinePlayer> offlinePlayers = new HashSet<>();
    private final UUID uniqueId = UUID.randomUUID();
    @Setter private InstanceState state = InstanceState.INIT;

    @Getter
    @RequiredArgsConstructor
    public static class InstanciableWorld {
        private final World world;
        private final boolean savable;
    }

    public InstanciableWorld getInstanciableWorld(String worldName) {
        return worlds.stream()
                .filter(instanciableWorld -> instanciableWorld.getWorld().getName().equals(worldName))
                .findAny()
                .orElse(null);
    }

    private final EventDispatcher eventDispatcher = new EventDispatcher();

    public void register() {
        instances.add(this);
    }

    public void registerListener(Listener listener) {
        eventDispatcher.registerListener(listener);
    }

    public void unregisterListener(Listener listener) {
        eventDispatcher.unregisterListener(listener);
    }

    public void dispatchEvent(Event event) {
        eventDispatcher.dispatchEvent(event);
    }

    public void close() {
        Location fallback = Bukkit.getWorlds().get(0).getSpawnLocation();

        worlds.forEach(world -> {
            World bukkitWorld = world.getWorld();
            String worldName = bukkitWorld.getName();

            // unloadWorld renvoie false (et laisse le monde en mémoire) tant qu'il
            // reste des joueurs dedans : on évacue tout le monde réellement présent,
            // pas seulement les joueurs trackés par l'instance.
            new ArrayList<>(bukkitWorld.getPlayers()).forEach(player -> player.teleport(fallback));

            // On ne sauvegarde que les mondes savable ; les temporaires (non-savable) sont
            // chargés en read-only donc ASP ne les réécrit jamais.
            boolean unloaded = Bukkit.unloadWorld(bukkitWorld, world.isSavable());
            if (!unloaded) {
                Bukkit.getLogger().warning("Déchargement du monde " + worldName
                        + " impossible (joueurs restants, WorldUnloadEvent annulé, ou arrêt serveur).");
            }

            if (!world.isSavable()) {
                // IMPORTANT : on supprime le fichier temporaire MÊME si unloadWorld a échoué.
                // À l'arrêt du serveur, Bukkit refuse souvent de décharger un monde ; si on
                // s'arrêtait là, la copie <uuid>_<world>.slime ne serait jamais supprimée et
                // s'accumulerait à chaque redémarrage jusqu'à saturer le dossier des mondes.
                // Le monde étant read-only, aucune réécriture ne recrée le fichier après coup.
                try {
                    SWMUtils.deleteWorld(worldName);
                } catch (RuntimeException e) {
                    Bukkit.getLogger().warning("Impossible de supprimer le monde temporaire " + worldName + " : " + e.getMessage());
                }
            }
        });

        worlds.clear();
        offlinePlayers.clear();
        instances.remove(this);
        plugin.getInstanceFactory().removeInstance(this);
    }

    public void loadWorld(String worldName, boolean isSavable, @Nullable Consumer<String> callback) {
        final Consumer<String> finalCallback = (callback == null ? (s -> {}) : callback);

        String destWorldName;
        if (isSavable) {
            destWorldName = worldName;
        } else {
            destWorldName = getUniqueId().toString() + "_" + worldName;
        }

        File src = new File(SWMUtils.getWorldSlimeFolder() + File.separator + worldName + ".slime");
        File dest = new File( SWMUtils.getWorldSlimeFolder() + File.separator + destWorldName + ".slime");

        long startTime = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (!isSavable) {
                    Files.copy(src.toPath(), dest.toPath());
                }
            } catch (IOException e) {
                finalCallback.accept("§cLe monde spécifié (" + worldName + ") n'existe pas.");
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    SWMUtils.loadWorld(destWorldName, !isSavable);
                } catch (UnknownWorldException | IOException | CorruptedWorldException | NewerFormatException |
                         WorldLoadedException e) {
                    finalCallback.accept("§cUne erreur est survenue lors du chargement du monde.");
                }
            });

            World world;
            do {
                world = Bukkit.getWorld(destWorldName);
            } while (world == null); // I can do this because i'm in an async thread

            this.addWorld(world, isSavable);

            long totalTime = System.currentTimeMillis() - startTime;
            finalCallback.accept("Monde " + destWorldName +  " chargé en " + totalTime + "ms ou " + ((float) totalTime / 50f) + " ticks .");
        });
    }

    public void addWorld(World world, boolean isSavable) {
        worlds.add(new InstanciableWorld(world,isSavable));
    }

    public void joinInstance(Player player) {
        InstanceJoinEvent event = new InstanceJoinEvent(this, player);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }

        Instance oldInstance = Instance.getInstance(player.getWorld());
        if(oldInstance != null) oldInstance.quitInstance(player);
        if (!plugin.isCrossInstanceVisibility()) {
            getPlayers().forEach(target -> {
                player.showPlayer(plugin, target);
                target.showPlayer(plugin, player);
            });
        }
        offlinePlayers.add(player);
        player.teleport(worlds.get(0).getWorld().getSpawnLocation());

        //PlayerJoinEvent event = new PlayerJoinEvent(player," ");
        //this.dispatchEvent(event);
    }

    public void joinInstance(Player player, World world) {
        InstanceJoinEvent event = new InstanceJoinEvent(this, player);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }

        Instance oldInstance = Instance.getInstance(player.getWorld());
        if(oldInstance != null) oldInstance.quitInstance(player);
        if (!plugin.isCrossInstanceVisibility()) {
            getPlayers().forEach(target -> {
                player.showPlayer(plugin, target);
                target.showPlayer(plugin, player);
            });
        }
        offlinePlayers.add(player);
        player.teleport(getInstanciableWorld(world.getName()).getWorld().getSpawnLocation());
    }

    public void quitInstance(Player player) {
        InstanceQuitEvent event = new InstanceQuitEvent(this, player);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }

        if (!plugin.isCrossInstanceVisibility()) {
            getPlayers().forEach(target -> {
                player.hidePlayer(plugin, target);
                target.hidePlayer(plugin, player);
            });
        }
        offlinePlayers.remove(player);

        //PlayerQuitEvent event = new PlayerQuitEvent(player," ");
        //this.dispatchEvent(event);
    }

    public void sendMessage(String message) {
        if (message == null) return;
        getPlayers().forEach(receiver -> receiver.sendMessage(message));
    }

    public void sendMessage(Component message) {
        if (message == null) return;
        getPlayers().forEach(receiver -> receiver.sendMessage(message));
    }

    public List<Player> getPlayers() {
        return offlinePlayers.stream().filter(OfflinePlayer::isOnline).map(OfflinePlayer::getPlayer).collect(Collectors.toList());
    }
}