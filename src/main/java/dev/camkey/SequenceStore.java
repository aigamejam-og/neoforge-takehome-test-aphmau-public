package dev.camkey;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** In-memory sequences, persisted to <gameDir>/camkey/sequences.json on every change. */
public final class SequenceStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TYPE = new TypeToken<Map<String, List<Keyframe>>>() {}.getType();
    private static final Path FILE = FMLPaths.GAMEDIR.get().resolve("camkey").resolve("sequences.json");
    private static final Map<String, List<Keyframe>> DATA = new TreeMap<>();

    private SequenceStore() {}

    public static void load() {
        DATA.clear();
        if (!Files.exists(FILE)) return;
        try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            Map<String, List<Keyframe>> loaded = GSON.fromJson(r, TYPE);
            if (loaded != null) loaded.forEach((k, v) -> DATA.put(k, new ArrayList<>(v)));
            CamKeyMod.LOGGER.info("Loaded {} camera sequence(s)", DATA.size());
        } catch (IOException | JsonParseException e) {
            CamKeyMod.LOGGER.error("Failed to read {}", FILE, e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling("sequences.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(DATA, TYPE, w);
            }
            Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            CamKeyMod.LOGGER.error("Failed to write {}", FILE, e);
        }
    }

    public static int add(String name, Keyframe k) {
        List<Keyframe> list = DATA.computeIfAbsent(name, n -> new ArrayList<>());
        list.add(k);
        save();
        return list.size();
    }

    public static boolean pop(String name) {
        List<Keyframe> list = DATA.get(name);
        if (list == null || list.isEmpty()) return false;
        list.remove(list.size() - 1);
        if (list.isEmpty()) DATA.remove(name);
        save();
        return true;
    }

    public static boolean delete(String name) {
        boolean removed = DATA.remove(name) != null;
        if (removed) save();
        return removed;
    }

    public static List<Keyframe> get(String name) {
        List<Keyframe> list = DATA.get(name);
        return list == null ? null : Collections.unmodifiableList(list);
    }

    public static Iterable<String> names() {
        return new ArrayList<>(DATA.keySet());
    }

    public static Map<String, List<Keyframe>> all() {
        return Collections.unmodifiableMap(DATA);
    }
}
