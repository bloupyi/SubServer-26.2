package com.stackmc.subserver.worldgen;

import com.infernalsuite.asp.api.AdvancedSlimePaperAPI;
import com.infernalsuite.asp.api.exceptions.CorruptedWorldException;
import com.infernalsuite.asp.api.exceptions.NewerFormatException;
import com.infernalsuite.asp.api.exceptions.UnknownWorldException;
import com.infernalsuite.asp.api.exceptions.WorldLoadedException;
import com.infernalsuite.asp.api.loaders.SlimeLoader;
import com.infernalsuite.asp.api.world.SlimeWorld;
import com.infernalsuite.asp.api.world.properties.SlimeProperties;
import com.infernalsuite.asp.api.world.properties.SlimePropertyMap;
import com.stackmc.subserver.SubServer;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Scanner;

public class SWMUtils {
    private static String slimeFolder = null;
    private static AdvancedSlimePaperAPI getSlimePlugin() {
        return AdvancedSlimePaperAPI.instance();
    }

    private static SlimePropertyMap getDefaultProperties() {
        SlimePropertyMap properties = new SlimePropertyMap();

        properties.setValue(SlimeProperties.DIFFICULTY, "normal");
        properties.setValue(SlimeProperties.SPAWN_X, 0);
        properties.setValue(SlimeProperties.SPAWN_Y, 100);
        properties.setValue(SlimeProperties.SPAWN_Z, 0);

        return properties;
    }

    @NotNull
    public static String getWorldSlimeFolder() {
        if (slimeFolder != null) {
            return slimeFolder;
        }

        try {
            File src = new File("plugins/SlimeWorldManager/sources.yml");
            Scanner myReader = new Scanner(src);
            String read = "file:";
            while(!(myReader.nextLine().equals(read))) {
                myReader.nextLine();
            }
            String data = myReader.nextLine();
            String[] dataSplit = data.split(":");
            String dataPart = dataSplit[1];
            String[] folderNameSplit = dataPart.split(" ");
            slimeFolder = folderNameSplit[1];
            myReader.close();
            return slimeFolder;
        } catch (FileNotFoundException e) {
            Bukkit.getLogger().severe("§cLa source n'existe pas.");
            return "";
        }
    }

    public static void deleteWorld(String worldName) {
        SlimeLoader loader = SubServer.loader;
        try {
            loader.deleteWorld(worldName);
        }catch (UnknownWorldException | IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public static void loadWorld(String worldName, boolean readOnly) throws UnknownWorldException, IOException, CorruptedWorldException, NewerFormatException, WorldLoadedException {
        attach(read(worldName, readOnly));
    }

    public static SlimeWorld read(String worldName, boolean readOnly)
            throws UnknownWorldException, IOException, CorruptedWorldException, NewerFormatException {
        return getSlimePlugin().readWorld(SubServer.loader, worldName, readOnly, getDefaultProperties());
    }

    public static void attach(SlimeWorld world) throws IllegalArgumentException {
        getSlimePlugin().loadWorld(world, true);
    }
}
