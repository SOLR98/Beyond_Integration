package com.solr98.beyondintegration.feature.totem;

import com.solr98.beyondintegration.CommandConfig;
import com.solr98.beyondintegration.api.MaidNetworkAPI;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.scores.Team;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * 网络图腾爆发：图腾触发复活后，对触发者周围实体造成范围伤害。
 * <p>
 * 豁免对象：同队玩家（原版计分板）、触发者本人及其队友的宠物、所有女仆。
 * 伤害为自定义伤害类型 {@code beyond_integration:totem_burst}，数值 = 触发者最大生命 × 配置百分比。
 * 由 {@code totem_burst} 分区配置控制，默认关闭。
 */
public final class TotemBurst {

    private static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("beyond_integration", "totem_burst"));

    private TotemBurst() {}

    /** 图腾触发后调用（玩家或女仆）。 */
    public static void trigger(LivingEntity source) {
        if (!CommandConfig.totemBurstEnabled()) return;
        if (source == null || source.level().isClientSide()) return;
        if (!(source.level() instanceof ServerLevel level)) return;

        double radius = CommandConfig.totemBurstRadius();
        float damage = (float) (source.getMaxHealth() * CommandConfig.totemBurstDamagePercent() / 100.0D);
        if (damage <= 0.0F || radius <= 0.0D) return;

        DamageSource damageSource = buildDamageSource(level, source);
        boolean ignoreInvulnerability = CommandConfig.totemBurstIgnoreInvulnerability();
        ServerPlayer ref = referencePlayer(source);

        AABB area = source.getBoundingBox().inflate(radius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (target == source || !target.isAlive()) continue;
            if (isExempt(source, ref, target)) continue;
            if (ignoreInvulnerability) target.invulnerableTime = 0;
            target.hurt(damageSource, damage);
        }

        startRing(level, source.getX(), source.getY() + source.getBbHeight() * 0.5D, source.getZ(), radius);
    }

    /** 冲击波球总时长（tick）：半径在此时长内从 0 匀速扩到目标半径。 */
    private static final int RING_DURATION_TICKS = 2;

    /** 活动中、等待逐 tick 扩散的冲击波环。仅服务端主线程访问。 */
    private static final List<Ring> RINGS = new ArrayList<>();

    /** 记录一次由触发点向外扩散的冲击波环。 */
    private static void startRing(ServerLevel level, double x, double y, double z, double maxRadius) {
        if (maxRadius <= 0.0D) return;
        RINGS.add(new Ring(level, x, y, z, maxRadius, 0));
    }

    /**
     * 服务端每 tick 推进所有冲击波球：水平圆周铺灵魂火、球面铺灵魂，
     * 并在球体内均匀铺 sculk_soul；半径从 0 匀速增长到目标半径后移除。
     * 由各分支的 tick 事件处理器调用。
     */
    public static void serverTick() {
        if (RINGS.isEmpty()) return;
        Iterator<Ring> it = RINGS.iterator();
        while (it.hasNext()) {
            Ring ring = it.next();
            if (ring.age >= RING_DURATION_TICKS) {
                it.remove();
                continue;
            }
            double progress = (double) (ring.age + 1) / RING_DURATION_TICKS;
            double r = ring.maxRadius * progress;
            ServerLevel level = ring.level;
            RandomSource random = level.getRandom();

            // 平面圆：灵魂火铺在水平面的半径 r 圆周上
            int flat = Math.min(160, Math.max(12, (int) Math.round(r * 6.0D)));
            for (int i = 0; i < flat; i++) {
                double angle = (Math.PI * 2.0D) * i / flat;
                double px = ring.x + Math.cos(angle) * r;
                double pz = ring.z + Math.sin(angle) * r;
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, ring.y, pz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }

            // 立体球壳：灵魂均匀铺在半径 r 的球面上（斐波那契球，避免聚集）
            int shell = Math.min(220, Math.max(16, (int) Math.round(r * r * 2.5D)));
            double goldenAngle = Math.PI * (3.0D - Math.sqrt(5.0D));
            for (int i = 0; i < shell; i++) {
                double y = 1.0D - 2.0D * (i + 0.5D) / shell;
                double rim = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
                double theta = goldenAngle * i;
                double px = ring.x + Math.cos(theta) * rim * r;
                double pz = ring.z + Math.sin(theta) * rim * r;
                level.sendParticles(ParticleTypes.SOUL, px, ring.y + y * r, pz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }

            // 内部球体：sculk_soul 在半径 r 的球体内均匀分布
            int fill = Math.min(200, Math.max(8, (int) Math.round(r * r * 2.0D)));
            for (int i = 0; i < fill; i++) {
                double rad = r * Math.cbrt(random.nextDouble());
                double cosT = 2.0D * random.nextDouble() - 1.0D;
                double sinT = Math.sqrt(Math.max(0.0D, 1.0D - cosT * cosT));
                double phi = random.nextDouble() * Math.PI * 2.0D;
                double px = ring.x + rad * sinT * Math.cos(phi);
                double py = ring.y + rad * cosT;
                double pz = ring.z + rad * sinT * Math.sin(phi);
                level.sendParticles(ParticleTypes.SCULK_SOUL, px, py, pz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            ring.age++;
        }
    }

    /** 单次冲击波环动画状态。 */
    private static final class Ring {
        final ServerLevel level;
        final double x;
        final double y;
        final double z;
        final double maxRadius;
        int age;

        Ring(ServerLevel level, double x, double y, double z, double maxRadius, int age) {
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
            this.maxRadius = maxRadius;
            this.age = age;
        }
    }

    /** 触发者对应的“玩家”参照体：玩家本身；女仆等可拥有者实体则取其主人（在线时）。 */
    private static ServerPlayer referencePlayer(LivingEntity source) {
        if (source instanceof ServerPlayer sp) return sp;
        if (source instanceof OwnableEntity ownable) {
            UUID owner = ownable.getOwnerUUID();
            MinecraftServer server = source.getServer();
            if (owner != null && server != null) {
                return server.getPlayerList().getPlayer(owner);
            }
        }
        return null;
    }

    /** 是否豁免：女仆 / 参照玩家的队友 / 参照玩家及其队友的宠物。 */
    private static boolean isExempt(LivingEntity source, ServerPlayer ref, LivingEntity target) {
        if (MaidNetworkAPI.isMaid(target)) return true;
        if (target instanceof OwnableEntity ownable) {
            UUID owner = ownable.getOwnerUUID();
            if (owner != null) {
                if (owner.equals(source.getUUID())) return true;
                if (ref != null && (owner.equals(ref.getUUID()) || isTeammate(ref, owner))) return true;
            }
        }
        if (ref != null && target instanceof ServerPlayer tp) {
            Team team = ref.getTeam();
            return team != null && team == tp.getTeam();
        }
        return false;
    }

    /** 该 UUID 是否为参照玩家所在原版计分板队伍的在线成员。 */
    private static boolean isTeammate(ServerPlayer ref, UUID uuid) {
        if (uuid == null) return false;
        if (uuid.equals(ref.getUUID())) return true;
        Team team = ref.getTeam();
        MinecraftServer server = ref.getServer();
        if (team == null || server == null) return false;
        ServerPlayer other = server.getPlayerList().getPlayer(uuid);
        return other != null && other.getTeam() == team;
    }

    private static DamageSource buildDamageSource(ServerLevel level, LivingEntity source) {
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DAMAGE_TYPE);
        return new DamageSource(holder, source, source);
    }
}
