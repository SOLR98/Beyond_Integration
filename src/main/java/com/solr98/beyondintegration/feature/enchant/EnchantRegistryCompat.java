package com.solr98.beyondintegration.feature.enchant;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.21.1 附魔注册表访问兼容层。
 *
 * <p>1.20.1 的 {@code BuiltInRegistries.ENCHANTMENT} 在 1.21.1 已改为 <b>datapack 注册表</b>，
 * 必须经 {@code registryAccess().registryOrThrow(Registries.ENCHANTMENT)} 取得；
 * 附魔对象本身也从 {@code Enchantment} 变成 {@code Holder<Enchantment>}。
 *
 * <p>本类把所有「按 id 取附魔 / 按附魔取 id / 枚举全部附魔」的调用集中到一处，
 * 便于后续版本再变时只改这里。
 */
public final class EnchantRegistryCompat {

    private EnchantRegistryCompat() {}

    /** 取附魔注册表：优先服务端，其次客户端世界；都没有时返回 null。 */
    @Nullable
    public static Registry<Enchantment> registry() {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        }
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null) {
            return level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        }
        return null;
    }

    /** 数字 id → Holder（网络包用数字 id 传输附魔，与 BD 的 enchantClue 口径一致）。 */
    @Nullable
    public static Holder<Enchantment> byId(int id) {
        if (id < 0) return null;
        Registry<Enchantment> reg = registry();
        return reg == null ? null : reg.asHolderIdMap().byId(id);
    }

    /** Holder → 数字 id；取不到时 −1。 */
    public static int idOf(@Nullable Holder<Enchantment> holder) {
        Registry<Enchantment> reg = registry();
        if (reg == null || holder == null) return -1;
        return reg.asHolderIdMap().getId(holder);
    }

    /** ResourceLocation → Holder；找不到时 null。 */
    @Nullable
    public static Holder<Enchantment> byKey(@Nullable ResourceLocation id) {
        Registry<Enchantment> reg = registry();
        if (reg == null || id == null) return null;
        return reg.getHolder(id).orElse(null);
    }

    /** 全部附魔。 */
    public static List<Holder<Enchantment>> all() {
        Registry<Enchantment> reg = registry();
        if (reg == null) return List.of();
        List<Holder<Enchantment>> out = new ArrayList<>();
        reg.holders().forEach(out::add);
        return out;
    }
}
