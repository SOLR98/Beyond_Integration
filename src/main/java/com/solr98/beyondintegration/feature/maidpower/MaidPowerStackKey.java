package com.solr98.beyondintegration.feature.maidpower;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.solr98.beyondintegration.BeyondIntegration;
import com.wintercogs.beyonddimensions.api.storage.key.IStackRender;
import com.wintercogs.beyonddimensions.api.storage.key.KeyAmount;
import com.wintercogs.beyonddimensions.api.storage.key.impl.LongStackKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

/**
 * 网络「女仆 P 点」存储键（纯数值型，唯一实例）。
 * <p>类型 ID：{@code beyond_integration:stack_type/maid_power}；单位为
 * {@code EntityPowerPoint#getValue()}（100 = 1.0 点）。
 * <p><b>必须在任何网络反序列化之前注册</b>（见 {@link MaidPowerKeyRegistration}）。
 */
public class MaidPowerStackKey extends LongStackKey<MaidPowerType> {

    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(BeyondIntegration.MODID, "stack_type/maid_power");

    public static final MaidPowerStackKey INSTANCE = new MaidPowerStackKey(ID);

    public static final MapCodec<MaidPowerStackKey> TYPE_CODEC = new MapCodec<>() {
        @Override
        public <T> DataResult<MaidPowerStackKey> decode(DynamicOps<T> ops, MapLike<T> input) {
            return DataResult.success(MaidPowerStackKey.INSTANCE);
        }

        @Override
        public <T> RecordBuilder<T> encode(MaidPowerStackKey value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            return prefix;
        }

        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return Stream.empty();
        }
    };

    private final ResourceLocation id;

    private MaidPowerStackKey(ResourceLocation id) {
        this.id = id;
        this.stack = new MaidPowerType(0);
    }

    @Override
    public MapCodec<MaidPowerStackKey> codec() {
        return TYPE_CODEC;
    }

    @Override
    public @Nullable KeyAmount fromStackObject(Object stack) {
        if (stack instanceof MaidPowerType type) {
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
    public @Nullable MaidPowerStackKey fromSourceObject(Object key, DataComponentPatch ignored) {
        if (key instanceof MaidPowerType || key instanceof Number) {
            return this;
        }
        return null;
    }

    @Override
    public @NotNull MaidPowerType getSource() {
        return this.stack;
    }

    @Override
    public MaidPowerStackKey getEmpty() {
        return this;
    }

    @Override
    public MaidPowerType getEmptyStack() {
        return new MaidPowerType(0);
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
    public void serialize(RegistryFriendlyByteBuf buf) {
    }

    @Override
    public @NotNull MaidPowerStackKey deserialize(RegistryFriendlyByteBuf buf) {
        return this;
    }

    @Override
    public @NotNull CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return new CompoundTag();
    }

    @Override
    public @NotNull MaidPowerStackKey deserializeNBT(CompoundTag nbt, HolderLookup.Provider provider) {
        return this;
    }

    @Override
    public @NotNull IStackRender getRender() {
        // 复用灵魂能量的图标渲染，避免新增客户端渲染类
        return com.solr98.beyondintegration.client.render.SoulEnergyStackKeyRender.INSTANCE;
    }
}
