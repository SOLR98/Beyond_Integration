package com.solr98.beyondintegration.mixin;
import com.solr98.beyondintegration.client.NetworkItemCache;
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
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

@Mixin(targets = "com.tacz.guns.client.gui.GunSmithTableScreen", remap = false)
public abstract class GunSmithTableScreenMixin extends AbstractContainerScreen<GunSmithTableMenu> {

    protected GunSmithTableScreenMixin(GunSmithTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Unique private boolean beyond$useNetwork = true;
    @Unique private boolean beyond$outputToNetwork = false;
    @Unique private int beyond$netVersion = -1;
    @Unique private int[] beyond$cachedNetworkCounts;
    @Unique private String beyond$lastRecipeKey;
    @Shadow(remap = false) private @Nullable Int2IntArrayMap playerIngredientCount;
    @Shadow(remap = false) private @Nullable RecipeHolder<GunSmithTableRecipe> selectedRecipe;
    @Shadow(remap = false) private void getPlayerIngredientCount(RecipeHolder<GunSmithTableRecipe> holder) {}

    @Unique private static final ResourceLocation beyond$tex = ResourceLocation.parse("tacz:textures/gui/gun_smith_table.png");
    @Unique private static ItemStack beyond$netIcon = ItemStack.EMPTY;
    @Unique private static File beyond$prefsFile;
    @Unique private static boolean beyond$loaded = false;
    @Unique
    private void beyond$loadPrefs() {
        try {
            if (beyond$prefsFile == null)
                beyond$prefsFile = new File(Minecraft.getInstance().gameDirectory, "config/beyond_integration_smith.properties");
            if (beyond$prefsFile.exists()) {
                var props = new Properties();
                try (var in = new FileInputStream(beyond$prefsFile)) {
                    props.load(in);
                    beyond$useNetwork = Boolean.parseBoolean(props.getProperty("useNetwork", "true"));
                    beyond$outputToNetwork = Boolean.parseBoolean(props.getProperty("outputToNetwork", "false"));
                }
            }
        } catch (Exception ignored) {}
    }

    @Unique
    private void beyond$savePrefs() {
        try {
            if (beyond$prefsFile == null)
                beyond$prefsFile = new File(Minecraft.getInstance().gameDirectory, "config/beyond_integration_smith.properties");
            beyond$prefsFile.getParentFile().mkdirs();
            var props = new Properties();
            props.setProperty("useNetwork", String.valueOf(beyond$useNetwork));
            props.setProperty("outputToNetwork", String.valueOf(beyond$outputToNetwork));
            try (var out = new FileOutputStream(beyond$prefsFile)) { props.store(out, "Beyond Integration Smith GUI"); }
        } catch (Exception ignored) {}
    }

    @Unique
    private static void beyond$initIcons() {
        if (!beyond$netIcon.isEmpty()) return;
        try {
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("beyonddimensions:net_creater"));
            if (item != null) beyond$netIcon = new ItemStack(item);
        } catch (Exception ignored) {}
    }

    @Unique
    private void beyond$drawButton(GuiGraphics g, int x, int y, int mx, int my, ItemStack icon) {
        boolean hover = mx >= x && mx < x + 18 && my >= y && my < y + 18;
        int v = hover ? 182 : 164;
        g.blit(beyond$tex, x, y, 18, 18, 138, v, 48, 18, 256, 256);
        if (!icon.isEmpty()) g.renderFakeItem(icon, x + 1, y + 1);
    }

    @Unique
    private void beyond$refreshAll() {
        if (selectedRecipe != null) getPlayerIngredientCount(selectedRecipe);
    }

