package com.solr98.beyondintegration.feature.conversion.model;

import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConversionRecipe {

    public static final String TYPE = "beyond_integration:conversion";

    private final String id;
    private final List<Ingredient> input;
    private final OutputItem output;
    private final List<NetworkOutput> outputNetwork;

    public ConversionRecipe(String id, List<Ingredient> input, OutputItem output, List<NetworkOutput> outputNetwork) {
        this.id = id;
        this.input = Collections.unmodifiableList(input);
        this.output = output;
        this.outputNetwork = outputNetwork != null ? Collections.unmodifiableList(outputNetwork) : List.of();
    }

    public String getId() { return id; }
    public List<Ingredient> getInput() { return input; }
    public OutputItem getOutput() { return output; }
    public List<NetworkOutput> getOutputNetwork() { return outputNetwork; }

    public static class Ingredient {
        private final String item;
        private final String tag;
        private final String fluid;
        private final int count;
        private final int amount;

        public Ingredient(String item, String tag, String fluid, int count, int amount) {
            this.item = item;
            this.tag = tag;
            this.fluid = fluid;
            this.count = count;
            this.amount = amount;
        }

        public boolean isItem() { return item != null; }
        public boolean isTag() { return tag != null; }
        public boolean isFluid() { return fluid != null; }

        public String getItem() { return item; }
        public String getTag() { return tag; }
        public String getFluid() { return fluid; }
        public int getCount() { return count; }
        public int getAmount() { return amount; }

        public long getCost() {
            return isFluid() ? amount : count;
        }
    }

    public static class OutputItem {
        private final ResourceLocation id;
        private final int count;

        public OutputItem(ResourceLocation id, int count) {
            this.id = id;
            this.count = count;
        }

        public ResourceLocation getId() { return id; }
        public int getCount() { return count; }
    }

    public static class NetworkOutput {
        private final boolean isItem;
        private final ResourceLocation id;
        private final int count;
        private final int amount;

        public NetworkOutput(boolean isItem, ResourceLocation id, int count, int amount) {
            this.isItem = isItem;
            this.id = id;
            this.count = count;
            this.amount = amount;
        }

        public boolean isItem() { return isItem; }
        public boolean isFluid() { return !isItem; }
        public ResourceLocation getId() { return id; }
        public int getCount() { return count; }
        public int getAmount() { return amount; }
        public long getCost() { return isItem ? count : amount; }
    }

    public static class Deserializer implements JsonDeserializer<ConversionRecipe> {
        @Override
        public ConversionRecipe deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();

            String recipeType = obj.has("type") ? obj.get("type").getAsString() : "";
            if (!TYPE.equals(recipeType)) {
                throw new JsonParseException("Expected type '" + TYPE + "', got '" + recipeType + "'");
            }

            String id = obj.get("id").getAsString();

            List<Ingredient> input = new ArrayList<>();
            JsonArray inputArr = obj.getAsJsonArray("input");
            for (JsonElement elem : inputArr) {
                input.add(parseIngredient(elem.getAsJsonObject()));
            }

            JsonObject outObj = obj.getAsJsonObject("output");
            String outItem = outObj.get("item").getAsString();
            int outCount = outObj.has("count") ? outObj.get("count").getAsInt() : 1;
            ResourceLocation outId = ResourceLocation.tryParse(outItem);
            if (outId == null) throw new JsonParseException("Invalid output item: " + outItem);
            OutputItem output = new OutputItem(outId, outCount);

            List<NetworkOutput> outputNetwork = new ArrayList<>();
            if (obj.has("outputNetwork")) {
                JsonArray netArr = obj.getAsJsonArray("outputNetwork");
                for (JsonElement elem : netArr) {
                    outputNetwork.add(parseNetworkOutput(elem.getAsJsonObject()));
                }
            }

            return new ConversionRecipe(id, input, output, outputNetwork);
        }

        private NetworkOutput parseNetworkOutput(JsonObject obj) {
            String type = obj.get("type").getAsString();
            String idStr = obj.get("id").getAsString();
            ResourceLocation rl = ResourceLocation.tryParse(idStr);
            if (rl == null) throw new JsonParseException("Invalid id: " + idStr);

            switch (type) {
                case "item" -> {
                    int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
                    return new NetworkOutput(true, rl, count, 0);
                }
                case "fluid" -> {
                    int amount = obj.has("amount") ? obj.get("amount").getAsInt() : 1000;
                    return new NetworkOutput(false, rl, 0, amount);
                }
                default -> throw new JsonParseException("outputNetwork type must be 'item' or 'fluid', got: " + type);
            }
        }

        private Ingredient parseIngredient(JsonObject obj) {
            if (obj.has("item")) {
                String item = obj.get("item").getAsString();
                int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
                return new Ingredient(item, null, null, count, 0);
            } else if (obj.has("tag")) {
                String tag = obj.get("tag").getAsString();
                int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
                return new Ingredient(null, tag, null, count, 0);
            } else if (obj.has("fluid")) {
                String fluid = obj.get("fluid").getAsString();
                int amount = obj.has("amount") ? obj.get("amount").getAsInt() : 1000;
                return new Ingredient(null, null, fluid, 1, amount);
            }
            throw new JsonParseException("Ingredient must have 'item', 'tag', or 'fluid' field");
        }
    }
}
