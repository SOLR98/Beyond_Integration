package com.solr98.beyondintegration.feature.mana;

import com.solr98.beyondintegration.BeyondIntegration;
import com.wintercogs.beyonddimensions.api.storage.key.IStackRender;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.LongStackKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

/**
 * 网络"魔力"存储键（纯数值型，唯一实例）。
 * <p>类型 ID：{@code beyond_integration:stack_type/iron_mana}；与 {@code EnergyStackKey} / 灵魂键相互独立。
 * <p><b>旧 ID 兼容</b>：{@link #LEGACY_ID}（{@code beyond_integration:stack_type/mana}）仍会注册，
 * 保证旧存档中的魔力键可反序列化；服务器启动时由 {@code ManaKeyMigration} 迁移到新键。
 * <p><b>必须在任何网络反序列化之前注册</b>（见 {@link ManaKeyRegistration}），否则
 * {@code IStackKey.deserializeCommon / deserializeNBTCommon} 找不到类型会抛异常。
 */
public class ManaStackKey extends LongStackKey<ManaType> {

    public static final ResourceLocation ID =
            ResourceLocation.tryBuild(BeyondIntegration.MODID, "stack_type/iron_mana");

    /** 旧版魔力键 ID（兼容旧存档，仅用于反序列化与迁移）。 */
    public static final ResourceLocation LEGACY_ID =
            ResourceLocation.tryBuild(BeyondIntegration.MODID, "stack_type/mana");

    /** 唯一实例（不区分空/非空） */
    public static final ManaStackKey INSTANCE = new ManaStackKey(ID);

    /** 旧 ID 实例：仅为旧存档反序列化保留，数据在服务器启动后迁移到 {@link #INSTANCE}。 */
    public static final ManaStackKey LEGACY = new ManaStackKey(LEGACY_ID);

    private final ResourceLocation id;

    private ManaStackKey(ResourceLocation id) {
        this.id = id;
        this.stack = new ManaType(0);
    }

    @Override
    public @Nullable KeyAmount fromStackObject(Object stack) {
        if (stack instanceof ManaType type) {
            return new KeyAmount(this, type.getStackCount());
        }
        return null;
    }

    @Override
    public ResourceLocation getTypeID() {
        return id;
    }

    @Override
    public long getVanillaMaxStackSize() {
        return Integer.MAX_VALUE;
    }

    @Override
    public String getModId() {
        return BeyondIntegration.MODID;
    }

    @Override
    public @Nullable ManaStackKey fromSourceObject(Object key, CompoundTag ignored) {
        if (key instanceof ManaType || key instanceof Number) {
            return this;
        }
        return null;
    }

    @Override
    public @NotNull ManaType getSource() {
        return this.stack;
    }

    @Override
    public ManaStackKey getEmpty() {
        return this;
    }

    @Override
    public ManaType getEmptyStack() {
        return new ManaType(0);
    }

    @Override
    public boolean hasTag(TagKey<?> tagKey) {
        return false;
    }

    @Override
    public Stream<? extends TagKey<?>> getTags() {
        return Stream.empty();
    }

    @Override
    public void serialize(FriendlyByteBuf buf) {
    }

    @Override
    public @NotNull ManaStackKey deserialize(FriendlyByteBuf buf) {
        return this;
    }

    @Override
    public @NotNull CompoundTag serializeNBT() {
        return new CompoundTag();
    }

    @Override
    public @NotNull ManaStackKey deserializeNBT(CompoundTag nbt) {
        return this;
    }

    @Override
    public @NotNull IStackRender getRender() {
        return com.solr98.beyondintegration.client.render.ManaStackKeyRender.INSTANCE;
    }
}
