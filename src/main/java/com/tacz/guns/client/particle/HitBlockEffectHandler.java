package com.tacz.guns.client.particle;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.tacz.guns.init.ModBlocks;

/**
 * 子弹命中方块时的客户端表现总入口：表面碎屑粒子 + 命中音效 + 弹孔。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/particle/HitBlockEffectHandler.java}
 *
 * <h2>1.7.10 类型替换</h2>
 * <ul>
 * <li>{@code ClientLevel} → {@link World}（1.7.10 客户端世界是 {@code WorldClient}，是 {@code World} 的子类）；</li>
 * <li>{@code BlockPos} → 三个 {@code int}（上游 {@code blockPos} 只用于「取方块 / 取碰撞箱」）；</li>
 * <li>{@code Direction} → {@link ForgeDirection}（1.7.10 没有 {@code net.minecraft.core.Direction}）；</li>
 * <li>{@code Vec3} → 三个 {@code double}（上游这里只做「距离」与「AABB 相交」两件事，拆成标量没有语义损失）；</li>
 * <li>{@code BlockState} → {@link Block}（1.7.10 无 {@code BlockState}，见 {@code docs/PORT_PLAN.md} §3.1）；</li>
 * <li>{@code SoundEvent} → {@code String}（1.7.10 声音就是名字字符串，见 {@code mcshim.SoundEvent} 类注释）。</li>
 * </ul>
 */
public final class HitBlockEffectHandler {

    private static final int MAX_SAME_STEP_SOUND_PER_TICK = 1;
    private static final double MAX_HIT_STEP_SOUND_DISTANCE_SQR = 64.0D * 64.0D;
    /**
     * 上游按 {@code SoundEvent} 计数；1.7.10 无 {@code SoundEvent}（声音即名字字符串），故按名字计数。
     */
    private static final Map<String, SoundLimit> STEP_SOUND_LIMITS = new HashMap<>();

    private HitBlockEffectHandler() {}

    /**
     * 上游 {@code handle(ClientLevel, Vec3, Direction, BlockPos, String, String, String, boolean)} 的 1.7.10 形态。
     * 实参顺序与上游一致，只把复合类型拆成标量。
     *
     * @param level                客户端世界
     * @param hitX/hitY/hitZ       命中点（世界坐标，double）
     * @param direction            命中面朝向（上游 {@code Direction}，1.7.10 用 {@link ForgeDirection}）
     * @param blockX/blockY/blockZ 被命中方块坐标（上游 {@code BlockPos}）
     */
    public static void handle(World level, double hitX, double hitY, double hitZ, ForgeDirection direction, int blockX,
        int blockY, int blockZ, String ammoId, String gunId, String gunDisplayId, boolean incendiary) {
        if (level == null) {
            return;
        }
        Block block = level.getBlock(blockX, blockY, blockZ);
        // 上游 state.is(ModBlocks.TARGET.get())：1.7.10 没有 BlockState，等价判断就是「方块是不是同一个实例」
        if (block == ModBlocks.TARGET || shouldRemove(level, blockX, blockY, blockZ, hitX, hitY, hitZ)) {
            return;
        }
        HitSurfaceParticleSpawner
            .spawn(level, hitX, hitY, hitZ, direction, blockX, blockY, blockZ, ammoId, gunId, gunDisplayId, incendiary);
        playHitBlockStepSound(level, block, hitX, hitY, hitZ);
        if (BulletHoleParticle.isBulletHoleEnabled(ammoId, gunId, gunDisplayId)) {
            // 上游 level.addParticle(new BulletHoleOption(...), true, x, y, z, 0, 0, 0)：
            // 第二个参数 force=true 表示「跳过 particleSetting 与距离门限」。
            // 1.7.10 直接 EffectRenderer#addEffect 就是无门限路径（World#spawnParticle 才有门限）。
            Minecraft.getMinecraft().effectRenderer.addEffect(
                new BulletHoleParticle(
                    level,
                    hitX,
                    hitY,
                    hitZ,
                    direction,
                    blockX,
                    blockY,
                    blockZ,
                    ammoId,
                    gunId,
                    gunDisplayId,
                    incendiary));
        }
    }

