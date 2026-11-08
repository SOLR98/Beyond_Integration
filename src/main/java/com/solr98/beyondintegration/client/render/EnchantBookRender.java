package com.solr98.beyondintegration.client.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.BookModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 附魔台 3D 书本渲染（移植自旧版 {@code client/gui/DimensionsEnchantGUI} 的 {@code renderWorkstationPanel}）。
 * <p>在给定 GUI 坐标处按原版 {@code EnchantmentScreen} 的姿态渲染打开/翻页的 {@link BookModel}。
 * <p>必须在渲染线程调用（由 CompixelUI 的原生绘制在渲染线程回调）。
 */
public final class EnchantBookRender {

    private static final ResourceLocation BOOK_TEX =
            new ResourceLocation("minecraft", "textures/entity/enchanting_table_book.png");

    private static BookModel bookModel;

    private EnchantBookRender() {
    }

    /**
     * @param anchorX  书本中心 X（GUI 坐标，相对绘制区域）
     * @param anchorY  书本中心 Y（GUI 坐标，相对绘制区域）
     * @param scale    书本缩放（工作台按自身布局决定）
     * @param openNow  开合插值（0 合、1 开）
     * @param flipNow  翻页插值
     */
    public static void render(GuiGraphics g, float anchorX, float anchorY, float scale,
                              float openNow, float flipNow) {
        Minecraft mc = Minecraft.getInstance();
        if (bookModel == null) {
            bookModel = new BookModel(mc.getEntityModels().bakeLayer(ModelLayers.BOOK));
        }
        Lighting.setupForEntityInInventory();
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(anchorX, anchorY, 100.0F);
        pose.scale(-scale, scale, scale);
        pose.mulPose(Axis.XP.rotationDegrees(25.0F));
        pose.translate((1.0F - openNow) * 0.2F, (1.0F - openNow) * 0.1F, (1.0F - openNow) * 0.25F);
        pose.mulPose(Axis.YP.rotationDegrees(-(1.0F - openNow) * 90.0F - 90.0F));
        pose.mulPose(Axis.XP.rotationDegrees(180.0F));
        float f4 = Mth.clamp(Mth.frac(flipNow + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
        float f5 = Mth.clamp(Mth.frac(flipNow + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
        bookModel.setupAnim(0.0F, f4, f5, openNow);
        VertexConsumer vc = g.bufferSource().getBuffer(bookModel.renderType(BOOK_TEX));
        bookModel.renderToBuffer(pose, vc, 15728880, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        g.flush();
        pose.popPose();
        Lighting.setupFor3DItems();
    }
}
