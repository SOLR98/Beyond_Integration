package com.solr98.beyondintegration.mixin;

import com.solr98.beyondintegration.cache.NetworkItemCache;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestNetworkItemsPacket;
import com.solr98.beyondintegration.network.TaczCraftPacket;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GunSmithTableScreen.class, remap = false)
public abstract class GunSmithTableScreenMixin extends AbstractContainerScreen<GunSmithTableMenu> {

    protected GunSmithTableScreenMixin(GunSmithTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Unique
    private static boolean beyond$loaded = false;
    @Unique
    private static boolean beyond$hasTaczAddon;
    @Unique
    private static boolean beyond$compatChecked = false;
    @Unique
    private boolean beyond$useNetwork = true;

    @Shadow(remap = false)
    private Int2IntArrayMap playerIngredientCount;

    @Shadow(remap = false)
    private GunSmithTableRecipe selectedRecipe;

    @Shadow(remap = false)
    private void getPlayerIngredientCount(GunSmithTableRecipe recipe) {}

    @Unique
    private boolean beyond$outputToNetwork = false;
    @Unique
    private int beyond$netVersion = -1;
    @Unique
    private Button beyond$outputBtn;

    @Unique
    private static final ResourceLocation beyond$tex =
            ResourceLocation.tryParse("tacz:textures/gui/gun_smith_table.png");

    @Unique
    private void beyond$drawButton(GuiGraphics g, int x, int y, int mx, int my, ItemStack icon) {
        boolean hover = mx >= x && mx < x + 18 && my >= y && my < y + 18;
        int v = hover ? 164 + 18 : 164;
        g.blit(beyond$tex, x, y, 18, 18, 138, v, 48, 18, 256, 256);
        if (!icon.isEmpty()) g.renderFakeItem(icon, x + 1, y + 1);
    }

    @Unique
    private static java.io.File beyond$prefsFile;

    @Unique
    private void beyond$loadPrefs() {
        try {
            if (beyond$prefsFile == null) beyond$prefsFile = new java.io.File(
                    Minecraft.getInstance().gameDirectory, "config/BeyondIntegration_gui.properties");
            if (beyond$prefsFile.exists()) {
                var props = new java.util.Properties();
                try (var in = new java.io.FileInputStream(beyond$prefsFile)) {
                    props.load(in);
                    beyond$useNetwork = Boolean.parseBoolean(props.getProperty("useNetwork", "true"));
                    beyond$outputToNetwork = Boolean.parseBoolean(props.getProperty("outputToNetwork", "false"));
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Unique
    private void beyond$savePrefs() {
        try {
            if (beyond$prefsFile == null) beyond$prefsFile = new java.io.File(
                    Minecraft.getInstance().gameDirectory, "config/BeyondIntegration_gui.properties");
            beyond$prefsFile.getParentFile().mkdirs();
            var props = new java.util.Properties();
            props.setProperty("useNetwork", String.valueOf(beyond$useNetwork));
            props.setProperty("outputToNetwork", String.valueOf(beyond$outputToNetwork));
            try (var out = new java.io.FileOutputStream(beyond$prefsFile)) {
                props.store(out, "Beyond Cmd Extension GUI Preferences");
            }
        } catch (Exception ignored) {
        }
    }

    @Unique
    private int[] beyond$cachedNetworkCounts;
    @Unique
    private String beyond$lastRecipeKey;

    @Unique
    private static ItemStack beyond$debugIcon = ItemStack.EMPTY;

    @Unique
    private static boolean beyond$isTaczAddonLoaded() {
        if (!beyond$compatChecked) {
            beyond$compatChecked = true;
            try {
                beyond$hasTaczAddon = ModList.get() != null && ModList.get().isLoaded("taczaddon");
            } catch (Exception e) {
                beyond$hasTaczAddon = false;
            }
        }
        return beyond$hasTaczAddon;
    }

    @Unique
    private static void beyond$initIcons() {
        if (!beyond$debugIcon.isEmpty()) return;
        try {
            var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse("beyonddimensions:net_creater"));
            if (item != null) beyond$debugIcon = new ItemStack(item);
        } catch (Exception ignored) {
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"), remap = true)
    private void onScreenClose(CallbackInfo ci) {
        beyond$savePrefs();
    }

    @Inject(method = "render", at = @At("TAIL"), remap = true)
    private void onRenderTick(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!beyond$loaded) {
            beyond$loaded = true;
            beyond$loadPrefs();
        }

        beyond$initIcons();
        var self = (GunSmithTableScreen) (Object) this;
        var font = Minecraft.getInstance().font;
        int left = self.getGuiLeft(), top = self.getGuiTop();

        int netColor;
        Component netText;
        if (NetworkItemCache.hasNetwork()) {
            int id = NetworkItemCache.getNetId();
            netText = Component.translatable("gui.beyond_integration.network.connected", id >= 0 ? id : "?");
            netColor = 0x55FFFF;
        } else {
            netText = Component.translatable("gui.beyond_integration.network.none");
            netColor = 0x888888;
        }
        graphics.drawString(font, netText, left + 254, top + 33, netColor, false);

        {
            if (beyond$isTaczAddonLoaded()) {
                var modeIcon = beyond$useNetwork
                        ? new ItemStack(net.minecraft.world.item.Items.ENDER_EYE)
                        : new ItemStack(net.minecraft.world.item.Items.CRAFTING_TABLE);
                beyond$drawButton(graphics, left + 322, top + 50, mouseX, mouseY, modeIcon);
            }

            if (beyond$useNetwork) {
                var outIcon = beyond$outputToNetwork ? beyond$debugIcon : new ItemStack(net.minecraft.world.item.Items.CHEST);
                beyond$drawButton(graphics, left + 267, top + 162, mouseX, mouseY, outIcon);
            }
        }
    }

    @Inject(method = "renderIngredient", at = @At("RETURN"))
    private void onRenderIngredient(GuiGraphics graphics, CallbackInfo ci) {
        if (!beyond$useNetwork) return;
        if (!NetworkItemCache.hasNetwork()) return;
        if (selectedRecipe == null) return;
        var inputs = selectedRecipe.getInputs();
        if (inputs == null || inputs.isEmpty()) return;

        int netVer = NetworkItemCache.getVersion();
        if (beyond$cachedNetworkCounts == null || beyond$netVersion != netVer) {
            if (NetworkItemCache.isEmpty()) return;
            beyond$netVersion = netVer;
            beyond$cachedNetworkCounts = calcNetworkCounts(selectedRecipe);
            beyond$lastRecipeKey = selectedRecipe.getId().toString();
        }

        var font = Minecraft.getInstance().font;
        var screen = (GunSmithTableScreen) (Object) this;
        int idx = 0;
        for (int i = 0; i < 6 && idx < inputs.size(); i++) {
            for (int j = 0; j < 2 && idx < inputs.size(); j++) {
                if (idx >= beyond$cachedNetworkCounts.length) break;
                int netCount = beyond$cachedNetworkCounts[idx];
                if (netCount > 0) {
                    int offsetX = screen.getGuiLeft() + 254 + 45 * j;
                    int offsetY = screen.getGuiTop() + 62 + 17 * i;
                    var pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(0, 0, 250);
                    pose.scale(0.5f, 0.5f, 1);
                    graphics.drawString(font, "+" + netCount, (offsetX + 17) * 2, (offsetY + 5) * 2, 0x55FFFF, false);
                    pose.popPose();
                }
                idx++;
            }
        }
    }

    @Inject(method = "getPlayerIngredientCount", at = @At("RETURN"))
    private void addNetworkCounts(GunSmithTableRecipe recipe, CallbackInfo ci) {
        if (!beyond$useNetwork) return;
        if (recipe == null || playerIngredientCount == null) return;

        PacketHandler.sendToServer(new RequestNetworkItemsPacket());

        if (ModList.get().isLoaded("taczaddon")) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                var realInv = player.getInventory();
                var inputs = recipe.getInputs();
                if (inputs != null) {
                    int size = Math.min(inputs.size(), playerIngredientCount.size());
                    for (int i = 0; i < size; i++) {
                        var ing = inputs.get(i).getIngredient();
                        int realCount = 0;
                        for (var stack : realInv.items) {
                            if (!stack.isEmpty() && ing.test(stack)) realCount += stack.getCount();
                        }
                        playerIngredientCount.put(i, realCount);
                    }
                }
            }
        }

        if (!NetworkItemCache.hasNetwork()) return;
        if (NetworkItemCache.isEmpty()) return;

        int netVer = NetworkItemCache.getVersion();
        String recipeKey = recipe.getId().toString();
        boolean cacheStale = !recipeKey.equals(beyond$lastRecipeKey) || beyond$netVersion != netVer;

        if (cacheStale) {
            beyond$netVersion = netVer;
            beyond$lastRecipeKey = recipeKey;
            beyond$cachedNetworkCounts = calcNetworkCounts(recipe);
        }

        int max = Math.min(beyond$cachedNetworkCounts.length, playerIngredientCount.size());
        for (int i = 0; i < max; i++) {
            int net = beyond$cachedNetworkCounts[i];
            if (net > 0) {
                long before = playerIngredientCount.get(i);
                long after = Math.min(before + net, Integer.MAX_VALUE);
                playerIngredientCount.put(i, (int) after);
            }
        }
    }

    @Inject(method = "addCraftButton", at = @At("TAIL"))
    private void onAddCraftButton(CallbackInfo ci) {
        var self = (GunSmithTableScreen) (Object) this;
        int left = self.getGuiLeft(), top = self.getGuiTop();

        if (beyond$isTaczAddonLoaded()) {
            var modeBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
                beyond$useNetwork = !beyond$useNetwork;
                if (beyond$outputBtn != null) beyond$outputBtn.visible = beyond$useNetwork;
                if (selectedRecipe != null) getPlayerIngredientCount(selectedRecipe);
                b.setTooltip(Tooltip.create(Component.translatable(
                        beyond$useNetwork ? "gui.beyond_integration.mode.network" : "gui.beyond_integration.mode.vanilla")));
            }).bounds(left + 322, top + 50, 18, 18).build());
            modeBtn.setTooltip(Tooltip.create(Component.translatable("gui.beyond_integration.mode.network")));
        }

        beyond$outputBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            beyond$outputToNetwork = !beyond$outputToNetwork;
            b.setTooltip(Tooltip.create(Component.translatable(beyond$outputToNetwork
                    ? "gui.beyond_integration.output.network"
                    : "gui.beyond_integration.output.inventory",
                    NetworkItemCache.getNetId() >= 0 ? NetworkItemCache.getNetId() : "?")));
        }).bounds(left + 267, top + 162, 18, 18).build());
        beyond$outputBtn.setTooltip(Tooltip.create(Component.translatable(beyond$outputToNetwork
                ? "gui.beyond_integration.output.network"
                : "gui.beyond_integration.output.inventory",
                NetworkItemCache.getNetId() >= 0 ? NetworkItemCache.getNetId() : "?")));
        beyond$outputBtn.visible = beyond$useNetwork;
    }

    @Redirect(method = "lambda$addCraftButton$5",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraftforge/network/simple/SimpleChannel;sendToServer(Ljava/lang/Object;)V",
                       remap = false),
              remap = false)
    private void onCraftButtonClick(net.minecraftforge.network.simple.SimpleChannel channel, Object message) {
        if (!beyond$useNetwork || !NetworkItemCache.hasNetwork()) {
            channel.sendToServer(message);
            return;
        }
        int count = Screen.hasShiftDown() ? 64 : 1;
        PacketHandler.sendToServer(new TaczCraftPacket(selectedRecipe.getId(), count, beyond$outputToNetwork));
    }

    @Unique
    private int[] calcNetworkCounts(GunSmithTableRecipe recipe) {
        var inputs = recipe.getInputs();
        if (inputs == null || inputs.isEmpty()) return new int[0];
        int size = inputs.size();
        int[] counts = new int[size];
        for (int i = 0; i < size; i++) {
            String key = recipe.getId().toString() + "|" + i;
            long raw = NetworkItemCache.getCount(key);
            counts[i] = (int) Math.min(raw, Integer.MAX_VALUE);
        }
        return counts;
    }
}
