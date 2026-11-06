package com.tacz.guns.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

import com.tacz.guns.config.common.AmmoConfig;
import com.tacz.guns.util.block.ProjectileExplosion;

/**
 * 弹药爆炸的统一入口：构造 {@link ProjectileExplosion}、触发 Forge 爆炸事件、执行爆炸并同步客户端。
 *
 * <p>
 * <b>移植说明</b>：
 * <ul>
 * <li>{@code net.minecraft.world.level.Explosion}（1.20 的数据驱动爆炸）在 1.7.10 没有对应物，
 * 本文件因此改用 1.7.10 原生的「{@code Explosion#doExplosionA()} + {@code doExplosionB(boolean)}」
 * 两段式，并保留上游的自定义 {@link ProjectileExplosion}（射线采样命中箱）。</li>
 * <li>{@code !(exploder.level() instanceof ServerLevel)} → {@code world.isRemote}（1.7.10 只有
 * {@code World}/{@code WorldServer}，没有 {@code ServerLevel} 类型）。</li>
 * <li>{@code Explosion.BlockInteraction.KEEP/DESTROY} → 布尔量 {@code destroy}（映射到
 * {@code ProjectileExplosion.isSmoking}，见该类注释）。</li>
 * <li><b>{@code ClientboundExplodePacket} 不存在</b>：1.7.10 的等价物是
 * {@link S27PacketExplosion}（原版 {@code WorldServer#newExplosion} 也是发它），
 * 携带爆心、强度、被破坏方块列表与玩家速度增量。因此
 * {@code explosion.getToBlow()} → {@code explosion.affectedBlockPositions}（{@code ChunkPosition} 列表），
 * {@code explosion.getHitPlayers().get(player)} → {@link ProjectileExplosion#getHitPlayers()}。</li>
 * <li>{@code level.players()} → {@code world.playerEntities}；
 * {@code player.distanceToSqr(Vec3)} → {@code player.getDistanceSq(x, y, z)}；
 * {@code player.connection.send(packet)} →
 * {@code ((EntityPlayerMP) player).playerNetServerHandler.sendPacket(packet)}。</li>
 * <li>{@code explosion.finalizeExplosion(true)} → {@code doExplosionB(false)}
 * （原版 1.7.10 服务端也传 {@code false}：粒子由 S27 包在客户端生成），
 * 随后在 {@code KEEP} 语义下清空方块列表（等价上游 {@code clearToBlow()}，
 * 也与原版 {@code WorldServer#newExplosion} 的 {@code if (!isSmoking) affectedBlockPositions.clear()} 一致）。</li>
 * <li>上游 {@code Explosion} 的 Forge 事件（{@code ForgeEventFactory.onExplosionStart}）在 1.7.10
 * <b>存在</b>（{@code net.minecraftforge.event.world.ExplosionEvent}），因此保留拦截逻辑。</li>
 * <li>{@code float damage} 仍是「伤害倍率」而非爆炸半径：半径来自 {@code radius}，与上游一致。</li>
 * </ul>
 */
public class ExplodeUtil {

    public static void createExplosion(Entity owner, Entity exploder, float damage, float radius, boolean knockback,
        boolean destroy, Vec3 hitPos) {
        // 半径无效时跳过爆炸，避免伤害和击退计算出现 NaN
        if (!Float.isFinite(radius) || radius <= 0) {
            return;
        }
        if (exploder == null || hitPos == null) {
            return;
        }
        World world = exploder.worldObj;
        // 客户端不执行（1.7.10 对应 1.20 的 `!(exploder.level() instanceof ServerLevel)`）
        if (world == null || world.isRemote) {
            return;
        }
        // 创建爆炸。上游的 Explosion.BlockInteraction.KEEP/DESTROY 在这里退化为 destroy 布尔量。
        ProjectileExplosion explosion = new ProjectileExplosion(
            world,
            owner,
            exploder,
            hitPos.xCoord,
            hitPos.yCoord,
            hitPos.zCoord,
            damage,
            radius,
            knockback,
            destroy);
        // 监听 forge 事件
        if (ForgeEventFactory.onExplosionStart(world, explosion)) {
            return;
        }
        // 执行爆炸逻辑（方块集合 + 实体伤害）
        explosion.doExplosionA();
        // 方块破坏与音效（粒子由 S27 包在客户端生成）
        explosion.doExplosionB(false);
        if (!destroy) {
            // 1.20: explosion.clearToBlow()
            explosion.affectedBlockPositions.clear();
        }
        // 客户端发包，发送爆炸相关信息
        int visibleDistance = AmmoConfig.EXPLOSIVE_AMMO_VISIBLE_DISTANCE.get();
        double visibleDistanceSq = (double) visibleDistance * (double) visibleDistance;
        for (Object obj : world.playerEntities) {
            if (!(obj instanceof EntityPlayer)) {
                continue;
            }
            EntityPlayer player = (EntityPlayer) obj;
            if (player.getDistanceSq(hitPos.xCoord, hitPos.yCoord, hitPos.zCoord) >= visibleDistanceSq) {
                continue;
            }
            if (!(player instanceof EntityPlayerMP)) {
                continue;
            }
            S27PacketExplosion packet = new S27PacketExplosion(
                hitPos.xCoord,
                hitPos.yCoord,
                hitPos.zCoord,
                radius,
                explosion.affectedBlockPositions,
                explosion.getHitPlayers()
                    .get(player));
            ((EntityPlayerMP) player).playerNetServerHandler.sendPacket(packet);
        }
    }
}
