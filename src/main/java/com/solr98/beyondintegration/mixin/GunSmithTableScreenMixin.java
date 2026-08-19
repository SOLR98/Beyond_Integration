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
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入 TACZ 的 {@link GunSmithTableScreen}（枪械工作台界面），
 * 扩展为支持维度网络材料：网络材料计入配方原料计数、额外显示网络数量徽标、
 * 新增“网络模式”与“产物入网络”切换按钮（偏好持久化到配置文件），
 * 并拦截合成按钮发包，使合成产物可选择输出到维度网络。
 */
@Mixin(value = GunSmithTableScreen.class, remap = false)
public abstract class GunSmithTableScreenMixin extends AbstractContainerScreen<GunSmithTableMenu> {

    protected GunSmithTableScreenMixin(GunSmithTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    /** 界面是否已初始化（延迟到首次渲染时加载偏好） */
    @Unique
    private static boolean beyond$loaded = false;
    /** 是否安装了 taczaddon 兼容模组 */
    @Unique
    private static boolean beyond$hasTaczAddon;
    /** 兼容模组是否已检查过 */
    @Unique
    private static boolean beyond$compatChecked = false;

    /** 影射：配方原料在当前玩家背包中的数量 */
    @Shadow(remap = false)
    private Int2IntArrayMap playerIngredientCount;

    /** 影射：当前选中的合成配方 */
    @Shadow(remap = false)
    private GunSmithTableRecipe selectedRecipe;

    /** 影射：原类刷新玩家原料数量的方法 */
    @Shadow(remap = false)
    private void getPlayerIngredientCount(GunSmithTableRecipe recipe) {}

    /** 网络物品缓存版本号（用于判定计数缓存失效） */
    @Unique
    private int beyond$netVersion = -1;
    /** “网络模式”切换按钮 */
    @Unique
    private Button beyond$modeBtn;
    /** “产物输出”切换按钮 */
    @Unique
    private Button beyond$outputBtn;

    /** 枪械工作台界面贴图 */
    @Unique
    private static final ResourceLocation beyond$tex =
            ResourceLocation.tryParse("tacz:textures/gui/gun_smith_table.png");

    /** 绘制 18x18 的切换按钮（含悬停高亮）并叠加物品图标 */
    @Unique
    private void beyond$drawButton(GuiGraphics g, int x, int y, int mx, int my, ItemStack icon) {
        boolean hover = mx >= x && mx < x + 18 && my >= y && my < y + 18;
        int v = hover ? 164 + 18 : 164;
        g.blit(beyond$tex, x, y, 18, 18, 138, v, 48, 18, 256, 256);
        if (!icon.isEmpty()) g.renderFakeItem(icon, x + 1, y + 1);
    }

    /** 从客户端配置加载 useNetwork / outputToNetwork 设置（CLIENT 类型，config 目录持久化） */
    @Unique
    private void beyond$loadPrefs() {
        try {
            com.solr98.beyondintegration.client.GunSmithNetMode.setNetworkMode(
                    com.solr98.beyondintegration.ClientConfig.taczSmithUseNetwork());
            com.solr98.beyondintegration.client.GunSmithNetMode.setOutputToNetwork(
                    com.solr98.beyondintegration.ClientConfig.taczSmithOutputToNetwork());
        } catch (Exception ignored) {
        }
    }

    /** 将当前 useNetwork / outputToNetwork 设置写入客户端配置并保存 */
    @Unique
    private void beyond$savePrefs() {
        try {
            com.solr98.beyondintegration.ClientConfig.setTaczSmithUseNetwork(
                    com.solr98.beyondintegration.client.GunSmithNetMode.isNetworkMode());
            com.solr98.beyondintegration.ClientConfig.setTaczSmithOutputToNetwork(
                    com.solr98.beyondintegration.client.GunSmithNetMode.isOutputToNetwork());
        } catch (Exception ignored) {
        }
    }

    /** 网络材料计数缓存（按配方输入下标），配切换配方或缓存版本变化时重建 */
    @Unique
    private int[] beyond$cachedNetworkCounts;
    /** 上次计算网络计数的配方 ID */
    @Unique
    private String beyond$lastRecipeKey;

    /** 产物输出按钮的物品图标（net_creater） */
    @Unique
    private static ItemStack beyond$debugIcon = ItemStack.EMPTY;

    /** 惰性检测是否安装了 taczaddon（只检查一次） */
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

    /** 初始化输出按钮图标（beyonddimensions:net_creater） */
    @Unique
    private static void beyond$initIcons() {
        if (!beyond$debugIcon.isEmpty()) return;
        try {
            var item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse("beyonddimensions:net_creater"));
            if (item != null) beyond$debugIcon = new ItemStack(item);
        } catch (Exception ignored) {
        }
    }

