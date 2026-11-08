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
import dev.compixel.sync.state.SyncCodec;
import dev.compixel.sync.state.SyncCodecs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 存储界面工作区里的「网络附魔台（神化）」工作台。
 * <p>与普通版 {@link EnchantTableWidget} 相同的槽位（装备 + 燃料）与动作（附魔 / 刷新），
 * 额外同步并可调整 <b>eterna / quanta / arcana</b> 三个神化参数（0~100）。
 * 逻辑在 {@link ApothEnchantTableMenu}，仅在工作区显示；要改三值时通过 {@link #SET_STATS} 动作提交。
 */
public class ApothEnchantTableWidget extends MenuWidget implements ClearableWidget {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("beyond_integration", "apoth_enchant_table");

    /** 四个参数一起提交（eterna/arcana 0~100，quanta 可为负，stable 为布尔；服务端会再夹取）。 */
    public record Stats(int eterna, int quanta, int arcana, boolean stable) {
    }

    private static final SyncCodec<Stats> STATS_CODEC = SyncCodec.of(
            "beyond_integration.apoth_stats/1",
            (out, stats) -> {
                out.writeInt(stats.eterna());
                out.writeInt(stats.quanta());
                out.writeInt(stats.arcana());
                out.writeBoolean(stats.stable());
            },
            in -> new Stats(in.readInt(), in.readInt(), in.readInt(), in.readBoolean()));

    public static final MenuAction<BDBaseMenu, Stats> SET_STATS = MenuAction.of(
            "beyond_integration.apoth_enchant_set_stats", STATS_CODEC, (menu, player, stats) -> {
                ApothEnchantTableWidget widget = menu.widget(ApothEnchantTableWidget.class);
                return widget != null && widget.setStats((ServerPlayer) player, stats);
            });

    public static final MenuAction<BDBaseMenu, Integer> ENCHANT = MenuAction.of(
            "beyond_integration.apoth_enchant_enchant", SyncCodecs.INT, (menu, player, slot) -> {
                ApothEnchantTableWidget widget = menu.widget(ApothEnchantTableWidget.class);
                return widget != null && widget.enchant((ServerPlayer) player, slot);
            });

    public static final MenuAction<BDBaseMenu, Boolean> REFRESH = MenuAction.of(
            "beyond_integration.apoth_enchant_refresh", SyncCodecs.BOOLEAN, (menu, player, ignored) -> {
                ApothEnchantTableWidget widget = menu.widget(ApothEnchantTableWidget.class);
                return widget != null && widget.refresh((ServerPlayer) player);
            });

    /** 附魔分离：把装备槽附魔物品的附魔分成附魔书入网（空值）。 */
    public static final MenuAction<BDBaseMenu, Boolean> SEPARATE = MenuAction.of(
            "beyond_integration.apoth_enchant_separate", SyncCodecs.BOOLEAN, (menu, player, ignored) -> {
                ApothEnchantTableWidget widget = menu.widget(ApothEnchantTableWidget.class);
                return widget != null && widget.separate((ServerPlayer) player);
            });

    /** 献祭：把鼠标拿取的书架物品献祭，累加其属性上限到网络存档（空值）。 */
    public static final MenuAction<BDBaseMenu, Boolean> SACRIFICE = MenuAction.of(
            "beyond_integration.apoth_enchant_sacrifice", SyncCodecs.BOOLEAN, (menu, player, ignored) -> {
                ApothEnchantTableWidget widget = menu.widget(ApothEnchantTableWidget.class);
                return widget != null && widget.sacrifice((ServerPlayer) player);
            });

    /** 切换“书架黑名单过滤附魔”开关（空值）。 */
    public static final MenuAction<BDBaseMenu, Boolean> TOGGLE_FILTER = MenuAction.of(
            "beyond_integration.apoth_enchant_toggle_filter", SyncCodecs.BOOLEAN, (menu, player, ignored) -> {
                ApothEnchantTableWidget widget = menu.widget(ApothEnchantTableWidget.class);
                return widget != null && widget.toggleFilter((ServerPlayer) player);
            });

    /** 手动加入一个过滤附魔（附魔 ID）。 */
    public static final MenuAction<BDBaseMenu, String> ADD_FILTER = MenuAction.of(
            "beyond_integration.apoth_enchant_add_filter", SyncCodecs.STRING, (menu, player, id) -> {
                ApothEnchantTableWidget widget = menu.widget(ApothEnchantTableWidget.class);
                return widget != null && widget.addFilter((ServerPlayer) player, id);
            });

    /** 手动移除一个过滤附魔（附魔 ID）。 */
    public static final MenuAction<BDBaseMenu, String> REMOVE_FILTER = MenuAction.of(
            "beyond_integration.apoth_enchant_remove_filter", SyncCodecs.STRING, (menu, player, id) -> {
                ApothEnchantTableWidget widget = menu.widget(ApothEnchantTableWidget.class);
                return widget != null && widget.removeFilter((ServerPlayer) player, id);
            });

    private final ApothEnchantTableMenu vanilla;
    private final IStackHandler storage;
    /** 客户端镜像：服务端通过 DataSlot 同步下来的四个参数。 */
    private final int[] clientStats = {30, 0, 0, 0};
    /** 客户端镜像：已献祭书架提供的四参数上限。 */
    private final int[] clientMax = {0, 0, 0, 0};
    /** 客户端镜像：书架黑名单过滤开关 / 黑名单数量。 */
    private int clientFilterEnabled = 0;
    private int clientFilteredCount = 0;
    /** 客户端镜像：宝藏附魔是否可用。 */
    private int clientTreasure = 0;

    public ApothEnchantTableWidget(BDBaseMenu menu, IStackHandler storage) {
        super(menu);
        this.storage = storage;
        this.vanilla = new ApothEnchantTableMenu(menu.player.getInventory(),
                menu.player.level().isClientSide()
                        ? ContainerLevelAccess.NULL
                        : ContainerLevelAccess.create(menu.player.level(), menu.player.blockPosition()));
        addSlot(vanilla.slots.get(ApothEnchantTableMenu.SLOT_ITEM));
        addSlot(vanilla.slots.get(ApothEnchantTableMenu.SLOT_FUEL));
        // 三档费用
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
        // 预览附魔 id / 等级
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
        // 三个参数（服务端读真实值，客户端写镜像）
        addData(new DataSlot() {
            @Override
            public int get() {
                return menu.player.level().isClientSide() ? clientStats[0] : vanilla.eterna();
            }

            @Override
            public void set(int value) {
                clientStats[0] = value;
            }
        });
        addData(new DataSlot() {
            @Override
            public int get() {
                return menu.player.level().isClientSide() ? clientStats[1] : vanilla.quanta();
            }

            @Override
            public void set(int value) {
                clientStats[1] = value;
            }
        });
        addData(new DataSlot() {
            @Override
            public int get() {
                return menu.player.level().isClientSide() ? clientStats[2] : vanilla.arcana();
            }

            @Override
            public void set(int value) {
                clientStats[2] = value;
            }
        });
        addData(new DataSlot() {
            @Override
            public int get() {
                return menu.player.level().isClientSide() ? clientStats[3] : (vanilla.stable() ? 1 : 0);
            }

            @Override
            public void set(int value) {
                clientStats[3] = value;
            }
        });
        // 四参数上限（书架提供）
        addData(new DataSlot() {
            @Override
            public int get() {
                return menu.player.level().isClientSide() ? clientMax[0] : vanilla.maxEterna();
            }

            @Override
            public void set(int value) {
                clientMax[0] = value;
            }
        });
        addData(new DataSlot() {
            @Override
            public int get() {
                return menu.player.level().isClientSide() ? clientMax[1] : vanilla.maxQuanta();
            }

            @Override
            public void set(int value) {
                clientMax[1] = value;
            }
        });
        addData(new DataSlot() {
            @Override
            public int get() {
                return menu.player.level().isClientSide() ? clientMax[2] : vanilla.maxArcana();
            }

            @Override
            public void set(int value) {
                clientMax[2] = value;
            }
        });
        addData(new DataSlot() {
            @Override
            public int get() {
                return menu.player.level().isClientSide() ? clientMax[3] : (vanilla.maxStable() ? 1 : 0);
            }

            @Override
            public void set(int value) {
                clientMax[3] = value;
            }
        });
        // 书架黑名单过滤开关 / 数量
        addData(new DataSlot() {
            @Override
            public int get() {
                return menu.player.level().isClientSide() ? clientFilterEnabled
                        : (netId() >= 0 && ApothStatData.filterEnabled(netId()) ? 1 : 0);
            }

            @Override
            public void set(int value) {
                clientFilterEnabled = value;
            }
        });
        addData(new DataSlot() {
            @Override
            public int get() {
                return menu.player.level().isClientSide() ? clientFilteredCount
                        : (netId() >= 0 ? ApothStatData.filtered(netId()).size() : 0);
            }

            @Override
            public void set(int value) {
                clientFilteredCount = value;
            }
        });
        // 宝藏附魔可用状态
        addData(new DataSlot() {
            @Override
            public int get() {
                return menu.player.level().isClientSide() ? clientTreasure
                        : (netId() >= 0 && (com.solr98.beyondintegration.CommandConfig.enchantAllowTreasure()
                                || ApothStatData.allowsTreasure(netId())) ? 1 : 0);
            }

            @Override
            public void set(int value) {
                clientTreasure = value;
            }
        });

        // 打开界面即把「网络存档里的属性上限」算出来：
        // 否则 maxEterna/maxQuanta/maxArcana 保持初值 0，recalc() 会把 eterna 一并压成 0，
        // 客户端 DataSlot 拿到的全是 0（表现为属性、上限、稳定全部丢失）。
        if (!menu.player.level().isClientSide()) {
            vanilla.refreshLimits();
        }
    }

    private int netId() {
        if (menu.player instanceof ServerPlayer sp) {
            com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet net =
                    com.solr98.beyondintegration.handler.NetMenuResolver.resolve(sp);
            return net == null ? -1 : net.getId();
        }
        return -1;
    }

    public int inputStart() {
        return start();
    }

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

    public int eterna() {
        return menu.player.level().isClientSide() ? clientStats[0] : vanilla.eterna();
    }

    public int quanta() {
        return menu.player.level().isClientSide() ? clientStats[1] : vanilla.quanta();
    }

    public int arcana() {
        return menu.player.level().isClientSide() ? clientStats[2] : vanilla.arcana();
    }

    public boolean stable() {
        return menu.player.level().isClientSide() ? clientStats[3] != 0 : vanilla.stable();
    }

    public int maxEterna() {
        return menu.player.level().isClientSide() ? clientMax[0] : vanilla.maxEterna();
    }

    public int maxQuanta() {
        return menu.player.level().isClientSide() ? clientMax[1] : vanilla.maxQuanta();
    }

    public int maxArcana() {
        return menu.player.level().isClientSide() ? clientMax[2] : vanilla.maxArcana();
    }

    public boolean maxStable() {
        return menu.player.level().isClientSide() ? clientMax[3] != 0 : vanilla.maxStable();
    }

    /** 是否需要献祭书架来提升上限（false 时献祭按钮隐藏）。 */
    public boolean requireSacrifice() {
        return vanilla.requireSacrifice();
    }

    public boolean filterEnabled() {
        return menu.player.level().isClientSide()
                ? clientFilterEnabled != 0
                : (netId() >= 0 && ApothStatData.filterEnabled(netId()));
    }

    public int filteredCount() {
        return menu.player.level().isClientSide()
                ? clientFilteredCount
                : (netId() >= 0 ? ApothStatData.filtered(netId()).size() : 0);
    }

    public boolean treasureAllowed() {
        return menu.player.level().isClientSide()
                ? clientTreasure != 0
                : (netId() >= 0 && (com.solr98.beyondintegration.CommandConfig.enchantAllowTreasure()
                        || ApothStatData.allowsTreasure(netId())));
    }

    public boolean refreshEnabled() {
        return vanilla.refreshEnabled();
    }

    public boolean setStats(ServerPlayer player, Stats stats) {
        if (menu.player != player) return false;
        vanilla.setStats(stats.eterna(), stats.quanta(), stats.arcana(), stats.stable());
        menu.broadcastChanges();
        sendPreview(player);
        return true;
    }

    public boolean enchant(ServerPlayer player, int slot) {
        if (menu.player != player) return false;
        boolean ok = vanilla.clickMenuButton(player, slot);
        if (ok) {
            menu.broadcastChanges();
            sendPreview(player);
        }
        return ok;
    }

    public boolean refresh(ServerPlayer player) {
        if (menu.player != player) return false;
        boolean ok = vanilla.doRefresh(player);
        if (ok) {
            menu.broadcastChanges();
            sendPreview(player);
        }
        return ok;
    }

    /** 附魔分离（服务端动作）：装备槽附魔物品分出单附魔书入网，装备去附魔保留本体。 */
    public boolean separate(ServerPlayer player) {
        if (menu.player != player) return false;
        com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet dimNet =
                com.solr98.beyondintegration.handler.NetMenuResolver.resolve(player);
        if (dimNet == null) return false;
        ItemStack item = vanilla.slots.get(ApothEnchantTableMenu.SLOT_ITEM).getItem();
        if (!EnchantItemSeparator.separate(item, dimNet)) return false;
        vanilla.slots.get(ApothEnchantTableMenu.SLOT_ITEM).setChanged();
        menu.broadcastChanges();
        sendPreview(player);
        return true;
    }

    /**
     * 献祭可行性评估结果（供 {@link #sacrifice} 与界面悬浮预览共用，不产生副作用）。
     * <p>{@code cur}/{@code next} 均为 {@code {eterna, quanta, arcana, stable(0|1)}}；
     * {@code ok=false} 时 {@code blockKey} 为提示语言的翻译键（可为 null 表示静默失败）。
     */
    public record SacrificeOutcome(boolean ok, String blockKey, int[] cur, int[] next) {}

    /**
     * 评估「把手中书架献祭给主网络」的结果，<b>不修改任何状态</b>。
     * <p>与 {@link #sacrifice} 用同一套夹取/上限规则，保证预览与实际执行一致。
     */
    private SacrificeOutcome evaluateSacrifice(ItemStack carried) {
        int[] zero = new int[]{0, 0, 0, 0};
        var owner = menu.player;
        if (carried == null || carried.isEmpty()) {
            return new SacrificeOutcome(false, "message.beyond_integration.apoth_sacrifice_no_item", zero, zero);
        }
        if (!com.solr98.beyondintegration.CommandConfig.enchantApothRequireSacrifice()) {
            return new SacrificeOutcome(false, "message.beyond_integration.apoth_sacrifice_disabled", zero, zero);
        }
        com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet dimNet =
                com.solr98.beyondintegration.handler.NetMenuResolver.resolve(owner);
        if (dimNet == null) {
            return new SacrificeOutcome(false, "message.beyond_integration.apoth_sacrifice_no_net", zero, zero);
        }

        int capEterna = com.solr98.beyondintegration.CommandConfig.enchantApothStatHardCapEterna();
        int capQuanta = com.solr98.beyondintegration.CommandConfig.enchantApothStatHardCapQuanta();
        int capArcana = com.solr98.beyondintegration.CommandConfig.enchantApothStatHardCapArcana();
        int[] add = ApothShelfScanner.itemStats(carried, owner.level(), owner.blockPosition(), 100);
        int[] cur = ApothStatData.get(dimNet.getId());
        if (add[0] == 0 && add[1] == 0 && add[2] == 0 && add[3] == 0) {
            // 不是提供属性的书架（普通方块/物品）
            return new SacrificeOutcome(false, "message.beyond_integration.apoth_sacrifice_not_shelf", cur, cur);
        }

        // 已满判定：eterna/arcana 达正向上限、quanta 达对应方向上限、稳定已具备
        boolean eternaFull = cur[0] >= capEterna;
        boolean arcanaFull = cur[2] >= capArcana;
        boolean stableFull = cur[3] > 0;
        boolean quantaFull = add[1] >= 0 ? cur[1] >= capQuanta : cur[1] <= -capQuanta;
        if (eternaFull && quantaFull && arcanaFull && stableFull) {
            return new SacrificeOutcome(false, "message.beyond_integration.apoth_stat_all_full", cur, cur);
        }

        // eterna 贡献后不超过配置上限，也不超过该书架自身的最高值（“最高 X”）；
        // 开启“无视书架属性上限”时忽略书架最高值
        int shelfMaxEterna = com.solr98.beyondintegration.CommandConfig.enchantApothIgnoreShelfMax()
                ? 0
                : ApothShelfScanner.shelfMaxEterna(carried, owner.level(), owner.blockPosition());
        boolean anyGain = false;
        int[] next = new int[4];

        // eterna：只增不减，上限受配置与书架自身 max 双重约束
        int limitEterna = shelfMaxEterna > 0 ? Math.min(capEterna, shelfMaxEterna) : capEterna;
        next[0] = Math.max(cur[0], Math.min(limitEterna, cur[0] + add[0]));
        if (next[0] > cur[0]) anyGain = true;

        // quanta：允许负值（负贡献书架削减波动），累加后双向夹取
        next[1] = Math.max(-capQuanta, Math.min(capQuanta, cur[1] + add[1]));
        if (next[1] != cur[1]) anyGain = true;

        // arcana：只增不减
        next[2] = Math.max(cur[2], Math.min(capArcana, cur[2] + add[2]));
        if (next[2] > cur[2]) anyGain = true;

        // 稳定：OR 语义（只要有一个 providesStability 的书架献祭过就具备）
        next[3] = (cur[3] > 0 || add[3] > 0) ? 1 : 0;
        if (next[3] > cur[3]) anyGain = true;

        if (!anyGain) {
            return new SacrificeOutcome(false, "message.beyond_integration.apoth_shelf_stat_full", cur, next);
        }
        return new SacrificeOutcome(true, null, cur, next);
    }

    /** 供界面悬浮预览：当前值 / 献祭后值 / 可否献祭（无副作用）。 */
    public SacrificeOutcome previewSacrifice() {
        return evaluateSacrifice(menu.getCarried());
    }

    /** 献祭（服务端动作）：把鼠标拿取的书架物品消耗掉，累加其属性上限到网络存档。 */
    public boolean sacrifice(ServerPlayer player) {
        if (menu.player != player) return false;
        ItemStack carried = menu.getCarried();
        SacrificeOutcome outcome = evaluateSacrifice(carried);
        if (!outcome.ok()) {
            if (outcome.blockKey() != null) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(outcome.blockKey()));
            }
            return false;
        }
        com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet dimNet =
                com.solr98.beyondintegration.handler.NetMenuResolver.resolve(player);
        if (dimNet == null) return false;

        // 献祭只消耗书架本身，不再额外收取网络经验
        ApothStatData.set(dimNet.getId(), outcome.next());

        // 若该书架允许宝藏附魔，记录到网络
        if (carried.getItem() instanceof net.minecraft.world.item.BlockItem bi) {
            net.minecraft.world.level.block.state.BlockState state = bi.getBlock().defaultBlockState();
            if (state.getBlock() instanceof dev.shadowsoffire.apothic_enchanting.api.EnchantmentStatBlock eb) {
                if (eb.allowsTreasure(state, player.level(), player.blockPosition())) {
                    ApothStatData.setTreasure(dimNet.getId(), true);
                }
                java.util.Set<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>> black =
                        eb.getBlacklistedEnchantments(state, player.level(), player.blockPosition());
                if (black != null && !black.isEmpty()) {
                    java.util.Set<String> ids = new java.util.HashSet<>();
                    for (net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> eh : black) {
                        net.minecraft.resources.ResourceLocation rl =
                                eh.unwrapKey().map(k -> k.location()).orElse(null);
                        if (rl != null) ids.add(rl.toString());
                    }
                    ApothStatData.addFiltered(dimNet.getId(), ids);
                    ApothStatData.setFilterEnabled(dimNet.getId(), true);
                }
            }
        }

        ItemStack after = carried.copy();
        after.shrink(1);
        menu.setCarried(after.isEmpty() ? ItemStack.EMPTY : after);

        vanilla.refreshLimits();
        menu.broadcastChanges();
        sendPreview(player);
        return true;
    }

    /** 切换书架黑名单过滤开关（服务端动作）。 */
    public boolean toggleFilter(ServerPlayer player) {
        if (menu.player != player) return false;
        com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet dimNet =
                com.solr98.beyondintegration.handler.NetMenuResolver.resolve(player);
        if (dimNet == null) return false;
        ApothStatData.setFilterEnabled(dimNet.getId(), !ApothStatData.filterEnabled(dimNet.getId()));
        vanilla.refreshLimits();
        menu.broadcastChanges();
        sendPreview(player);
        return true;
    }

    /** 手动加入过滤附魔（服务端动作）。 */
    public boolean addFilter(ServerPlayer player, String id) {
        if (menu.player != player) return false;
        if (id == null || id.isEmpty()) return false;
        com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet dimNet =
                com.solr98.beyondintegration.handler.NetMenuResolver.resolve(player);
        if (dimNet == null) return false;
        ApothStatData.addFiltered(dimNet.getId(), java.util.List.of(id));
        ApothStatData.setFilterEnabled(dimNet.getId(), true);
        vanilla.refreshLimits();
        menu.broadcastChanges();
        sendPreview(player);
        return true;
    }

    /** 手动移除过滤附魔（服务端动作）。 */
    public boolean removeFilter(ServerPlayer player, String id) {
        if (menu.player != player) return false;
        if (id == null || id.isEmpty()) return false;
        com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet dimNet =
                com.solr98.beyondintegration.handler.NetMenuResolver.resolve(player);
        if (dimNet == null) return false;
        ApothStatData.removeFiltered(dimNet.getId(), id);
        vanilla.refreshLimits();
        menu.broadcastChanges();
        sendPreview(player);
        return true;
    }

    @Override
    public <M extends BDBaseMenu> void addActions(MenuSync<M> sync) {
        sync.action(SET_STATS).action(ENCHANT).action(REFRESH).action(SEPARATE).action(SACRIFICE)
                .action(TOGGLE_FILTER).action(ADD_FILTER).action(REMOVE_FILTER);
    }

    @Override
    public void addQuickMoveRoutes(SlotTransferRoutes.Builder routes) {
        routes.group("bi_apoth_enchant_table", inputStart(), end()).route("bi_apoth_enchant_table", "storage");
    }

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
        List<List<com.solr98.beyondintegration.client.ApothPreviewState.Entry>> previews =
                new ArrayList<>(3);
        for (int i = 0; i < 3; i++) {
            List<com.solr98.beyondintegration.client.ApothPreviewState.Entry> list = new ArrayList<>();
            for (net.minecraft.world.item.enchantment.EnchantmentInstance ei : vanilla.preview(i)) {
                list.add(new com.solr98.beyondintegration.client.ApothPreviewState.Entry(
                        EnchantRegistryCompat.idOf(ei.enchantment),
                        ei.level));
            }
            previews.add(list);
        }
        java.util.List<String> filtered = new java.util.ArrayList<>();
        com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet dimNet =
                com.solr98.beyondintegration.handler.NetMenuResolver.resolve(player);
        if (dimNet != null) filtered.addAll(ApothStatData.filtered(dimNet.getId()));
        com.solr98.beyondintegration.network.PacketHandler.sendToPlayer(
                player,
                new com.solr98.beyondintegration.network.ApothPreviewPacket(
                        menu.containerId, previews, filtered));
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
