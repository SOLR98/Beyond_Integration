package com.solr98.beyondintegration.mixin;

// 注意：Beyond Dimensions 本体将在下个版本更换 UI 框架，本类依赖其现有 GUI 布局/坐标/纹理，
// 待 BD 正式发布后需校对代码与新版 GUI。


import com.solr98.beyondintegration.client.SuperbAmmoCache;
import com.solr98.beyondintegration.client.WorkstationActivationCache;
import com.solr98.beyondintegration.client.gui.LeftSidebarLayout;
import com.solr98.beyondintegration.client.gui.WorkstationModeConstants;
import com.solr98.beyondintegration.client.gui.extension.BDGUIHelper;
import com.solr98.beyondintegration.client.widget.EnchantToggleBtn;
import com.solr98.beyondintegration.client.widget.EnergyChargeToggleBtn;
import com.solr98.beyondintegration.client.widget.PotionCharmToggleBtn;
import com.solr98.beyondintegration.feature.workstation.WorkstationActivation;
import com.solr98.beyondintegration.network.ActivateWorkstationPacket;
import com.solr98.beyondintegration.network.OpenStorageMenuPacket;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestSuperbAmmoExtractPacket;
import com.solr98.beyondintegration.network.RequestEnchantSeparationPacket;
import com.solr98.beyondintegration.network.RequestEnergyChargePacket;
import com.solr98.beyondintegration.network.RequestPotionCharmPacket;
import com.solr98.beyondintegration.network.SacrificeMendingPacket;
import com.solr98.beyondintegration.network.RequestSuperbAmmoStatusPacket;
import com.solr98.beyondintegration.network.RequestWorkstationActivationPacket;
import com.solr98.beyondintegration.network.ToggleEnchantSeparationPacket;
import com.solr98.beyondintegration.network.ToggleEnergyChargePacket;
import com.solr98.beyondintegration.network.CyclePotionCharmModePacket;
import com.solr98.beyondintegration.feature.charm.PotionCharmMode;
import com.wintercogs.beyonddimensions.client.gui.DimensionsNetGUI;
import com.solr98.beyondintegration.client.gui.BeyondSidebarAccess;
import com.wintercogs.beyonddimensions.common.menu.DimensionsNetMenu;
import com.solr98.beyondintegration.ClientConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * 注入 BeyondDimensions 的 {@link DimensionsNetGUI}（存储网络界面），兼容 0.7.27 / 0.7.30。
 * 不依赖 BD 的 LeftButtonSidebar：反射读取 BD 原生左侧按钮（两版同名，基准坐标 guiLeft-18, guiTop+6），
 * 连同本模组按钮交给 LeftSidebarLayout 统一重排。扩展该界面：附魔分离/自动充电开关按钮
 * （反射调用父类 addRenderableWidget 注册）、工作站模式切换按钮、Superb Warfare 弹药面板
 * （显示/提取网络弹药，支持 Shift 批量），并处理对应点击交互。
 */
@Mixin(value = DimensionsNetGUI.class, remap = false)
public class DimensionsNetGUIMixin implements BeyondSidebarAccess {
    /** BD 原生左侧按钮字段（0.7.27/0.7.30 同名、顺序固定），反射读取以兼容两版 */
    @Unique private static final String[] beyond$nativeButtonFields = {
            "sortButton", "secondSortButton", "reverseButton", "searchToggleButton",
            "addPageButton", "removePageButton", "craftButton", "primaryNetSwitcherButton"
    };
    /** 接管布局的全部左侧按钮（BD 原生 + 本模组） */
    @Unique private final List<AbstractButton> beyond$allButtons = new ArrayList<>();
    /** BD 可容纳的最大行数（按当前屏幕高度计算） */
    @Shadow(remap = false) protected int calMaxLines() { return 0; }
    /** BD GUI 图像高度（按当前行数计算） */
    @Shadow(remap = false) protected int rebuildImageHeight() { return 0; }
    /** 是否处于"行数已达当前屏幕最大值"状态（屏幕调整后继续跟随最大值） */
    @Unique private boolean beyond$atMaxLines = false;
    /** 上一次 init 时的屏幕尺寸（用于区分"窗口调整"与"加/减页触发的 init"） */
    @Unique private int beyond$lastInitW = -1;
    @Unique private int beyond$lastInitH = -1;
    /** 反射缓存的父类 Screen#addRenderableWidget：Mixin 0.8.5 不支持 @Shadow 继承方法，故运行时反射注册 */
    @Unique private static java.lang.reflect.Method beyond$addWidgetMethod;