    /** 关闭界面时保存按钮偏好设置 */
    @Inject(method = "onClose", at = @At("HEAD"), remap = true)
    private void onScreenClose(CallbackInfo ci) {
        beyond$savePrefs();
    }

    /** 渲染末尾：绘制网络连接状态文本与模式/输出切换按钮（首次渲染时加载偏好并同步按钮提示） */
    @Inject(method = "render", at = @At("TAIL"), remap = true)
    private void onRenderTick(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!beyond$loaded) {
            beyond$loaded = true;
            beyond$loadPrefs();
            beyond$syncTooltips();
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
            var modeIcon = com.solr98.beyondintegration.client.GunSmithNetMode.isNetworkMode()
                    ? new ItemStack(net.minecraft.world.item.Items.ENDER_EYE)
                    : new ItemStack(net.minecraft.world.item.Items.CRAFTING_TABLE);
            beyond$drawButton(graphics, left + 322, top + 50, mouseX, mouseY, modeIcon);

            if (com.solr98.beyondintegration.client.GunSmithNetMode.isNetworkMode()) {
                var outIcon = com.solr98.beyondintegration.client.GunSmithNetMode.isOutputToNetwork() ? beyond$debugIcon : new ItemStack(net.minecraft.world.item.Items.CHEST);
                beyond$drawButton(graphics, left + 267, top + 162, mouseX, mouseY, outIcon);
            }
        }
    }

    /** 原料渲染后追加网络材料数量徽标（"+" 数量，缓存失效时先重新计算） */
    @Inject(method = "renderIngredient", at = @At("RETURN"))
    private void onRenderIngredient(GuiGraphics graphics, CallbackInfo ci) {
        if (!com.solr98.beyondintegration.client.GunSmithNetMode.isNetworkMode()) return;
        if (!NetworkItemCache.hasNetwork()) return;
        if (selectedRecipe == null) return;
        var inputs = selectedRecipe.getInputs();
        if (inputs == null || inputs.isEmpty()) return;

        int netVer = NetworkItemCache.getVersion();
        if (beyond$cachedNetworkCounts == null || beyond$netVersion != netVer) {
            if (NetworkItemCache.isEmpty()) return;
            beyond$netVersion = netVer;
                    beyond$cachedNetworkCounts = com.solr98.beyondintegration.client.GunSmithNetMode.calcNetworkCounts(selectedRecipe);
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

    /** 原料计数后叠加网络材料数量：刷新网络缓存，把各原料网络数量加进 playerIngredientCount */
    @Inject(method = "getPlayerIngredientCount", at = @At("RETURN"))
    private void addNetworkCounts(GunSmithTableRecipe recipe, CallbackInfo ci) {
        if (!com.solr98.beyondintegration.client.GunSmithNetMode.isNetworkMode()) return;
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
            beyond$cachedNetworkCounts = com.solr98.beyondintegration.client.GunSmithNetMode.calcNetworkCounts(recipe);
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

    /** 合成按钮创建后追加“网络模式”与“产物输出”两个切换按钮 */
    @Inject(method = "addCraftButton", at = @At("TAIL"))
    private void onAddCraftButton(CallbackInfo ci) {
        var self = (GunSmithTableScreen) (Object) this;
        int left = self.getGuiLeft(), top = self.getGuiTop();

        var modeBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            com.solr98.beyondintegration.client.GunSmithNetMode.setNetworkMode(
                    !com.solr98.beyondintegration.client.GunSmithNetMode.isNetworkMode());
            if (beyond$outputBtn != null) beyond$outputBtn.visible = com.solr98.beyondintegration.client.GunSmithNetMode.isNetworkMode();
            if (selectedRecipe != null) getPlayerIngredientCount(selectedRecipe);
            b.setTooltip(Tooltip.create(Component.translatable(
                    com.solr98.beyondintegration.client.GunSmithNetMode.isNetworkMode() ? "gui.beyond_integration.mode.network" : "gui.beyond_integration.mode.vanilla")));
        }).bounds(left + 322, top + 50, 18, 18).build());
        beyond$modeBtn = modeBtn;
        beyond$modeBtn.setTooltip(Tooltip.create(Component.translatable(
                com.solr98.beyondintegration.client.GunSmithNetMode.isNetworkMode() ? "gui.beyond_integration.mode.network" : "gui.beyond_integration.mode.vanilla")));

        beyond$outputBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            com.solr98.beyondintegration.client.GunSmithNetMode.setOutputToNetwork(
                    !com.solr98.beyondintegration.client.GunSmithNetMode.isOutputToNetwork());
            b.setTooltip(Tooltip.create(Component.translatable(com.solr98.beyondintegration.client.GunSmithNetMode.isOutputToNetwork()
                    ? "gui.beyond_integration.output.network"
                    : "gui.beyond_integration.output.inventory",
                    NetworkItemCache.getNetId() >= 0 ? NetworkItemCache.getNetId() : "?")));
        }).bounds(left + 267, top + 162, 18, 18).build());
        beyond$outputBtn.setTooltip(Tooltip.create(Component.translatable(com.solr98.beyondintegration.client.GunSmithNetMode.isOutputToNetwork()
                ? "gui.beyond_integration.output.network"
                : "gui.beyond_integration.output.inventory",
                NetworkItemCache.getNetId() >= 0 ? NetworkItemCache.getNetId() : "?")));
        beyond$outputBtn.visible = com.solr98.beyondintegration.client.GunSmithNetMode.isNetworkMode();
    }

    /** 按当前模式同步两个切换按钮的提示框文本（首次加载偏好后调用，修正初始硬编码） */
    @Unique
    private void beyond$syncTooltips() {
        if (beyond$modeBtn != null) {
            beyond$modeBtn.setTooltip(Tooltip.create(Component.translatable(
                    com.solr98.beyondintegration.client.GunSmithNetMode.isNetworkMode() ? "gui.beyond_integration.mode.network" : "gui.beyond_integration.mode.vanilla")));
        }
        if (beyond$outputBtn != null) {
            beyond$outputBtn.setTooltip(Tooltip.create(Component.translatable(com.solr98.beyondintegration.client.GunSmithNetMode.isOutputToNetwork()
                    ? "gui.beyond_integration.output.network"
                    : "gui.beyond_integration.output.inventory",
                    NetworkItemCache.getNetId() >= 0 ? NetworkItemCache.getNetId() : "?")));
        }
    }

    /** 包装合成按钮点击回调：网络模式下改发网络合成包，否则走原（或 taczaddon）流程 */
    @ModifyArg(method = "addCraftButton()V",
               at = @At(value = "INVOKE",
                        target = "Lnet/minecraft/client/gui/components/ImageButton;<init>(IIIIIIILnet/minecraft/resources/ResourceLocation;Lnet/minecraft/client/gui/components/Button$OnPress;)V",
                        remap = true),
               index = 8,
               remap = false,
               require = 1)
    private Button.OnPress beyond$wrapCraftButton(Button.OnPress original) {
        return b -> {
            if (!com.solr98.beyondintegration.client.GunSmithNetMode.isNetworkMode() || !NetworkItemCache.hasNetwork()
                    || selectedRecipe == null) {
                original.onPress(b);
                return;
            }
            int count = Screen.hasShiftDown() ? 64 : 1;
            PacketHandler.sendToServer(new TaczCraftPacket(selectedRecipe.getId(), count, com.solr98.beyondintegration.client.GunSmithNetMode.isOutputToNetwork()));
        };
    }
}
