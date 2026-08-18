package com.solr98.beyondintegration.client.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;

/**
 * GUI 内实体渲染工具：基于原版 InventoryScreen 的渲染逻辑，
 * 在容器界面中渲染实体，支持固定角度与跟随鼠标两种模式。
 */
// 实体在 GUI 内渲染（照抄原版 InventoryScreen.renderEntityInInventory 逻辑）
public final class WorkstationRenderHelper {
    /** 以固定角度渲染实体（照抄原版 InventoryScreen.renderEntityInInventory） */
    public static void renderEntityInInventory(GuiGraphics g, int x, int y, int scale, Quaternionf quat, Quaternionf camQuat, LivingEntity entity) {
        g.pose().pushPose();
        g.pose().translate(x, y, 50.0D);
        g.pose().scale(scale, scale, -scale);
        g.pose().mulPose(quat);
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        if (camQuat != null) {
            camQuat.conjugate();
            dispatcher.overrideCameraOrientation(camQuat);
        }
        dispatcher.setRenderShadow(false);
        RenderSystem.runAsFancy(() -> {
            dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, g.pose(), g.bufferSource(), LightTexture.FULL_BRIGHT);
        });
        g.flush();
        dispatcher.setRenderShadow(true);
        g.pose().popPose();
        Lighting.setupFor3DItems();
    }

    // 鼠标跟随：偏移 → 角度 → 渲染（照抄原版 renderEntityInInventoryFollowsMouse / FollowsAngle）
    /** 由鼠标相对位置换算旋转角并渲染实体（原版跟随鼠标逻辑） */
    public static void renderEntityInInventoryFollowsMouse(GuiGraphics g, int x, int y, int scale, float mouseX, float mouseY, LivingEntity entity) {
        float f = (float) Math.atan(mouseX / 40.0F);
        float f1 = (float) Math.atan(mouseY / 40.0F);
        renderEntityInInventoryFollowsAngle(g, x, y, scale, f, f1, entity);
    }

    /** 按给定角度渲染实体：临时修改实体朝向，渲染完成后恢复原值 */
    public static void renderEntityInInventoryFollowsAngle(GuiGraphics g, int x, int y, int scale, float angleX, float angleY, LivingEntity entity) {
        Quaternionf quat = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf quat1 = new Quaternionf().rotateX(angleY * 20.0F * ((float) Math.PI / 180F));
        quat.mul(quat1);
        float bodyYaw = entity.yBodyRot;
        float headYaw = entity.getYRot();
        float pitch = entity.getXRot();
        float headRotO = entity.yHeadRotO;
        float headRot = entity.yHeadRot;
        entity.yBodyRot = 180.0F + angleX * 20.0F;
        entity.setYRot(180.0F + angleX * 40.0F);
        entity.setXRot(-angleY * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        renderEntityInInventory(g, x, y, scale, quat, quat1, entity);
        entity.yBodyRot = bodyYaw;
        entity.setYRot(headYaw);
        entity.setXRot(pitch);
        entity.yHeadRotO = headRotO;
        entity.yHeadRot = headRot;
    }

    private WorkstationRenderHelper() {}
}