    /** 把侧栏按钮注册进原生 widget 体系（渲染/点击/tooltip）；反射失败时静默跳过 */
    @Unique
    private void beyond$addWidget(net.minecraft.client.gui.components.events.GuiEventListener widget) {
        try {
            if (beyond$addWidgetMethod == null) {
                beyond$addWidgetMethod = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(
                        net.minecraft.client.gui.screens.Screen.class, "m_142416_",
                        net.minecraft.client.gui.components.events.GuiEventListener.class);
            }
            beyond$addWidgetMethod.setAccessible(true);
            beyond$addWidgetMethod.invoke(this, widget);
        } catch (Throwable ignored) {}
    }
    /** 模式按钮正常贴图 */
    @Unique private static final ResourceLocation BTN = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button.png");
    /** 模式按钮悬停贴图 */
    @Unique private static final ResourceLocation BH = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button_hovered.png");
    /** 模式按钮禁用贴图（工作台未献祭激活时使用） */
    @Unique private static final ResourceLocation BTN_DISABLED = ResourceLocation.tryParse("beyonddimensions:textures/gui/sprites/widget/slot_button_disabled.png");
    /** 弹药面板显示的 5 种 SW 弹药物品 ID */
    @Unique private static final String[] AMMO_ITEMS = {
        "superbwarfare:handgun_ammo", "superbwarfare:rifle_ammo", "superbwarfare:shotgun_ammo",
        "superbwarfare:sniper_ammo", "superbwarfare:heavy_ammo"
    };
    /** 与 AMMO_ITEMS 对应的网络缓存键名 */
    @Unique private static final String[] AMMO_NAMES = {
        "HandgunAmmo", "RifleAmmo", "ShotgunAmmo", "SniperAmmo", "HeavyAmmo"
    };
    /** 弹药格子大小 */
    @Unique private static final int SLOT_SIZE = 18;
    /** 弹药格子间距 */
    @Unique private static final int SLOT_GAP = 2;
    /** 当前鼠标悬停的弹药格子下标（-1 表示无） */
    @Unique private int beyond$hoveredSlot = -1;
    /** 附魔分离开关按钮 */
    @Unique private EnchantToggleBtn beyond$enchantBtn;
    /** 自动充电开关按钮 */
    @Unique private EnergyChargeToggleBtn beyond$energyBtn;
    /** 上次记录的自动充电状态，用于检测变化并刷新 tooltip */
    @Unique private boolean beyond$lastEnergyState = true;
    /** 网络药水护符开关按钮 */
    @Unique private PotionCharmToggleBtn beyond$potionCharmBtn;
    /** 上次记录的网络药水护符生效目标（PotionCharmMode 序号），用于检测变化并刷新 tooltip */
    @Unique private int beyond$lastPotionCharmState = 0;
    /** 上次记录的经验修补献祭解锁状态，用于检测变化并刷新按钮 */
    @Unique private boolean beyond$lastPotionCharmMendingUnlocked = false;
    /** 已屏蔽的 RI 原生按钮（EAT/SELECT/MODE/MENU）：仅保留在界面中让 RI 的 existing 检测命中，隐藏且不可点 */
    @Unique private final java.util.List<net.minecraft.client.gui.components.Button> beyond$rsButtons = new java.util.ArrayList<>();
    /** 本模组接管的按钮（排后组，onInit 收集；用于接管布局重排） */
    @Unique private final java.util.List<AbstractButton> beyond$biButtons = new java.util.ArrayList<>();
    /** 附魔物品(装备)分离开关按钮（全局配置关闭物品分离时不创建/不显示） */
    /** 上次记录的附魔分离状态，用于检测变化并刷新 tooltip */
    @Unique private boolean beyond$lastEnchantState = true;
    /** 上次记录的附魔物品分离状态，用于检测变化并刷新 tooltip */

