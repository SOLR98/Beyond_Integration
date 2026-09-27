package com.solr98.beyondintegration.jei;

import com.solr98.beyondintegration.util.NumberFormatUtil;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import mezz.jei.api.gui.drawable.IDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * JEI 物品条目上的 BD 网络库存数量角标（绘制在物品右下角）。
 * 数量在每次绘制时实时查询（打开 BD 终端界面时有效），因此网络变化即时反映。
 * 存储键在构造时创建一次并复用（ItemStackKey 不可变且自带哈希缓存），避免每帧重建的 NBT 拷贝开销。
 * 文本按数量值缓存（数量不变时复用格式化字符串与宽度），并使用无阴影文本降低字形绘制开销。
 */
public class NetworkCountOverlay implements IDrawable {

    /** 缓存的存储键（仅用于查询网络库存） */
    private final ItemStackKey key;
    /** 文本缓存：数量未变时复用格式化结果与宽度（避免每帧 compactFormat + font.width） */
    private long cachedCount = Long.MIN_VALUE;
    private String cachedText = "";
    private int cachedWidth = 0;

    public NetworkCountOverlay(ItemStack stack) {
        this.key = new ItemStackKey(stack.copyWithCount(1));
    }

    @Override
    public int getWidth() {
        return 16;
    }

    @Override
    public int getHeight() {
        return 16;
    }

    @Override
    public void draw(GuiGraphics g, int x, int y) {
        long count = BeyondJeiNetworkHelper.getNetworkCount(key);
        if (count <= 0) return;
        var font = Minecraft.getInstance().font;
        if (count != cachedCount) {
            cachedCount = count;
            cachedText = NumberFormatUtil.compactLargeInt(count);
            cachedWidth = font.width(cachedText);
        }
        float scale = 0.666f;
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 200);
        pose.scale(scale, scale, scale);
        int tx = (int) ((x + 17 - cachedWidth * scale) / scale);
        int ty = (int) ((y + 12) / scale);
        g.drawString(font, cachedText, tx, ty, 0xFFFFFF, false);
        pose.popPose();
    }
}
