package com.stackmc.subserver.worldgen;

import com.infernalsuite.asp.api.exceptions.UnknownWorldException;
import com.infernalsuite.asp.api.loaders.SlimeLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Loader ASP qui range les mondes par proprietaire au lieu d'un seul dossier plat.
 *
 * <pre>
 * &lt;racine&gt;/
 *   &lt;uuid-proprietaire&gt;/
 *     &lt;map&gt;/                  edit.&lt;hex32&gt;.&lt;map&gt;
 *       &lt;map&gt;.slime
 *     published/
 *       &lt;niveau&gt;/             pub.&lt;hex32&gt;.&lt;niveau&gt;.&lt;map&gt;
 *         &lt;map&gt;.slime
 *   &lt;autre&gt;.slime             nom plat, comme avant
 * </pre>
 *
 * Un nom qui ne suit aucun des deux schemas retombe sur la racine : les mondes existants et
 * les copies temporaires de partie continuent de fonctionner.
 */
public class TreeSlimeLoader implements SlimeLoader {

    public static final String EDIT = "edit";
    public static final String PUBLISHED = "pub";
    private static final String PUBLISHED_DIR = "published";
    private static final String EXTENSION = ".slime";

    private static final Pattern HEX32 = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern SLUG = Pattern.compile("[a-z0-9_-]{1,32}");

    private final File root;

    public TreeSlimeLoader(File root) {
        this.root = root;
        if (!root.exists() && !root.mkdirs()) {
            throw new IllegalStateException("Dossier des mondes introuvable : " + root);
        }
    }

    public static String editName(UUID owner, String map) {
        return EDIT + "." + hex(owner) + "." + map;
    }

    public static String publishedName(UUID owner, String level, String map) {
        return PUBLISHED + "." + hex(owner) + "." + level + "." + map;
    }

    /** Dossier d'un proprietaire, cree au besoin. */
    public File ownerDirectory(UUID owner) {
        return new File(root, owner.toString());
    }

    public File mapDirectory(UUID owner, String map) {
        return new File(ownerDirectory(owner), map);
    }

    public File publishedDirectory(UUID owner, String level) {
        return new File(new File(ownerDirectory(owner), PUBLISHED_DIR), level);
    }

    public File root() {
        return root;
    }

    /** {@code true} si ce nom peut servir de nom de map ou de niveau. */
    public static boolean isValidSlug(String slug) {
        return slug != null && SLUG.matcher(slug).matches();
    }

    public static String slugify(String raw) {
        if (raw == null) {
            return null;
        }
        String slug = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        return slug.length() > 32 ? slug.substring(0, 32) : slug;
    }

    @Override
    public byte[] readWorld(String worldName) throws UnknownWorldException, IOException {
        File file = resolve(worldName);
        if (file == null || !file.exists()) {
            throw new UnknownWorldException(worldName);
        }
        return Files.readAllBytes(file.toPath());
    }

    @Override
    public boolean worldExists(String worldName) {
        File file = resolve(worldName);
        return file != null && file.exists();
    }

    @Override
    public List<String> listWorlds() throws IOException {
        List<String> names = new ArrayList<>();

        File[] entries = root.listFiles();
        if (entries == null) {
            return names;
        }

        for (File entry : entries) {
            if (entry.isFile() && entry.getName().endsWith(EXTENSION)) {
                names.add(trimExtension(entry.getName()));
                continue;
            }
            if (!entry.isDirectory()) {
                continue;
            }
            UUID owner = parseUuid(entry.getName());
            if (owner == null) {
                continue;
            }
            collectOwner(owner, entry, names);
        }
        return names;
    }

    private void collectOwner(UUID owner, File ownerDir, List<String> names) {
        File[] children = ownerDir.listFiles(File::isDirectory);
        if (children == null) {
            return;
        }

        for (File child : children) {
            if (child.getName().equals(PUBLISHED_DIR)) {
                File[] levels = child.listFiles(File::isDirectory);
                if (levels == null) {
                    continue;
                }
                for (File level : levels) {
                    for (String map : slimeFiles(level)) {
                        names.add(publishedName(owner, level.getName(), map));
                    }
                }
                continue;
            }
            if (new File(child, child.getName() + EXTENSION).exists()) {
                names.add(editName(owner, child.getName()));
            }
        }
    }

    private List<String> slimeFiles(File directory) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(EXTENSION));
        List<String> names = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                names.add(trimExtension(file.getName()));
            }
        }
        return names;
    }

    @Override
    public void saveWorld(String worldName, byte[] serializedWorld) throws IOException {
        File file = resolve(worldName);
        if (file == null) {
            throw new IOException("Nom de monde refuse : " + worldName);
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Impossible de creer " + parent);
        }
        Files.write(file.toPath(), serializedWorld);
    }

    @Override
    public void deleteWorld(String worldName) throws UnknownWorldException, IOException {
        File file = resolve(worldName);
        if (file == null || !file.exists()) {
            throw new UnknownWorldException(worldName);
        }
        Files.delete(file.toPath());
    }

    /** Fichier correspondant a ce nom de monde, ou {@code null} si le nom est refuse. */
    public File resolve(String worldName) {
        if (worldName == null || worldName.isBlank()
                || worldName.contains("/") || worldName.contains("\\") || worldName.contains("..")) {
            return null;
        }

        String[] parts = worldName.split("\\.");
        if (parts.length == 3 && parts[0].equals(EDIT)) {
            UUID owner = fromHex(parts[1]);
            if (owner != null && isValidSlug(parts[2])) {
                return new File(mapDirectory(owner, parts[2]), parts[2] + EXTENSION);
            }
        }
        if (parts.length == 4 && parts[0].equals(PUBLISHED)) {
            UUID owner = fromHex(parts[1]);
            if (owner != null && isValidSlug(parts[2]) && isValidSlug(parts[3])) {
                return new File(publishedDirectory(owner, parts[2]), parts[3] + EXTENSION);
            }
        }
        return new File(root, worldName + EXTENSION);
    }

    private static String trimExtension(String fileName) {
        return fileName.substring(0, fileName.length() - EXTENSION.length());
    }

    private static String hex(UUID uuid) {
        return uuid.toString().replace("-", "");
    }

    private static UUID fromHex(String hex) {
        if (hex == null || !HEX32.matcher(hex).matches()) {
            return null;
        }
        return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20));
    }

    private static UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Supprime un dossier et tout ce qu'il contient. */
    public static void deleteTree(File directory) throws IOException {
        if (!directory.exists()) {
            return;
        }
        try (Stream<Path> walk = Files.walk(directory.toPath())) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            throw new IOException(e.getCause() == null ? e : e.getCause());
        }
    }
}