    private static void playHitBlockStepSound(World level, Block block, double x, double y, double z) {
        if (!canHearHitBlockStepSound(x, y, z)) {
            return;
        }
        // 上游 state.getSoundType() → 1.7.10 Block#stepSound（public 字段，类型 Block.SoundType）
        Block.SoundType soundType = block.stepSound;
        if (soundType == null) {
            return;
        }
        // 上游 SoundType#getStepSound() → 1.7.10 SoundType#getStepResourcePath()
        String stepSound = soundType.getStepResourcePath();
        if (!canPlayStepSound(level, stepSound)) {
            return;
        }
        float volume = soundType.getVolume() * 0.6F;
        float pitch = soundType.getPitch() * (0.9F + level.rand.nextFloat() * 0.2F);
        // TODO(L2): 上游 HitBlockEffectHandler.java:51 用 ClientLevel#playLocalSound(x, y, z, stepSound,
        // SoundSource.BLOCKS, volume, pitch, false)（带 SoundSource 分类、显式本机播放、distanceDelay=false）；
        // 1.7.10 的 World#playSound(double,double,double,String,float,float,boolean) 没有 SoundSource，
        // 此处改用 WorldClient 的覆写（它本来就只走本机 SoundHandler，与 playLocalSound 等价）近似。
        level.playSound(x, y, z, stepSound, volume, pitch, false);
    }

    private static boolean canHearHitBlockStepSound(double x, double y, double z) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null) {
            return false;
        }
        return minecraft.thePlayer.getDistanceSq(x, y, z) <= MAX_HIT_STEP_SOUND_DISTANCE_SQR;
    }

    private static boolean canPlayStepSound(World level, String sound) {
        // 上游 ClientLevel#getGameTime() → 1.7.10 World#getTotalWorldTime()
        long gameTime = level.getTotalWorldTime();
        SoundLimit limit = STEP_SOUND_LIMITS.get(sound);
        if (limit == null || limit.gameTime != gameTime) {
            STEP_SOUND_LIMITS.put(sound, new SoundLimit(gameTime, 1));
            return true;
        }
        if (limit.count >= MAX_SAME_STEP_SOUND_PER_TICK) {
            return false;
        }
        limit.count++;
        return true;
    }

    /**
     * 上游 {@code shouldRemove(ClientLevel, BlockPos, Vec3)}：方块没了、或碰撞箱不含命中点时丢弃效果。
     *
     * <p>
     * 1.7.10 无 {@code VoxelShape}，等价物是
     * {@link Block#getCollisionBoundingBoxFromPool(World, int, int, int)}（返回的已是世界坐标 AABB，
     * 对应上游 {@code shape.bounds().move(blockPos)}）；返回 {@code null} 等价于上游 {@code shape.isEmpty()}。
     * 调用前先 {@code setBlockBoundsBasedOnState}，让栅栏 / 台阶这类按状态变化的方块给出正确包围盒。
     */
    private static boolean shouldRemove(World level, int blockX, int blockY, int blockZ, double hitX, double hitY,
        double hitZ) {
        Block block = level.getBlock(blockX, blockY, blockZ);
        if (block == null || block.isAir(level, blockX, blockY, blockZ)) {
            return true;
        }
        block.setBlockBoundsBasedOnState(level, blockX, blockY, blockZ);
        AxisAlignedBB blockBoundingBox = block.getCollisionBoundingBoxFromPool(level, blockX, blockY, blockZ);
        if (blockBoundingBox == null) {
            return true;
        }
        boolean intersects = blockBoundingBox.intersectsWith(
            AxisAlignedBB.getBoundingBox(hitX - 0.1, hitY - 0.1, hitZ - 0.1, hitX + 0.1, hitY + 0.1, hitZ + 0.1));
        return !intersects;
    }

    private static class SoundLimit {

        private final long gameTime;
        private int count;

        private SoundLimit(long gameTime, int count) {
            this.gameTime = gameTime;
            this.count = count;
        }
    }
}
