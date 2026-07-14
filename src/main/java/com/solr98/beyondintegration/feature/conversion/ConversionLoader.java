package com.solr98.beyondintegration.feature.conversion;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.solr98.beyondintegration.feature.conversion.model.ConversionRecipe;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.util.*;

public class ConversionLoader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FOLDER = "conversion";
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(ConversionRecipe.class, new ConversionRecipe.Deserializer())
            .create();

    private static final Map<ResourceLocation, List<ConversionRecipe>> produceIndex = new HashMap<>();
    private static final List<ConversionRecipe> allRecipes = new ArrayList<>();
    private static int lastLoadedCount = 0;

    @Override
    protected Map<ResourceLocation, JsonObject> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonObject> map = new HashMap<>();

        var resources = resourceManager.listResources(FOLDER, path ->
                path.getPath().endsWith(".json") && path.getPath().contains("/"));

        for (var entry : resources.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try (var reader = new InputStreamReader(entry.getValue().open())) {
                JsonObject obj = GSON.fromJson(reader, JsonObject.class);
                if (obj != null) {
                    map.put(fileId, obj);
                } else {
                    LOGGER.warn("[ConversionLoader] Empty JSON: {}", fileId);
                }
            } catch (Exception e) {
                LOGGER.warn("[ConversionLoader] Failed to read {}: {}", fileId, e.getMessage());
            }
        }
        return map;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonObject> prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        produceIndex.clear();
        allRecipes.clear();

        for (var entry : prepared.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            JsonObject obj = entry.getValue();

            String path = fileId.getPath(); // e.g. "conversion/fill_water_bucket.json"
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            String recipeId = fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - 5) : fileName;

            try {
                obj.addProperty("id", fileId.getNamespace() + ":" + recipeId);
                ConversionRecipe recipe = GSON.fromJson(obj, ConversionRecipe.class);
                register(recipe);
            } catch (Exception e) {
                LOGGER.warn("[ConversionLoader] Failed to parse recipe {}: {}", fileId, e.getMessage());
            }
        }

        validateNoChaining();
        lastLoadedCount = allRecipes.size();

        if (lastLoadedCount > 0) {
            LOGGER.info("[ConversionLoader] Loaded {} conversion recipes", lastLoadedCount);
        }
    }

    private static void register(ConversionRecipe recipe) {
        allRecipes.add(recipe);
        ResourceLocation outId = recipe.getOutput().getId();
        produceIndex.computeIfAbsent(outId, k -> new ArrayList<>()).add(recipe);
    }

    public static List<ConversionRecipe> getRecipesFor(ResourceLocation outputId) {
        return produceIndex.getOrDefault(outputId, List.of());
    }

    public static List<ConversionRecipe> getAllRecipes() {
        return allRecipes;
    }

    public static int getLoadedCount() {
        return lastLoadedCount;
    }

    private static void validateNoChaining() {
        if (allRecipes.size() <= 1) return;

        Set<ResourceLocation> producedItems = new HashSet<>();
        for (ConversionRecipe r : allRecipes) {
            producedItems.add(r.getOutput().getId());
        }

        boolean warned = false;
        for (ConversionRecipe recipe : allRecipes) {
            for (ConversionRecipe.Ingredient ing : recipe.getInput()) {
                if (ing.isItem()) {
                    ResourceLocation id = ResourceLocation.tryParse(ing.getItem());
                    if (id != null && producedItems.contains(id) && !id.equals(recipe.getOutput().getId())) {
                        LOGGER.warn("[ConversionLoader] Recipe '{}' requires '{}', which is also produced by another conversion recipe. Chained/nested conversion is NOT supported.",
                                recipe.getId(), id);
                        warned = true;
                    }
                }
            }
        }

        if (warned) {
            LOGGER.warn("[ConversionLoader] Conversion recipes must consume from the network directly, not from the output of other conversion recipes.");
        }
    }
}
