package com.solr98.beyondintegration.feature.enchant;

import com.wintercogs.beyonddimensions.api.menu.widget.ClearableWidget;
import com.wintercogs.beyonddimensions.api.menu.widget.MenuWidget;
import com.wintercogs.beyonddimensions.api.menu.workbench.PlayerWorkbenches;
import com.wintercogs.beyonddimensions.api.storage.handler.IStackHandler;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import com.wintercogs.beyonddimensions.common.menu.widget.CraftingWidget;
import dev.compixel.forge.sync.MenuAction;
import dev.compixel.forge.sync.MenuSync;
import dev.compixel.slots.SlotTransferRoutes;
import dev.compixel.sync.state.SyncCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 存储界面工作区里的「网络附魔台」工作台（普通/原版规则）。
 * <p>两个槽：装备与燃料（青金石 / {@code #forge:enchanting_fuels}）；三档费用与预览附魔由
 * {@link EnchantTableMenu} 在服务端计算，经 {@code addData} 同步给客户端。
 * 两个动作：{@link #ENCHANT}（选一档附魔）与 {@link #REFRESH}（消耗燃料刷新结果）。
 * 附魔扣网络 XP（不足时玩家补足）与燃料，结果直接写回装备槽（原版附魔台无产物槽）。
 */
public class EnchantTableWidget extends MenuWidget implements ClearableWidget {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("beyond_integration", "enchant_table");

    /** 附魔：{@code 0..2} 对应三档。 */
    public static final MenuAction<BDBaseMenu, Integer> ENCHANT = MenuAction.of(
            "beyond_integration.enchant_table_enchant", SyncCodecs.INT, (menu, player, slot) -> {
                EnchantTableWidget widget = menu.widget(EnchantTableWidget.class);
                return widget != null && widget.enchant((ServerPlayer) player, slot);
            });

    /** 刷新：消耗燃料换一组结果（空值）。 */
    public static final MenuAction<BDBaseMenu, Boolean> REFRESH = MenuAction.of(
            "beyond_integration.enchant_table_refresh", SyncCodecs.BOOLEAN, (menu, player, ignored) -> {
                EnchantTableWidget widget = menu.widget(EnchantTableWidget.class);
                return widget != null && widget.refresh((ServerPlayer) player);
            });

    /** 附魔分离：把装备槽附魔物品的附魔分成附魔书入网（空值）。 */
    public static final MenuAction<BDBaseMenu, Boolean> SEPARATE = MenuAction.of(
            "beyond_integration.enchant_table_separate", SyncCodecs.BOOLEAN, (menu, player, ignored) -> {
                EnchantTableWidget widget = menu.widget(EnchantTableWidget.class);
                return widget != null && widget.separate((ServerPlayer) player);
            });

    private final EnchantTableMenu vanilla;
    private final IStackHandler storage;

    public EnchantTableWidget(BDBaseMenu menu, IStackHandler storage) {
        super(menu);
        this.storage = storage;
        this.vanilla = new EnchantTableMenu(menu.player.getInventory(),
                menu.player.level().isClientSide()
                        ? net.minecraft.world.inventory.ContainerLevelAccess.NULL
                        : net.minecraft.world.inventory.ContainerLevelAccess.create(menu.player.level(), menu.player.blockPosition()));
        addSlot(vanilla.slots.get(EnchantTableMenu.SLOT_ITEM));
        addSlot(vanilla.slots.get(EnchantTableMenu.SLOT_FUEL));
        // 三档费用 + 预览附魔（附魔 id / 等级）
        for (int i = 0; i < 3; i++) {
            final int index = i;
            addData(new DataSlot() {
                @Override
                public int get() {
                    return vanilla.costs[index];
                }

                @Override
                public void set(int value) {
                    vanilla.costs[index] = value;
                }
            });
        }
        for (int i = 0; i < 3; i++) {
            final int index = i;
            addData(new DataSlot() {
                @Override
                public int get() {
                    return vanilla.enchantClue[index];
                }

                @Override
                public void set(int value) {
                    vanilla.enchantClue[index] = value;
                }
            });
        }
        for (int i = 0; i < 3; i++) {
            final int index = i;
            addData(new DataSlot() {
                @Override
                public int get() {
                    return vanilla.levelClue[index];
                }

                @Override
                public void set(int value) {
                    vanilla.levelClue[index] = value;
                }
            });
        }
    }

    /** 装备槽在菜单 {@code slots} 里的下标。 */
    public int inputStart() {
        return start();
    }

    /** 燃料槽下标。 */
    public int fuelSlot() {
        return start() + 1;
    }

    public int cost(int slot) {
        return slot >= 0 && slot < 3 ? vanilla.costs[slot] : 0;
    }

    public int clue(int slot) {
        return slot >= 0 && slot < 3 ? vanilla.enchantClue[slot] : -1;
    }

    public int level(int slot) {
        return slot >= 0 && slot < 3 ? vanilla.levelClue[slot] : -1;
    }

    public boolean refreshEnabled() {
        return vanilla.refreshEnabled();
    }

    /** 执行附魔（服务端动作）。 */
    public boolean enchant(ServerPlayer player, int slot) {
        if (menu.player != player) return false;
        boolean ok = vanilla.clickMenuButton(player, slot);
        if (ok) sendPreview(player);
        return ok;
    }

    /** 刷新（服务端动作）。 */
    public boolean refresh(ServerPlayer player) {
        if (menu.player != player) return false;
        boolean ok = vanilla.doRefresh(player);
        if (ok) sendPreview(player);
        return ok;
    }

    /** 附魔分离（服务端动作）：装备槽附魔物品分出单附魔书入网，装备去附魔保留本体。 */
    public boolean separate(ServerPlayer player) {
        if (menu.player != player) return false;
        com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet net =
                com.solr98.beyondintegration.handler.NetMenuResolver.resolve(player);
        if (net == null) return false;
        ItemStack item = vanilla.slots.get(EnchantTableMenu.SLOT_ITEM).getItem();
        if (!EnchantItemSeparator.separate(item, net)) return false;
        vanilla.slots.get(EnchantTableMenu.SLOT_ITEM).setChanged();
        menu.broadcastChanges();
        sendPreview(player);
        return true;
    }

    /** 把三档完整预览附魔列表发给玩家（C2S 请求的应答）；服务端开关关闭时只下发空列表。 */
    public void sendPreview(ServerPlayer player) {
        if (menu.player != player) return;
        if (!com.solr98.beyondintegration.CommandConfig.enchantPreviewEnabled()) {
            com.solr98.beyondintegration.network.PacketHandler.sendToPlayer(
                    player,
                    new com.solr98.beyondintegration.network.ApothPreviewPacket(
                            menu.containerId, java.util.List.of(), java.util.List.of()));
            return;
        }
        java.util.List<java.util.List<com.solr98.beyondintegration.client.ApothPreviewState.Entry>> previews =
                new java.util.ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            java.util.List<com.solr98.beyondintegration.client.ApothPreviewState.Entry> list =
                    new java.util.ArrayList<>();
            for (net.minecraft.world.item.enchantment.EnchantmentInstance ei : vanilla.preview(i)) {
                list.add(new com.solr98.beyondintegration.client.ApothPreviewState.Entry(
                        EnchantRegistryCompat.idOf(ei.enchantment),
                        ei.level));
            }
            previews.add(list);
        }
        com.solr98.beyondintegration.network.PacketHandler.sendToPlayer(
                player,
                new com.solr98.beyondintegration.network.ApothPreviewPacket(
                        menu.containerId, previews, java.util.List.of()));
    }

    @Override
    public <M extends BDBaseMenu> void addActions(MenuSync<M> sync) {
        sync.action(ENCHANT).action(REFRESH).action(SEPARATE);
    }

    @Override
    public void addQuickMoveRoutes(SlotTransferRoutes.Builder routes) {
        routes.group("bi_enchant_table", inputStart(), end()).route("bi_enchant_table", "storage");
    }

    // ── ClearableWidget ──

    @Override
    public void clearItems(boolean toStorageFirst) {
        if (!(menu.player instanceof ServerPlayer)) return;
        for (int i = inputStart(); i < end(); i++) {
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            slot.set(ItemStack.EMPTY);
            CraftingWidget.returnStack(menu.player, storage, stack.copy(), toStorageFirst);
        }
    }

    @Override
    public void removed(@NotNull Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        PlayerWorkbenches workbenches = PlayerWorkbenches.of(player);
        if (!workbenches.keep() && !serverPlayer.hasDisconnected()) {
            clearItems(workbenches.returnToStorage());
        }
    }
}
