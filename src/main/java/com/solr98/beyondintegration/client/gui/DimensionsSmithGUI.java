package com.solr98.beyondintegration.client.gui;

import com.solr98.beyondintegration.feature.crafting.DimensionsSmithMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SmithingTemplateItem;
import org.joml.Quaternionf;
import java.util.List;
import java.util.Optional;

/**
 * 锻造台工作站界面：对齐原版锻造台，提供盔甲架预览、
 * 空槽图标循环动画、配方错误提示与新手引导 tooltip。
 */
public class DimensionsSmithGUI extends DimensionsStorageGUI<DimensionsSmithMenu> {
    private static final ResourceLocation BG = ResourceLocation.tryParse("beyond_integration:textures/gui/smithing.png");
    // 原版锻造台参数（SmithingScreen）：固定角度 25°X + 180°Z，缩放 25
    private static final Quaternionf ARMOR_STAND_ANGLE = new Quaternionf().rotationXYZ(0.43633232F, 0.0F, (float) Math.PI);
    // 原版红叉图标（BI 自备纹理，28×21）
    private static final ResourceLocation NOT_FOR = ResourceLocation.tryParse("beyond_integration:textures/gui/notfor.png");
    // 原版 SmithingScreen：模板槽空槽图标（装甲纹饰 / 下界合金升级）
    private static final List<ResourceLocation> EMPTY_SLOT_SMITHING_TEMPLATES = List.of(
            ResourceLocation.tryParse("item/empty_slot_smithing_template_armor_trim"),
            ResourceLocation.tryParse("item/empty_slot_smithing_template_netherite_upgrade"));
    /** 预览用盔甲架实体（显示锻造结果穿戴效果） */
    private ArmorStand armorStand;
    /** 空槽图标循环动画（模板 / 底座 / 附加槽） */
    private final CyclingSlotBackground templateIcon = new CyclingSlotBackground();
    private final CyclingSlotBackground baseIcon = new CyclingSlotBackground();
    private final CyclingSlotBackground additionalIcon = new CyclingSlotBackground();

    public DimensionsSmithGUI(DimensionsSmithMenu c, Inventory p, Component t) { super(c, p, t); }

    /** 创建并初始化预览盔甲架（按原版朝向参数设置） */
    @Override protected void init() {
        super.init();
        if (armorStand == null) {
            armorStand = new ArmorStand(Minecraft.getInstance().level, 0.0D, 0.0D, 0.0D);
            armorStand.setNoBasePlate(true);
            armorStand.setShowArms(true);
            // 原版朝向（SmithingScreen.subInit）
            armorStand.yBodyRot = 210.0F;
            armorStand.setXRot(25.0F);
            armorStand.yHeadRot = armorStand.getYRot();
            armorStand.yHeadRotO = armorStand.getYRot();
        }
    }

    /** 渲染面板背景、盔甲架预览、空槽图标动画、配方错误提示与引导 tooltip */
    @Override protected void renderWorkstationPanel(GuiGraphics g) {
        int gy = getGapY();
        g.blit(BG, this.leftPos, gy, 0, 0, 176, 62, 176, 62);
        g.drawString(Minecraft.getInstance().font, Component.translatable("gui.beyond_integration.workstation.smith"), this.leftPos + 6, gy - 7, 0x404040, false);
        // 盔甲架：原版位置 (leftPos+141, topPos+75)，按 BI 面板换算 → 结果槽 (97, ey(41)) + 原版偏移 (7, 30)
        if (armorStand != null) {
            int ax = this.leftPos + 140;
            int ay = gy + 54;
            WorkstationRenderHelper.renderEntityInInventory(g, ax, ay, 25, ARMOR_STAND_ANGLE, null, armorStand);
        }
        // 原版空槽图标循环动画（模板/底座/附加槽）
        templateIcon.render(g, this.leftPos, this.topPos, slotOf(0));
        baseIcon.render(g, this.leftPos, this.topPos, slotOf(1));
        additionalIcon.render(g, this.leftPos, this.topPos, slotOf(2));
        // 原版 hasRecipeError：三个输入槽都有物品且结果为空（红叉 y 对齐输出槽 gy+42，上移 1px）
        if (hasRecipeError()) {
            g.blit(NOT_FOR, this.leftPos + 65, gy + 41, 0, 0, 28, 21, 28, 21);
            g.drawString(Minecraft.getInstance().font,
                    Component.translatable("gui.beyond_integration.smithing.invalid"),
                    this.leftPos + 6, gy + 66, 0xFFFF5555, false);
        }
        renderOnboardingTooltips(g, mouseX, mouseY);
    }

    /** 按自定义槽位序号取出对应的实际 Slot */
    private Slot slotOf(int wsIdx) { return this.menu.slots.get(this.menu.customSlotIndices.get(wsIdx)); }

