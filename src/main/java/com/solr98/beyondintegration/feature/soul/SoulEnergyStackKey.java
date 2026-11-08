package com.solr98.beyondintegration.feature.soul;

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
 * 网络"灵魂能量"存储键（纯数值型，唯一实例）。
 * <p>BI 自己的类型 ID：{@code beyond_integration:stack_type/soul_energy}；与 {@code EnergyStackKey} 相互独立。
 * <p><b>RI 兜底</b>：{@link #RI_FALLBACK} 使用 RI 的 ID
 * {@code beyonddimensions:stack_type/goety_soul}。当 RI 未安装（或已移除）时由
 * {@link SoulKeyRegistration#ensureRiKeyFallback()} 注册，保证已迁移到该 ID 的既有灵魂数据
 * 仍能被 BD 反序列化并读取。
 * <p><b>必须在任何网络反序列化之前注册</b>（见 {@link SoulKeyRegistration}），否则
 * {@code IStackKey.deserializeCommon / deserializeNBTCommon} 找不到类型会抛异常。
 */
public class SoulEnergyStackKey extends LongStackKey<SoulEnergyType> {

    public static final ResourceLocation ID =
            ResourceLocation.tryBuild(BeyondIntegration.MODID, "stack_type/soul_energy");

    /** RI（rs_integration）的 BD 灵魂键 ID。 */
    public static final ResourceLocation RI_FALLBACK_ID =
            ResourceLocation.tryParse("beyonddimensions:stack_type/goety_soul");

    /** BI 自己的灵魂键实例。 */
    public static final SoulEnergyStackKey INSTANCE = new SoulEnergyStackKey(ID);
    /** RI 缺席时注册到 RI ID 的兜底实例（与 {@link SoulEnergyStackKey} 数据类型一致）。 */
    public static final SoulEnergyStackKey RI_FALLBACK = new SoulEnergyStackKey(RI_FALLBACK_ID);

    private final ResourceLocation id;

    private SoulEnergyStackKey(ResourceLocation id) {
        this.id = id;
        this.stack = new SoulEnergyType(0);
    }

    @Override
    public @Nullable KeyAmount fromStackObject(Object stack) {
        if (stack instanceof SoulEnergyType type) {
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
        // 单次接口方块传输上限：用 int 安全上限
        return Integer.MAX_VALUE;
    }

    @Override
    public String getModId() {
        return BeyondIntegration.MODID;
    }

    @Override
    public @Nullable SoulEnergyStackKey fromSourceObject(Object key, CompoundTag ignored) {
        if (key instanceof SoulEnergyType || key instanceof Number) {
            return this;
        }
        return null;
    }

    @Override
    public @NotNull SoulEnergyType getSource() {
        return this.stack;
    }

    @Override
    public SoulEnergyStackKey getEmpty() {
        return this;
    }

    @Override
    public SoulEnergyType getEmptyStack() {
        return new SoulEnergyType(0);
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
    public @NotNull SoulEnergyStackKey deserialize(FriendlyByteBuf buf) {
        return this;
    }

    @Override
    public @NotNull CompoundTag serializeNBT() {
        return new CompoundTag();
    }

    @Override
    public @NotNull SoulEnergyStackKey deserializeNBT(CompoundTag nbt) {
        return this;
    }

    @Override
    public @NotNull IStackRender getRender() {
        return com.solr98.beyondintegration.client.render.SoulEnergyStackKeyRender.INSTANCE;
    }
}