    /** 屏幕调整时（init HEAD）：若此前行数已达屏幕最大值，则跟随新屏幕最大值（加/减页触发的 init 不受影响） */
    @Inject(method = "init", at = @At("HEAD"), remap = true)
    private void beyond$followMaxLinesOnResize(CallbackInfo ci) {
        var self = (DimensionsNetGUI<?>) (Object) this;
        boolean resized = beyond$lastInitW > 0 && (beyond$lastInitW != self.width || beyond$lastInitH != self.height);
        beyond$lastInitW = self.width;
        beyond$lastInitH = self.height;
        if (!resized || !beyond$atMaxLines) return;
        int newMax = Math.max(2, Math.min(99, this.calMaxLines()));
        var menu = self.getMenu();
        if (menu.getLines() != newMax) {
            menu.setLines(newMax);
            menu.rebuildSlots();
        }
    }

    /** 初始化完成时（init RETURN）：记录是否处于"不能再加行"的最大值状态 */
    @Inject(method = "init", at = @At("RETURN"), remap = true)
    private void beyond$trackMaxLinesState(CallbackInfo ci) {
        var self = (DimensionsNetGUI<?>) (Object) this;
        beyond$atMaxLines = self.getMenu().getLines() >= 99
                || self.height - 36 <= this.rebuildImageHeight() + 18;
    }

    /** 初始化完成后：附魔分离按钮挂到左侧按钮栏（0.7.30 自动布局，追加在作者按钮之后），并请求服务端弹药状态 */
    @Inject(method = "init", at = @At("RETURN"), remap = true)
    private void onInit(CallbackInfo ci) {
        var self = (DimensionsNetGUI<?>) (Object) this;
        beyond$biButtons.clear();

        // 坐标由 sidebar 自动排列；按 BD 原生模式经 addRenderableWidget 注册（渲染/点击/tooltip 由 widget 体系处理）
        beyond$enchantBtn = new EnchantToggleBtn(0, 0, btn -> {
            boolean next = !SuperbAmmoCache.getEnchantSeparation();
            SuperbAmmoCache.setEnchantSeparation(next);
            PacketHandler.sendToServer(new ToggleEnchantSeparationPacket());
        });
        beyond$enchantBtn.updateTooltip();
        beyond$lastEnchantState = SuperbAmmoCache.getEnchantSeparation();

        beyond$energyBtn = new EnergyChargeToggleBtn(0, 0, btn -> {
            boolean next = !SuperbAmmoCache.getEnergyCharge();
            SuperbAmmoCache.setEnergyCharge(next);
            PacketHandler.sendToServer(new ToggleEnergyChargePacket());
        });
        beyond$energyBtn.updateTooltip();
        beyond$lastEnergyState = SuperbAmmoCache.getEnergyCharge();

        // 网络药水护符按钮：正常点击=循环生效目标；Shift+点击=献祭经验修补书（已解锁后失效）
        beyond$potionCharmBtn = new PotionCharmToggleBtn(0, 0, btn -> {
            if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                if (!SuperbAmmoCache.getPotionCharmMendingUnlocked()) {
                    PacketHandler.sendToServer(new SacrificeMendingPacket());
                }
            } else {
                int next = PotionCharmMode.of(SuperbAmmoCache.getPotionCharmMode()).next().ordinal();
                SuperbAmmoCache.setPotionCharmMode(next);
                PacketHandler.sendToServer(new CyclePotionCharmModePacket());
            }
        });
        beyond$potionCharmBtn.updateTooltip();
        beyond$lastPotionCharmState = SuperbAmmoCache.getPotionCharmMode();
        beyond$lastPotionCharmMendingUnlocked = SuperbAmmoCache.getPotionCharmMendingUnlocked();
        // 本模组开关按钮：统一加入 BD 左侧按钮栏（自动排列）
        beyond$addWidget(beyond$enchantBtn);
        beyond$addWidget(beyond$energyBtn);
        beyond$addWidget(beyond$potionCharmBtn);
        beyond$biButtons.add(beyond$enchantBtn);
        beyond$biButtons.add(beyond$energyBtn);
        beyond$biButtons.add(beyond$potionCharmBtn);

