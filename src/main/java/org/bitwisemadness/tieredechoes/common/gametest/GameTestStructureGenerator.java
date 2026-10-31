package org.bitwisemadness.tieredechoes.common.gametest;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

/**
 * Writes the empty structure templates that this mod's GameTests are named after.
 *
 * <p>Vanilla derives a GameTest's structure id from its declaring class and method name —
 * {@code tieredechoes:<className>.<methodName>}, both lowercased — and loads it from
 * {@code data/tieredechoes/structures/<id>.nbt}. A missing file crashes the game test server
 * before a single test runs.</p>
 *
 * <p>Every test here asserts on code rather than on blocks, so one identical all-air box is
 * enough for all of them; only the file name differs. Ids come from
 * {@link #discoverStructureIds}, which the drift test calls as well, so the two can no longer
 * disagree about which tests exist — they used to carry separate copies of this regex.</p>
 *
 * <p>NBT is written through {@link NbtIo} rather than encoded by hand. The former script wrote
 * every byte itself, which meant a mistyped tag id desynchronised the reader for every entry
 * after it and surfaced later as an unrelated EOFException.</p>
 *
 * <p>Run with {@code gradlew generateGameTestStructures}.</p>
 */
public final class GameTestStructureGenerator {

    /** Empty in every axis; nothing is ever placed. */
    static final int DEFAULT_SIZE = 5;

    /** 1.20.1 — see {@code SharedConstants.WORLD_VERSION}. */
    static final int DATA_VERSION = 3465;

    /**
     * Mirrors {@code GameTestRegistry}: {@code <simple class name>.<method name>}, both lowercased.
     */
    private static final Pattern CLASS_DECLARATION =
            Pattern.compile("^\\s*public\\s+(?:final\\s+)?class\\s+(\\w+)", Pattern.MULTILINE);

    private static final Pattern GAME_TEST_METHOD =
            Pattern.compile("@GameTest(?:\\s*\\([^)]*\\))?\\s+(?:public\\s+)?static\\s+void\\s+(\\w+)");

    private static final String GAMETEST_PACKAGE =
            "src/main/java/org/bitwisemadness/tieredechoes/common/gametest";

    private static final String STRUCTURES_PACKAGE =
            "src/main/resources/data/tieredechoes/structures";

    private GameTestStructureGenerator() {
    }

    public static void main(final String[] args) throws IOException {
        final Path projectRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        final Path outputDir = args.length > 1
                ? Paths.get(args[1])
                : projectRoot.resolve(STRUCTURES_PACKAGE);

        final SortedSet<String> ids = discoverStructureIds(projectRoot);
        if (ids.isEmpty()) {
            throw new IllegalStateException(
                    "No @GameTest methods found under " + projectRoot.resolve(GAMETEST_PACKAGE)
                            + " — nothing to generate.");
        }

        Files.createDirectories(outputDir);
        int generated = 0;
        int unchanged = 0;
        for (final String id : ids) {
            final Path destination = outputDir.resolve(id + ".nbt");
            if (Files.exists(destination)) {
                unchanged++;
                continue;
            }
            writeStructure(destination, DEFAULT_SIZE, DATA_VERSION);
            System.out.println("generated  " + id + ".nbt");
            generated++;
        }

        System.out.println("structure templates: " + ids.size()
                + " (" + generated + " written, " + unchanged + " already present)");
    }

    /**
     * The structure ids of every {@code @GameTest} in the mod, sorted and deduplicated.
     *
     * <p>Reads the sources rather than loading the classes, so it works before anything is
     * compiled and cannot be skewed by a class failing to load.</p>
     */
    public static SortedSet<String> discoverStructureIds(final Path projectRoot) throws IOException {
        final Path packageDir = projectRoot.resolve(GAMETEST_PACKAGE);
        if (!Files.isDirectory(packageDir)) {
            return new TreeSet<>();
        }

        final SortedSet<String> ids = new TreeSet<>();
        final List<Path> sources;
        try (var files = Files.list(packageDir)) {
            sources = files.filter(path -> {
                final Path fileName = path.getFileName();
                return fileName != null && fileName.toString().endsWith(".java");
            }).toList();
        }

        for (final Path source : sources) {
            final String text = Files.readString(source);
            final Matcher classMatcher = CLASS_DECLARATION.matcher(text);
            if (!classMatcher.find()) {
                continue;
            }
            final String className = classMatcher.group(1).toLowerCase(Locale.ROOT);

            final Matcher methods = GAME_TEST_METHOD.matcher(text);
            while (methods.find()) {
                ids.add(className + "." + methods.group(1).toLowerCase(Locale.ROOT));
            }
        }
        return ids;
    }

    /**
     * Writes one all-air box of the given size.
     *
     * <p>The layout has to match what {@code StructureTemplate#load} reads:
     * {@code size} is a TAG_List of three TAG_Int rather than an int array, and {@code palette}
     * holds one compound naming the single block entry. {@code blocks} and {@code entities} stay
     * empty because nothing is placed.</p>
     */
    private static void writeStructure(final Path destination, final int size, final int dataVersion)
            throws IOException {
        final CompoundTag root = new CompoundTag();

        final ListTag dimensions = new ListTag();
        for (int axis = 0; axis < 3; axis++) {
            dimensions.add(IntTag.valueOf(size));
        }
        root.put("size", dimensions);

        root.put("entities", new ListTag());
        root.put("blocks", new ListTag());

        final ListTag palette = new ListTag();
        final CompoundTag air = new CompoundTag();
        air.putString("Name", "minecraft:air");
        palette.add(air);
        root.put("palette", palette);

        root.putInt("DataVersion", dataVersion);

        try (final OutputStream out = Files.newOutputStream(destination)) {
            NbtIo.writeCompressed(root, out);
        }
    }

    /** Exposed for the drift test, which needs to know what a clean run should have found. */
    static List<String> sortedIds(final Path projectRoot) {
        final SortedSet<String> ids;
        try {
            ids = discoverStructureIds(projectRoot);
        } catch (final IOException e) {
            throw new IllegalStateException("Could not read the GameTest sources", e);
        }
        return List.copyOf(ids);
    }
}