    /** 每 tick 更新盔甲架穿戴效果与三个空槽图标的循环动画 */
    @Override public void containerTick() {
        super.containerTick();
        if (armorStand == null) return;
        updateArmorStand(this.menu.getOutput());
        Optional<SmithingTemplateItem> optional = getTemplateItem();
        templateIcon.tick(EMPTY_SLOT_SMITHING_TEMPLATES);
        baseIcon.tick(optional.map(SmithingTemplateItem::getBaseSlotEmptyIcons).orElse(List.of()));
        additionalIcon.tick(optional.map(SmithingTemplateItem::getAdditionalSlotEmptyIcons).orElse(List.of()));
    }

    /** 获取模板槽中的锻造模板物品（空槽或非模板物品返回 empty） */
    private Optional<SmithingTemplateItem> getTemplateItem() {
        ItemStack itemstack = this.menu.getTemplate();
        return !itemstack.isEmpty() && itemstack.getItem() instanceof SmithingTemplateItem st ? Optional.of(st) : Optional.empty();
    }

    // 原版 SmithingScreen.hasRecipeError
    /** 配方错误判定：三个输入槽均有物品但结果槽为空 */
    private boolean hasRecipeError() {
        return !this.menu.getTemplate().isEmpty()
                && !this.menu.getBase().isEmpty()
                && !this.menu.getAdditional().isEmpty()
                && this.menu.getOutput().isEmpty();
    }

    // 原版 SmithingScreen.renderOnboardingTooltips：错误提示 / 缺模板提示 / 槽位描述提示（115px 换行）
    /** 渲染引导 tooltip：配方错误提示、缺模板提示与槽位描述（文本 115px 换行） */
    private void renderOnboardingTooltips(GuiGraphics g, int mx, int my) {
        int gy = getGapY();
        Optional<Component> optional = Optional.empty();
        if (hasRecipeError() && isHovering(65, gy - this.topPos + 41, 28, 21, mx, my)) {
            optional = Optional.of(Component.translatable("container.upgrade.error_tooltip"));
        }
        if (this.hoveredSlot != null) {
            ItemStack itemstack = this.menu.getTemplate();
            ItemStack itemstack1 = this.hoveredSlot.getItem();
            if (itemstack.isEmpty()) {
                if (this.hoveredSlot == slotOf(0)) {
                    optional = Optional.of(Component.translatable("container.upgrade.missing_template_tooltip"));
                }
            } else if (itemstack.getItem() instanceof SmithingTemplateItem smithingtemplateitem && itemstack1.isEmpty()) {
                if (this.hoveredSlot == slotOf(1)) {
                    optional = Optional.of(smithingtemplateitem.getBaseSlotDescription());
                } else if (this.hoveredSlot == slotOf(2)) {
                    optional = Optional.of(smithingtemplateitem.getAdditionSlotDescription());
                }
            }
        }
        optional.ifPresent(c -> g.renderTooltip(Minecraft.getInstance().font, Minecraft.getInstance().font.split(c, 115), mx, my));
    }

    // 原版 SmithingScreen.CyclingSlotBackground（空槽图标循环动画）
    /** 原版锻造台空槽图标循环动画（每 30 tick 切换一个图标） */
    private static class CyclingSlotBackground {
        private static final int ICON_SWAP_TICKS = 30;
        private static final int ICON_SIZE = 16;
        private List<ResourceLocation> icons = List.of();
        private int tick;

        public void tick(List<ResourceLocation> icons) {
            if (icons.isEmpty()) { this.tick = 0; return; }
            this.icons = icons;
            this.tick++;
        }

        public void render(GuiGraphics g, int leftPos, int topPos, Slot slot) {
            if (this.icons.isEmpty()) return;
            int i = (int)((float)this.tick / ICON_SWAP_TICKS % (float)this.icons.size());
            ResourceLocation rl = this.icons.get(i);
            if (!slot.hasItem() && slot.isActive()) {
                // 原版：空槽图标在 BLOCK atlas（物品纹理），需 getTextureAtlas(LOCATION_BLOCKS) + blit
                var sprite = Minecraft.getInstance().getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(rl);
                g.blit(leftPos + slot.x, topPos + slot.y, 0, ICON_SIZE, ICON_SIZE, sprite);
            }
        }
    }

    // 原版逻辑（SmithingScreen.updateArmorStandPreview）：清空全部槽位 → 盔甲穿对应部位，否则副手
    /** 更新盔甲架预览：先清空全部穿戴，再按输出物品穿对应盔甲部位或副手 */
    private void updateArmorStand(ItemStack stack) {
        for (EquipmentSlot es : EquipmentSlot.values())
            armorStand.setItemSlot(es, ItemStack.EMPTY);
        if (!stack.isEmpty()) {
            ItemStack copy = stack.copy();
            if (copy.getItem() instanceof ArmorItem armorItem) {
                armorStand.setItemSlot(armorItem.getEquipmentSlot(), copy);
            } else {
                armorStand.setItemSlot(EquipmentSlot.OFFHAND, copy);
            }
        }
    }
}
