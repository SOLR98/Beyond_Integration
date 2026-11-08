package com.solr98.beyondintegration.common.menu;

import com.solr98.beyondintegration.feature.netpathway.NetPathwayFilterAccess;
import com.solr98.beyondintegration.init.ModMenus;
import com.wintercogs.beyonddimensions.api.menu.BdMenuActions;
import com.wintercogs.beyonddimensions.api.storage.handler.impl.StackHandler;
import com.wintercogs.beyonddimensions.common.menu.BDBaseMenu;
import com.wintercogs.beyonddimensions.common.menu.sync.BDMenuResources;
import com.wintercogs.beyonddimensions.common.menu.widget.slot.FlagStackTypedSlot;
import dev.compixel.forge.sync.MenuAction;
import dev.compixel.forge.sync.MenuSync;
import dev.compixel.sync.state.SyncCodec;
import dev.compixel.sync.state.SyncCodecs;
import dev.compixel.sync.state.SyncSchema;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;

/**
 * 维度网络通道（net_pathway）过滤界面（BD 0.8.3 / Compixel SyncedMenu 体系）。
 * <p>内容：标记槽（白名单样品，尺寸 = 全局行数×9）+ 三个开关
 * （启用过滤 / 仅接收输入 / 模糊过滤）。开关经 {@link SyncSchema} 由服务端下发，
 * 客户端改动经 {@link MenuAction} 提交，服务端写入方块实体并回同步。
 */
public class NetPathwayFilterMenu extends BDBaseMenu {

    /** 客户端提交的开关切换：{@code index} 0=启用过滤 1=仅接收输入 2=模糊过滤。 */
    public record Toggle(int index, boolean value) {
    }

    private static final SyncCodec<Toggle> TOGGLE_CODEC = SyncCodec.of(
            "beyond_integration:net_pathway_toggle/1",
            (out, toggle) -> {
                out.writeByte(toggle.index());
                out.writeBoolean(toggle.value());
            },
            in -> new Toggle(in.readUnsignedByte(), in.readBoolean()));

    public static final MenuAction<NetPathwayFilterMenu, Toggle> TOGGLE_ACTION =
            // CompixelUI 0.1.16+ 起 SyncAction 的 id 只允许 [a-zA-Z0-9_.-]（不能带冒号），
            // 否则类初始化即抛 IllegalArgumentException: Invalid action ID
            MenuAction.of("beyond_integration.net_pathway_toggle", TOGGLE_CODEC,
                    (menu, player, toggle) -> menu.applyToggle(toggle));

    private static final SyncSchema<NetPathwayFilterMenu> SCHEMA =
            SyncSchema.<NetPathwayFilterMenu>builder("beyond_integration:net_pathway_filter", 3)
                    .field("filter_enabled", SyncCodecs.BOOLEAN,
                            m -> m.filterEnabled, (m, v) -> m.filterEnabled = v)
                    .field("only_input", SyncCodecs.BOOLEAN,
                            m -> m.onlyInput, (m, v) -> m.onlyInput = v)
                    .field("fuzzy", SyncCodecs.BOOLEAN,
                            m -> m.fuzzy, (m, v) -> m.fuzzy = v)
                    .build();

    /** 标记槽（白名单样品）。 */
    public final StackHandler filterSlots;
    /** 服务端为方块实体过滤访问器；客户端可能为 null（方块实体缺失）。 */
    public final NetPathwayFilterAccess access;

    /** 同步字段（服务端写入、客户端只读）。 */
    private boolean filterEnabled;
    private boolean onlyInput;
    private boolean fuzzy;

    private final MenuSync<NetPathwayFilterMenu> synchronization;

    @Override
    public MenuSync<NetPathwayFilterMenu> menuSync() {
        return synchronization;
    }

    /** 客户端构造：从方块坐标解析方块实体。 */
    public NetPathwayFilterMenu(int id, Inventory inventory, FriendlyByteBuf data) {
        this(id, inventory, resolve(inventory, data));
    }

    private static NetPathwayFilterAccess resolve(Inventory inventory, FriendlyByteBuf data) {
        var pos = data.readBlockPos();
        return inventory.player.level().getBlockEntity(pos) instanceof NetPathwayFilterAccess access
                ? access : null;
    }

    /** 服务端 / 双端构造。 */
    public NetPathwayFilterMenu(int id, Inventory inventory, NetPathwayFilterAccess access) {
        super(ModMenus.NET_PATHWAY_FILTER.get(), id, inventory);
        this.access = access;
        this.filterSlots = access == null ? new StackHandler(0) : access.beyond$getFilterSlots();

        // 标记槽
        int slots = filterSlots.getSlots();
        for (int i = 0; i < slots; i++) {
            int col = i % 9;
            int row = i / 9;
            addSlot(new FlagStackTypedSlot(this, filterSlots, i, 8 + col * 18, markerTop() + row * 18));
        }

        // 玩家背包
        int invTop = inventoryTop();
        inventoryStartIndex = this.slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, invTop + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, invTop + 58));
        }
        inventoryEndIndex = this.slots.size();

        synchronization = BdMenuActions.attach(this,
                commands()
                        // 槽位点击（放入/取出标记）走 BD 的命令体系；不接的话 Compixel 界面的槽位点击没有服务端处理
                        .inventory(BDMenuResources.bind(this, SCHEMA))
                        .action(TOGGLE_ACTION));
    }

    // ── 布局（屏幕复用同一套计算） ──

    /** 标记槽第一行顶部 Y。 */
    public int markerTop() {
        return 18;
    }

    /** 标记槽行数。 */
    public int markerRows() {
        return Math.max(1, (filterSlots.getSlots() + 8) / 9);
    }

    /** 玩家背包槽顶部 Y（标记槽区 + 开关按钮行之后）。 */
    public int inventoryTop() {
        return markerTop() + markerRows() * 18 + 26;
    }

    // ── 同步 ──

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (player.level().isClientSide() || access == null) return;
        // 服务端：把方块实体当前状态同步给客户端
        this.filterEnabled = access.beyond$isFilterEnabled();
        this.onlyInput = access.beyond$isOnlyInput();
        this.fuzzy = access.beyond$isFuzzy();
    }

    /** 服务端处理开关切换（客户端值不可信，以方块实体为准回填）。 */
    private boolean applyToggle(Toggle toggle) {
        if (access == null) return false;
        switch (toggle.index()) {
            case 0 -> access.beyond$setFilterEnabled(toggle.value());
            case 1 -> access.beyond$setOnlyInput(toggle.value());
            case 2 -> access.beyond$setFuzzy(toggle.value());
            default -> {
                return false;
            }
        }
        this.filterEnabled = access.beyond$isFilterEnabled();
        this.onlyInput = access.beyond$isOnlyInput();
        this.fuzzy = access.beyond$isFuzzy();
        return true;
    }

    /** 客户端：请求切换某个开关。 */
    public boolean requestToggle(int index, boolean value) {
        return synchronization.request(TOGGLE_ACTION, new Toggle(index, value)).queued();
    }

    public boolean filterEnabled() {
        return filterEnabled;
    }

    public boolean onlyInput() {
        return onlyInput;
    }

    public boolean fuzzy() {
        return fuzzy;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return access instanceof net.minecraft.world.level.block.entity.BlockEntity be && !be.isRemoved();
    }
}
