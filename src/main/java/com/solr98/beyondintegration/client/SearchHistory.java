package com.solr98.beyondintegration.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.solr98.beyondintegration.ClientConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 终端搜索框历史记录（客户端，跨会话保存）。
 * 存于 {@code config/beyond_integration_search_history.json}；重复项上移，条数上限取客户端配置。
 */
public final class SearchHistory {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("beyond_integration_search_history.json");
    private static List<String> entries;

    private SearchHistory() {}

    private static void ensureLoaded() {
        if (entries != null) return;
        entries = new ArrayList<>();
        try {
            if (Files.exists(FILE)) {
                try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
                    List<String> data = GSON.fromJson(r, new TypeToken<List<String>>() {}.getType());
                    if (data != null) entries.addAll(data);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 历史列表（最新在前）。 */
    public static List<String> list() {
        ensureLoaded();
        return entries;
    }

    /** 记录一条搜索（去重上移，按配置截断），并保存。 */
    public static void add(String text) {
        if (text == null) return;
        String t = text.trim();
        if (t.isEmpty()) return;
        ensureLoaded();
        entries.remove(t);
        entries.add(0, t);
        int max = Math.max(1, ClientConfig.searchHistoryMax());
        while (entries.size() > max) entries.remove(entries.size() - 1);
        save();
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
                GSON.toJson(entries, w);
            }
        } catch (Throwable ignored) {
        }
    }
}
