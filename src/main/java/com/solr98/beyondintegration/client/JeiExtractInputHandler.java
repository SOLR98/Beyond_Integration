package com.solr98.beyondintegration.client;

import com.solr98.beyondintegration.compat.RsIntegrationCompat;
import com.solr98.beyondintegration.jei.BeyondJeiNetworkHelper;
import com.solr98.beyondintegration.jei.JeiRuntimeHolder;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * JEI 点击取物（公开 API 版）：Shift+左键取一组 / Shift+右键取 1 个。
 * <p>
 * 通过 {@link IJeiRuntime} 的 {@code IIngredientListOverlay} / {@code IBookmarkOverlay}
 * 公开接口查询鼠标下物品，不再注入 JEI 内部类（FocusInputHandler / CombinedRecipeFocusSource），
 * 降低对 JEI 版本的耦合。
 * <p>
 * 事件以 {@link EventPriority#HIGHEST} 注册并取消：JEI 的监听器以 receiveCanceled=false 注册，
 * 取消后不会触发其默认行为（显示配方/用途）；未打开 BD 终端或无库存时不拦截，保持 JEI 原行为。
 * 检测到 rs_integration（RI）时让路禁用。
 */
public final class JeiExtractInputHandler {

    private JeiExtractInputHandler() {}

    /** 注册到 NeoForge 事件总线（客户端初始化时调用） */
    public static void register() {
        // 本模组不依赖 JEI：未安装时不注册（避免引用了 JEI 类型的监听被加载/校验）
        if (!ModList.get().isLoaded("jei")) return;
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,
                ScreenEvent.MouseButtonPressed.Pre.class, JeiExtractInputHandler::onMousePressed);
    }

    /** 鼠标按下：Shift + 左/右键且鼠标下有 JEI 物品且网络有库存 → 从网络取物并取消事件 */
    private static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        try {
            if (RsIntegrationCompat.isLoaded()) return;   // RI 让路
            if (!ModList.get().isLoaded("jei")) return;
            if (!Screen.hasShiftDown()) return;
            int button = event.getButton();
            if (button != 0 && button != 1) return;
            if (!(event.getScreen() instanceof AbstractContainerScreen<?>)) return;
            IJeiRuntime runtime = JeiRuntimeHolder.get();
            if (runtime == null) return;

            ItemStack stack = null;
            var listOverlay = runtime.getIngredientListOverlay();
            if (listOverlay != null && listOverlay.isListDisplayed()) {
                stack = listOverlay.getIngredientUnderMouse(VanillaTypes.ITEM_STACK);
            }
            if (stack == null || stack.isEmpty()) {
                var bookmarkOverlay = runtime.getBookmarkOverlay();
                if (bookmarkOverlay != null) {
                    stack = bookmarkOverlay.getItemStackUnderMouse();
                }
            }
            if (stack == null || stack.isEmpty()) return;
            if (BeyondJeiNetworkHelper.getNetworkCount(stack) <= 0) return;

            int amount = button == 0 ? Math.max(1, stack.getMaxStackSize()) : 1;
            BeyondJeiNetworkHelper.requestExtract(stack, amount);
            event.setCanceled(true);
        } catch (Throwable ignored) {}
    }
}