    @Inject(method = "render", at = @At("TAIL"), remap = true)
    private void beyond$onRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!beyond$loaded) { beyond$loaded = true; beyond$loadPrefs(); }
        beyond$initIcons();

        var self = (GunSmithTableScreen) (Object) this;
        var font = Minecraft.getInstance().font;
        int left = self.getGuiLeft(), top = self.getGuiTop();

        Component netText = NetworkItemCache.getDisplayName();
        graphics.drawString(font, netText, left + 254, top + 33,
                NetworkItemCache.hasNetwork() ? 0x55FFFF : 0x888888, false);

        if (NetworkItemCache.hasNetwork()) {
            var modeIcon = beyond$useNetwork
                    ? new ItemStack(net.minecraft.world.item.Items.ENDER_EYE)
                    : new ItemStack(net.minecraft.world.item.Items.CRAFTING_TABLE);
            beyond$drawButton(graphics, left + 322, top + 50, mouseX, mouseY, modeIcon);
        }

        if (beyond$useNetwork && NetworkItemCache.hasNetwork()) {
            var outIcon = beyond$outputToNetwork ? beyond$netIcon : new ItemStack(net.minecraft.world.item.Items.CHEST);
            beyond$drawButton(graphics, left + 267, top + 162, mouseX, mouseY, outIcon);
        }
    }

    @Inject(method = "init", at = @At("TAIL"), remap = true)
    private void beyond$onInit(CallbackInfo ci) {
        var self = (GunSmithTableScreen) (Object) this;
        int left = self.getGuiLeft(), top = self.getGuiTop();

        this.addWidget(new BeyondToggleWidget(left + 322, top + 50, () -> {
            beyond$useNetwork = !beyond$useNetwork;
            beyond$refreshAll();
        }));
        this.addWidget(new BeyondToggleWidget(left + 267, top + 162, () -> {
            if (!beyond$useNetwork || !NetworkItemCache.hasNetwork()) return;
            beyond$outputToNetwork = !beyond$outputToNetwork;
        }));
    }

    @Inject(method = "renderIngredient", at = @At("RETURN"))
    private void beyond$onRenderIngredient(GuiGraphics graphics, CallbackInfo ci) {
        if (!beyond$useNetwork) return;
        if (!NetworkItemCache.hasNetwork()) return;
        if (selectedRecipe == null) return;
        var inputs = selectedRecipe.value().getInputs();
        if (inputs == null || inputs.isEmpty()) return;

        int netVer = NetworkItemCache.getVersion();
        if (beyond$cachedNetworkCounts == null || beyond$netVersion != netVer) {
            if (NetworkItemCache.isEmpty()) return;
            beyond$netVersion = netVer;
            beyond$cachedNetworkCounts = beyond$calcNetworkCounts(selectedRecipe.id().toString(), selectedRecipe.value());
            beyond$lastRecipeKey = selectedRecipe.id().toString();
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
    private void beyond$addNetworkCounts(RecipeHolder<GunSmithTableRecipe> holder, CallbackInfo ci) {
        if (!beyond$useNetwork) return;
        if (playerIngredientCount == null) return;

        if (ModList.get() != null && ModList.get().isLoaded("taczaddon")) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                var realInv = player.getInventory();
                var recipe = holder.value();
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

        PacketHandler.sendToServer(new RequestNetworkItemsPacket());

        if (!NetworkItemCache.hasNetwork() || NetworkItemCache.isEmpty()) return;

        var recipe = holder.value();
        int netVer = NetworkItemCache.getVersion();
        String recipeKey = holder.id().toString();
        boolean cacheStale = !recipeKey.equals(beyond$lastRecipeKey) || beyond$netVersion != netVer;

        if (cacheStale) {
            beyond$netVersion = netVer;
            beyond$lastRecipeKey = recipeKey;
            beyond$cachedNetworkCounts = beyond$calcNetworkCounts(holder.id().toString(), recipe);
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

    @ModifyArg(method = "addCraftButton",
               at = @At(value = "INVOKE",
                        target = "Lcom/tacz/guns/client/gui/components/smith/ImageButton;<init>(IIIIIIILnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/gui/components/Button$OnPress;)V"),
               index = 8,
               remap = false)
    private Button.OnPress beyond$wrapOnPress(Button.OnPress original) {
        return b -> {
            if (!beyond$useNetwork || !NetworkItemCache.hasNetwork()) { original.onPress(b); return; }
            if (selectedRecipe == null || playerIngredientCount == null) { original.onPress(b); return; }
            int count = beyond$getCraftCount();
            PacketHandler.sendToServer(new TaczCraftPacket(selectedRecipe.id(), count, beyond$outputToNetwork));
            beyond$refreshAll();
        };
    }

    @Unique
    private int beyond$getCraftCount() {
        int requested = Screen.hasShiftDown() ? 64 : 1;
        if (playerIngredientCount == null || selectedRecipe == null) return 1;
        var inputs = selectedRecipe.value().getInputs();
        if (inputs == null || inputs.isEmpty()) return 1;
        int max = requested;
        for (int i = 0; i < inputs.size() && i < playerIngredientCount.size(); i++) {
            int have = playerIngredientCount.get(i);
            int need = inputs.get(i).getCount();
            if (need <= 0) continue;
            max = Math.min(max, have / need);
        }
        return Math.max(max, 1);
    }

    @Inject(method = "onClose", at = @At("HEAD"), remap = true)
    private void beyond$onClose(CallbackInfo ci) {
        beyond$savePrefs();
    }

    @Unique
    private int[] beyond$calcNetworkCounts(String recipeId, GunSmithTableRecipe recipe) {
        var inputs = recipe.getInputs();
        if (inputs == null || inputs.isEmpty()) return new int[0];
        int[] counts = new int[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) {
            counts[i] = (int) Math.min(NetworkItemCache.getCount(recipeId + "|" + i), Integer.MAX_VALUE);
        }
        return counts;
    }

    @Unique
    private static class BeyondToggleWidget implements GuiEventListener, Renderable, NarratableEntry {
        private final int x, y;
        private final Runnable onClick;
        private boolean focused;

        BeyondToggleWidget(int x, int y, Runnable onClick) {
            this.x = x;
            this.y = y;
            this.onClick = onClick;
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {}

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (btn != 0) return false;
            if (mx < x || mx >= x + 18 || my < y || my >= y + 18) return false;
            onClick.run();
            return true;
        }

        @Override
        public void setFocused(boolean focused) { this.focused = focused; }

        @Override
        public boolean isFocused() { return focused; }

        @Override
        public NarratableEntry.NarrationPriority narrationPriority() { return NarratableEntry.NarrationPriority.NONE; }

        @Override
        public void updateNarration(NarrationElementOutput output) {}
    }
}
