package com.solr98.beyondintegration.client.gui;

// 注意：Beyond Dimensions 本体将在下个版本更换 UI 框架，本类依赖其现有 GUI 布局/坐标/纹理，
// 待 BD 正式发布后需校对代码与新版 GUI。


import com.solr98.beyondintegration.feature.ftb.FtbIntegrationHelper;
import com.solr98.beyondintegration.network.PacketHandler;
import com.solr98.beyondintegration.network.RequestFtbTaskScanPacket;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftbquests.client.gui.quests.TabButton;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * FTB 任务界面顶部按钮：手动触发一次任务检测扫描（把主网络资源计入检测类任务）。
 * 图标使用 BD「维度网络发生器」物品；点击发送 C2S 请求，由服务端清缓存并执行检测。
 */
public class BeyondTaskScanButton extends TabButton {

    /** BD 维度网络发生器物品 ID（按钮图标） */
    private static final ResourceLocation ICON_ITEM = ResourceLocation.tryParse("beyonddimensions:net_creater");

    public BeyondTaskScanButton(Panel panel) {
        super(panel, Component.empty(), scanIcon());
    }

    /** 按钮图标：维度网络发生器（物品缺失时回退 FTB 内置图标） */
    private static Icon scanIcon() {
        try {
            var item = BuiltInRegistries.ITEM.get(ICON_ITEM);
            if (item != null) return ItemIcon.getItemIcon(new ItemStack(item));
        } catch (Throwable ignored) {}
        return dev.ftb.mods.ftblibrary.icon.Icons.ACCEPT;
    }

    @Override
    public void onClicked(MouseButton button) {
        if (!FtbIntegrationHelper.isEnabled()) return;
        playClickSound();
        PacketHandler.sendToServer(new RequestFtbTaskScanPacket());
    }

    @Override
    public void addMouseOverText(TooltipList list) {
        list.translate("beyond_integration.ftb.scan_button");
    }
}
