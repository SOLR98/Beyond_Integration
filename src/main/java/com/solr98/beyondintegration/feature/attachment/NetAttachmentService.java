package com.solr98.beyondintegration.feature.attachment;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.wintercogs.beyonddimensions.api.dimensionnet.DimensionsNet;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 网络配件服务（服务端）：从玩家主网络列出/安装枪械配件。
 * <p>列出：服务端只按类型筛选 + 按 {@code attachmentId} 去重（可安装性由客户端按 tacz 口径过滤）。
 * <p>安装：从网络提取 1 个配件 → 写入枪 NBT → 换下的旧配件写回网络（失败则背包/掉落）→ 刷新属性。
 * <p>刷新由客户端驱动（{@code NetAttachmentRefreshTicker}）；服务端只同步物品栏数据。
 */
public final class NetAttachmentService {

    /** 网络配件摘要（去重后）：attachmentId + 总数量。 */
    public record Summary(ResourceLocation attachmentId, int count) {}

    private NetAttachmentService() {}

    /** 列出某网络里指定类型（槽位）的全部配件（按 attachmentId 去重并累加数量）。 */
    public static List<Summary> list(DimensionsNet net, AttachmentType type) {
        if (net == null || type == null || type == AttachmentType.NONE) return List.of();
        Map<ResourceLocation, Summary> dedup = new LinkedHashMap<>();
        for (KeyAmount ka : net.getUnifiedStorage().getStorage()) {
            if (!(ka.key() instanceof ItemStackKey ik)) continue;
            ItemStack stack = ik.getReadOnlyStack();
            IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
            if (attachment == null || attachment.getType(stack) != type) continue;
            ResourceLocation id = attachment.getAttachmentId(stack);
            if (id == null) continue;
            long amount = ka.amount();
            if (amount <= 0L) continue;
            Summary previous = dedup.get(id);
            long sum = (previous == null ? 0L : (long) previous.count()) + amount;
            dedup.put(id, new Summary(id, (int) Math.min(sum, Integer.MAX_VALUE)));
        }
        return new ArrayList<>(dedup.values());
    }

    /** 从网络安装配件到主手枪上；失败返回 false（不产生任何状态变化）。 */
    public static boolean install(ServerPlayer player, DimensionsNet net,
                                  AttachmentType type, ResourceLocation attachmentId) {
        if (player == null || net == null || attachmentId == null
                || type == null || type == AttachmentType.NONE) return false;
        ItemStack gun = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null || iGun.hasAttachmentLock(gun)) return false;
        if (!iGun.allowAttachmentType(gun, type)) return false;
        HolderLookup.Provider provider = player.level().registryAccess();

        ItemStackKey foundKey = null;
        ItemStack foundStack = null;
        for (KeyAmount ka : net.getUnifiedStorage().getStorage()) {
            if (!(ka.key() instanceof ItemStackKey ik)) continue;
            ItemStack stack = ik.getReadOnlyStack();
            IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
            if (attachment == null) continue;
            if (!attachmentId.equals(attachment.getAttachmentId(stack))) continue;
            if (attachment.getType(stack) != type) continue;
            foundKey = ik;
            foundStack = stack;
            break;
        }
        if (foundKey == null || foundStack == null) return false;
        if (!iGun.allowAttachment(gun, foundStack)) return false;

        KeyAmount taken = net.getUnifiedStorage().extract(foundKey, 1L, false, false);
        if (taken.amount() != 1L) return false;
        net.setDirty();

        ItemStack oldAttachment = iGun.getAttachment(provider, gun, type);
        iGun.installAttachment(provider, gun, foundStack);
        AttachmentPropertyManager.postChangeEvent(player, gun);

        // 校验是否真的装上；未装上则回滚（新件插回网络、旧件恢复）
        ItemStack installed = iGun.getAttachment(provider, gun, type);
        IAttachment installedAttachment = IAttachment.getIAttachmentOrNull(installed);
        if (installedAttachment == null || !attachmentId.equals(installedAttachment.getAttachmentId(installed))) {
            KeyAmount rollbackLeftover = net.getUnifiedStorage().insert(new ItemStackKey(foundStack), 1L, false);
            if (rollbackLeftover.amount() > 0L && !player.getInventory().add(foundStack)) {
                player.drop(foundStack, false);
            }
            if (!oldAttachment.isEmpty()) {
                iGun.installAttachment(provider, gun, oldAttachment);
            }
            AttachmentPropertyManager.postChangeEvent(player, gun);
            net.setDirty();
            player.inventoryMenu.broadcastChanges();
            return false;
        }

        if (type == AttachmentType.EXTENDED_MAG) {
            iGun.dropAllAmmo(player, gun);
        }

        if (!oldAttachment.isEmpty()) {
            // BD 的 insert 返回值是「未插入的余量」（0 = 全部插入成功），不是插入量
            KeyAmount leftover = net.getUnifiedStorage().insert(new ItemStackKey(oldAttachment), 1L, false);
            if (leftover.amount() > 0L) {
                if (!player.getInventory().add(oldAttachment)) {
                    player.drop(oldAttachment, false);
                }
            }
            net.setDirty();
        }

        player.inventoryMenu.broadcastChanges();
        return true;
    }
}
