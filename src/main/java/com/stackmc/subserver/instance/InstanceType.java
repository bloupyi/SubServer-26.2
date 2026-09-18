package com.stackmc.subserver.instance;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Getter
public class InstanceType {
    /** Plafond absolu du nombre d'instances ouvertes simultanement pour un meme type. */
    public static final int MAX_INSTANCES_LIMIT = 10;

    /** Valeur de {@code maxPlayers} signifiant "pas de limite". */
    public static final int UNLIMITED_PLAYERS = -1;

    private final String name;
    private final boolean autoJoin;
    private final int maxPlayers;

    private int maxInstancesCount = 1;

    @Setter private boolean closeWhenEmpty;

    /** Delai de grace avant la fermeture d'une instance vide, en secondes. */
    @Setter private int emptyGraceSeconds = 15;

    private final List<InstanciableWorld> worlds = new ArrayList<>();

    @Setter
    private Consumer<Instance> postInitRunnable = (instance -> {});

    public InstanceType(String name, boolean autoJoin, int maxPlayers) {
        this.name = name;
        this.autoJoin = autoJoin;
        this.maxPlayers = maxPlayers;
    }

    public void setMaxInstancesCount(int maxInstancesCount) {
        if (maxInstancesCount > MAX_INSTANCES_LIMIT) {
            Bukkit.getLogger().warning("Type d'instance " + name + " : " + maxInstancesCount
                    + " instances demandees, ramene au maximum de " + MAX_INSTANCES_LIMIT + ".");
            this.maxInstancesCount = MAX_INSTANCES_LIMIT;
            return;
        }
        this.maxInstancesCount = Math.max(0, maxInstancesCount);
    }

    /** {@code true} si une instance de ce type peut encore accueillir {@code current} joueurs. */
    public boolean hasRoomFor(int current) {
        return maxPlayers <= UNLIMITED_PLAYERS || current < maxPlayers;
    }

    /** {@code true} si la boucle de generation doit maintenir des instances de ce type. */
    public boolean isPreGenerated() {
        return maxInstancesCount > 0;
    }

    @Override
    public InstanceType clone() {
        InstanceType type = new InstanceType(name, autoJoin, maxPlayers);
        type.setMaxInstancesCount(maxInstancesCount);
        type.setPostInitRunnable(postInitRunnable);
        type.setCloseWhenEmpty(closeWhenEmpty);
        type.setEmptyGraceSeconds(emptyGraceSeconds);
        type.worlds.addAll(worlds);
        return type;
    }

    @Getter
    @RequiredArgsConstructor
    public static class InstanciableWorld {
        private final String worldName;
        private final boolean savable;
    }

    public void addWorld(String worldName, boolean savable) {
        worlds.add(new InstanciableWorld(worldName, savable));
    }
}