        // 主动兼容 rs_integration：RI 自身的按钮安装/定位已被 RsIntegrationAutoEatMixin 屏蔽，
        // 这里由本模组调用其按钮创建入口，但原生 EAT/SELECT/MODE/MENU 按钮全部屏蔽：
        // 仅注册进界面（让 RI 的 existing 检测命中，避免其每帧重建），不加入侧栏布局、不参与接管排序；
        // 渲染隐藏 + 点击吞掉，功能由下方本模组的 RsAutoEatButton 替代
        beyond$rsButtons.clear();
        com.solr98.beyondintegration.compat.RsIntegrationCompat.installControls(self, button -> {
            com.solr98.beyondintegration.compat.RsIntegrationCompat.manage(button);
            beyond$rsButtons.add(button);
            beyond$addWidget(button);
        });

        // RI 的“机器中心 / 维度共振盘”在 BD 左侧栏为手动渲染按钮（已被 RsIntegrationMachineHubMixin 屏蔽），
        // 这里以 BD 侧栏按钮形式接管，保证同样在左侧栏内且不重叠
        if (com.solr98.beyondintegration.compat.RsIntegrationCompat.isLoaded()) {
            // RI 原生自动进食按钮已屏蔽，这里以 BD 侧栏按钮替代其功能（进食/选择/模式切换）
            for (var role : com.solr98.beyondintegration.client.widget.RsAutoEatButton.Role.values()) {
                var autoEatBtn = new com.solr98.beyondintegration.client.widget.RsAutoEatButton(role, 0, 0);
                beyond$addWidget(autoEatBtn);
                beyond$biButtons.add(autoEatBtn);
            }

            var machineCenterBtn = new com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton(
                    0, 0, 16, 16,
                    ResourceLocation.tryParse("rs_integration:textures/gui/machine_center_bd_icon_16x16.png"),
                    b -> com.solr98.beyondintegration.compat.RsIntegrationCompat.toggleMachineCenter());
            machineCenterBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("gui.beyond_integration.rs_machine_center")));
            beyond$addWidget(machineCenterBtn);
            beyond$biButtons.add(machineCenterBtn);

            var resonanceBtn = new com.wintercogs.beyonddimensions.client.gui.widget.shared.IconButton(
                    0, 0, 16, 16,
                    ResourceLocation.tryParse("rs_integration:textures/gui/resonance_backpack_bd_icon_16x16.png"),
                    b -> com.solr98.beyondintegration.compat.RsIntegrationCompat.toggleResonanceBackpack());
            resonanceBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable("gui.beyond_integration.rs_resonance_backpack")));
            beyond$addWidget(resonanceBtn);
            beyond$biButtons.add(resonanceBtn);
        }

        // 收集 BD 原生按钮（反射，兼容 0.7.27/0.7.30），合并本模组按钮后统一接管布局
        beyond$allButtons.clear();
        beyond$allButtons.addAll(beyond$collectNativeButtons());
        beyond$allButtons.addAll(beyond$biButtons);
        // 接管左侧栏布局：BD 原有按钮始终在最前（保持原序），本模组按钮排后；超出 GUI 底部的按钮移到左侧新增列
        beyond$applySidebarLayout();

        if (ModList.get().isLoaded("superbwarfare"))
            PacketHandler.sendToServer(new RequestSuperbAmmoStatusPacket());
        // 附魔分离状态请求：独立于 SW，仅 BD+BI 时按钮也能正确回显
        PacketHandler.sendToServer(new RequestEnchantSeparationPacket());
        // 自动充电状态请求：独立于 SW，仅 BD+BI 时按钮也能正确回显
        PacketHandler.sendToServer(new RequestEnergyChargePacket());
        // 网络药水护符状态请求：独立于 SW，仅 BD+BI 时按钮也能正确回显
        PacketHandler.sendToServer(new RequestPotionCharmPacket());
        // 工作台献祭激活状态请求（可选平衡项）：供工作站模式按钮显示锁定状态
        PacketHandler.sendToServer(new RequestWorkstationActivationPacket());
    }

    /** 渲染前：屏蔽 RI 原生按钮，并每帧重排左侧栏（防止 RI 在渲染后事件中把它们设回可见/固定坐标） */
    @Inject(method = "render", at = @At("HEAD"), remap = true)
    private void onRenderHead(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        beyond$hideNativeRsButtons();
        beyond$applySidebarLayout();
    }

    /** 重新应用左侧栏接管布局（幂等；每帧渲染前调用，确保外部模组的坐标覆盖不生效） */
    @Unique
    private void beyond$applySidebarLayout() {
        var self = (DimensionsNetGUI<?>) (Object) this;
        LeftSidebarLayout.apply(beyond$allButtons, beyond$biButtons,
                self.getGuiLeft() - 18, self.getGuiTop() + 6, self.getGuiTop() + self.getYSize());
    }

    /** 反射读取 BD 原生左侧按钮（按固定字段名顺序），BD 类/字段名不混淆 */
    @Unique
    private List<AbstractButton> beyond$collectNativeButtons() {
        List<AbstractButton> list = new ArrayList<>();
        for (String name : beyond$nativeButtonFields) {
            try {
                var field = DimensionsNetGUI.class.getDeclaredField(name);
                field.setAccessible(true);
                Object value = field.get(this);
                if (value instanceof AbstractButton button) {
                    list.add(button);
                }
            } catch (Throwable ignored) {}
        }
        return list;
    }

    @Override
    public List<AbstractButton> beyond$trackedButtons() {
        return beyond$allButtons;
    }

    /** 屏蔽 RI 原生按钮：每帧渲染前强制隐藏并禁用（RI 会在渲染后事件中把它们设回可见/可点） */
    @Unique
    private void beyond$hideNativeRsButtons() {
        for (net.minecraft.client.gui.components.Button rsBtn : beyond$rsButtons) {
            if (rsBtn.visible) rsBtn.visible = false;
            if (rsBtn.active) rsBtn.active = false;
        }
    }

    /** 渲染末尾：绘制工作站模式按钮和网络弹药面板（含悬停 tooltip），并刷新两个开关按钮的 tooltip */
    @Inject(method = "render", at = @At("TAIL"), remap = true)
    private void onRender(GuiGraphics g, int mx, int my, float pt, CallbackInfo ci) {
        var self = (DimensionsNetGUI<?>) (Object) this;
        var f = Minecraft.getInstance().font;

        beyond$hoveredSlot = -1;

        // 开关按钮状态刷新：悬停 tooltip 文本随网络开关状态变化（渲染/点击由 widget 体系负责）
        if (beyond$enchantBtn != null) {
            boolean cur = SuperbAmmoCache.getEnchantSeparation();
            if (cur != beyond$lastEnchantState) {
                beyond$lastEnchantState = cur;
                beyond$enchantBtn.updateTooltip();
            }
        }
        if (beyond$energyBtn != null) {
            boolean cur = SuperbAmmoCache.getEnergyCharge();
            if (cur != beyond$lastEnergyState) {
                beyond$lastEnergyState = cur;
                beyond$energyBtn.updateTooltip();
            }
        }
        if (beyond$potionCharmBtn != null) {
            int cur = SuperbAmmoCache.getPotionCharmMode();
            boolean unlocked = SuperbAmmoCache.getPotionCharmMendingUnlocked();
            if (cur != beyond$lastPotionCharmState || unlocked != beyond$lastPotionCharmMendingUnlocked) {
                beyond$lastPotionCharmState = cur;
                beyond$lastPotionCharmMendingUnlocked = unlocked;
                beyond$potionCharmBtn.updateTooltip();
            }
        }

        // 附魔物品分离按钮
        // 工作站模式按钮（统一由 mixin 绘制：BI 工作站界面/BD 合成终端/纯存储）
        var menu = (DimensionsNetMenu) self.getMenu();
        int lx = self.getGuiLeft();
        int gy = self.getGuiTop() + 24 + 18 + (menu.getLines() - 2) * 18 + 26;
        List<Component> modeTooltip = null;
        // 顺序/可见集 = 客户端配置顺序 ∩ 服务端可用列表（服务端禁用项已剔除，紧凑排列）
        var cfgModes = WorkstationModeConstants.availableModes();
        for (int i = 0; i < cfgModes.size(); i++) {
            var mode = cfgModes.get(i);
            int bx = lx + WorkstationModeConstants.xFor(i), by = gy + WorkstationModeConstants.yFor(i);
            boolean on = beyond$isCurrentMode(menu.getClass(), mode);
            boolean h = mx >= bx && mx < bx + 16 && my >= by && my < by + 16;
            // 未献祭激活的工作台：禁用纹理 + 锁定 tooltip（点击将尝试献祭激活而非打开）
            boolean locked = WorkstationActivationCache.isLocked(mode.id());
            g.blit(locked ? BTN_DISABLED : (h || on ? BH : BTN), bx, by, 0, 0, 16, 16, 16, 16);
            var p = g.pose(); p.pushPose(); p.translate(bx + 1, by + 1, 1); p.scale(0.85f, 0.85f, 1);
            g.renderFakeItem(WorkstationModeConstants.iconFor(mode), 0, 0); p.popPose();
            // 仅记录第一个命中的 tooltip，统一在全部背景绘制后渲染（避免被后续按钮/面板背景覆盖）
            if (h && modeTooltip == null) {
                if (locked) {
                    // 多行 Component 列表（不用 \n，避免部分渲染上下文把换行识别为未知字符）
                    modeTooltip = List.of(
                            Component.translatable("gui.beyond_integration.workstation.locked.title",
                                    Component.translatable("gui.beyond_integration.mode." + mode.id())),
                            Component.translatable("gui.beyond_integration.workstation.locked.cost",
                                    WorkstationActivation.costName(mode.id())),
                            Component.translatable("gui.beyond_integration.workstation.locked.hint"));
                } else {
                    modeTooltip = List.of(Component.translatable("gui.beyond_integration.mode." + mode.id()));
                }
            }
        }

        // 弹药面板（数据不可用时仅跳过面板，模式按钮 tooltip 仍需渲染）
        boolean showAmmo = ModList.get().isLoaded("superbwarfare")
                && SuperbAmmoCache.hasData() && SuperbAmmoCache.getNetId() >= 0;
        boolean infinite = showAmmo && SuperbAmmoCache.getCount("__infinite__") > 0;
        if (showAmmo) {
            int panelX = self.getGuiLeft() + self.getXSize() + 4;
            int panelY = self.getGuiTop() + 8;

            for (int i = 0; i < 5; i++) {
                int sx = panelX;
                int sy = panelY + i * (SLOT_SIZE + SLOT_GAP);
                boolean hover = mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE;
                if (hover) beyond$hoveredSlot = i;

                g.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF8B8B8B);
                g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0xFF373737);
                if (hover) g.fill(sx + 1, sy + 1, sx + SLOT_SIZE - 1, sy + SLOT_SIZE - 1, 0x80FFFFFF);

                var ammoItem = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(AMMO_ITEMS[i]));
                if (ammoItem != null) g.renderFakeItem(new ItemStack(ammoItem), sx + 1, sy + 1);

                long count = SuperbAmmoCache.getCount(AMMO_NAMES[i]);
                var overlay = infinite ? "\u221E" : count == 0 ? "0" : BDGUIHelper.compactFormat(count);
                int overlayColor = infinite ? 0xFFAA00 : count == 0 ? 0x555555 : 0xFFFFFF;
                var pose = g.pose();
                pose.pushPose();
                pose.translate(0, 0, 300);
                float scale = 0.666f;
                pose.scale(scale, scale, scale);
                int textX = (int)((sx + 19 - f.width(overlay) * scale) / scale);
                int textY = (int)((sy + 12) / scale);
                g.drawString(f, overlay, textX, textY, overlayColor);
                pose.popPose();
            }
        }

        // tooltip 统一最后渲染：保证悬浮框渲染在所有按钮/面板背景之上
        if (modeTooltip != null) {
            g.renderComponentTooltip(f, modeTooltip, mx, my);
        }
        if (showAmmo && beyond$hoveredSlot >= 0) {
            String ammoName = AMMO_NAMES[beyond$hoveredSlot];
            long count = SuperbAmmoCache.getCount(ammoName);
            var ammoItem = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(AMMO_ITEMS[beyond$hoveredSlot]));
            List<Component> tooltip = new ArrayList<>();
            if (ammoItem != null) tooltip.add(Component.translatable(ammoItem.getDescriptionId()));
            else tooltip.add(Component.literal(ammoName));
            if (infinite) tooltip.add(Component.literal("\u221E").withStyle(ChatFormatting.GOLD));
            else tooltip.add(Component.literal(NumberFormat.getIntegerInstance().format(count)).withStyle(ChatFormatting.WHITE));
            if (ammoItem != null) g.renderTooltip(f, tooltip, new ItemStack(ammoItem).getTooltipImage(), new ItemStack(ammoItem), mx, my);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, remap = true)
    private void onMouseClicked(double mx, double my, int button, CallbackInfoReturnable<Boolean> cir) {
        var self = (DimensionsNetGUI<?>) (Object) this;

        // 屏蔽的 RI 原生按钮：命中其坐标直接吞掉（RI 每帧会把它们设回固定位置，避免点击触发原功能）
        // 注意：仅对"可见"的 RI 按钮吞点击——它们已被本模组隐藏，若其残留坐标与本模组侧栏按钮重叠，
        // 不判可见性会把侧栏按钮的点击一并吞掉（导致按钮无反应）。
        for (net.minecraft.client.gui.components.Button rsBtn : beyond$rsButtons) {
            if (!rsBtn.visible || !rsBtn.active) continue;
            if (mx >= rsBtn.getX() && mx < rsBtn.getX() + rsBtn.getWidth()
                    && my >= rsBtn.getY() && my < rsBtn.getY() + rsBtn.getHeight()) {
                cir.setReturnValue(true);
                return;
            }
        }

        // 扩展驱动：优先处理扩展点击（如 Ctrl+右键 附魔分离保护切换）
        for (var ext : com.solr98.beyondintegration.client.gui.extension.BDGUIExtensionRegistry.getExtensions()) {
            if (ext.onMouseClicked(self, mx, my, button)) {
                cir.setReturnValue(true);
                return;
            }
        }

        // 附魔物品分离按钮点击
        var menu = (DimensionsNetMenu) self.getMenu();
        int lx = self.getGuiLeft();
        int gy = self.getGuiTop() + 24 + 18 + (menu.getLines() - 2) * 18 + 26;
        // 顺序/可见集 = 客户端配置顺序 ∩ 服务端可用列表（服务端禁用项已隐藏，服务端仍会兜底校验）
        var clickModes = WorkstationModeConstants.availableModes();
        for (int i = 0; i < clickModes.size(); i++) {
            int bx = lx + WorkstationModeConstants.xFor(i), by = gy + WorkstationModeConstants.yFor(i);
            if (mx >= bx && mx < bx + 16 && my >= by && my < by + 16) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                if (WorkstationActivationCache.isLocked(clickModes.get(i).id())) {
                    // 未献祭激活：发送激活请求（从网络扣除献祭物品），不打开工作台
                    PacketHandler.sendToServer(new ActivateWorkstationPacket(clickModes.get(i).id()));
                } else {
                    com.solr98.beyondintegration.client.gui.WorkstationTransferHelper.save(menu);
                    PacketHandler.sendToServer(new OpenStorageMenuPacket(clickModes.get(i)));
                }
                cir.setReturnValue(true);
                return;
            }
        }

        // 弹药面板点击
        if (!ModList.get().isLoaded("superbwarfare")) return;
        if (!SuperbAmmoCache.hasData()) return;
        if (SuperbAmmoCache.getNetId() < 0) return;

        int panelX = self.getGuiLeft() + self.getXSize() + 4;
        int panelY = self.getGuiTop() + 8;
        int hitSlot = -1;
        for (int i = 0; i < 5; i++) {
            int sx = panelX;
            int sy = panelY + i * (SLOT_SIZE + SLOT_GAP);
            if (mx >= sx && mx < sx + SLOT_SIZE && my >= sy && my < sy + SLOT_SIZE) { hitSlot = i; break; }
        }
        if (hitSlot < 0) return;

        String ammoName = AMMO_NAMES[hitSlot];
        boolean infinite = SuperbAmmoCache.getCount("__infinite__") > 0;
        long count = infinite ? Long.MAX_VALUE : SuperbAmmoCache.getCount(ammoName);
        if (count <= 0) return;

        cir.setReturnValue(true);
        long toExtract = 64;
        if (beyond$hasShiftDown()) toExtract = 256;
        PacketHandler.sendToServer(new RequestSuperbAmmoExtractPacket(ammoName, Math.min(toExtract, count)));
    }

    /** 读取原菜单的 hasShiftDown 字段，判断是否按住 Shift */
    @Unique
    private boolean beyond$hasShiftDown() {
        try {
            var self = (DimensionsNetGUI<?>) (Object) this;
            var menu = self.getMenu();
            if (menu instanceof DimensionsNetMenu) return ((DimensionsNetMenu) menu).hasShiftDown;
        } catch (Exception ignored) {}
        return false;
    }

    /** 判断当前打开的菜单是否属于指定的工作站模式 */
    @Unique
    private static boolean beyond$isCurrentMode(Class<?> menuClass, OpenStorageMenuPacket.Type mode) {
        return switch (mode) {
            case ANVIL -> menuClass == com.solr98.beyondintegration.feature.crafting.DimensionsAnvilMenu.class;
            case CUT -> menuClass == com.solr98.beyondintegration.feature.crafting.DimensionsCutMenu.class;
            case GRIND -> menuClass == com.solr98.beyondintegration.feature.crafting.DimensionsGrindMenu.class;
            case SMITH -> menuClass == com.solr98.beyondintegration.feature.crafting.DimensionsSmithMenu.class;
            case CRAFT -> menuClass == com.solr98.beyondintegration.feature.crafting.DimensionsCraftMenu.class;
            case ENCHANT -> menuClass == com.solr98.beyondintegration.feature.crafting.DimensionsEnchantMenu.class;
            case ENCHANT_MERGE -> menuClass == com.solr98.beyondintegration.feature.crafting.DimensionsEnchantMergeMenu.class;
            default -> false;
        };
    }
}
