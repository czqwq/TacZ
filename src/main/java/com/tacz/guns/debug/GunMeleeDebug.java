package com.tacz.guns.debug;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldServer;

/**
 * 搬运自上游 1.20.1 {@code debug/GunMeleeDebug.java}（近战范围调试粒子）。
 *
 * <p>
 * 1.7.10 适配：{@code ServerLevel#sendParticles(ParticleTypes.FLAME, ...)} →
 * {@link WorldServer#func_147487_a(String, double, double, double, int, double, double, double, double)}
 * 逐点广播（{@code World#spawnParticle} 在服务端是 no-op：{@code WorldServer} 的 {@code IWorldAccess}
 * 是 {@code WorldManager}，其 {@code spawnParticle} 为空实现，见
 * {@code build/rfg/minecraft-src/java/net/minecraft/world/WorldManager.java:29}）；
 * {@code Vec3} 换成 1.7.10 实现（{@code add/subtract/length/dotProduct}，没有 {@code add(x,y,z)} 需拆成
 * createVectorHelper）。该功能仅在 {@code DebugCommand.DEBUG} 模式调用，属调试辅助，语义保持一致。
 */
public class GunMeleeDebug {

    public static void showRange(EntityLivingBase user, int distance, Vec3 centrePos, Vec3 eyeVec, float rangeAngle) {
        if (user.worldObj.isRemote || !(user.worldObj instanceof WorldServer)) {
            return;
        }
        WorldServer serverLevel = (WorldServer) user.worldObj;
        int half = distance / 2;
        double eyeX = user.posX;
        double eyeY = user.posY + user.getEyeHeight();
        double eyeZ = user.posZ;
        Vec3 startPos = Vec3.createVectorHelper(eyeX - half, eyeY - half, eyeZ - half);
        for (int i = 0; i < distance * 2; i++) {
            for (int j = 0; j < distance * 2; j++) {
                for (int k = 0; k < distance * 2; k++) {
                    Vec3 tmpPos = Vec3.createVectorHelper(
                        startPos.xCoord + i / 2.0,
                        startPos.yCoord + j / 2.0,
                        startPos.zCoord + k / 2.0);
                    // 1.7.10 的 Vec3#subtract 是反向减法（a.subtract(b) = b - a），
                    // 上游 tmpPos - centrePos 这里按分量写，避免同 doMelee 的镜像错误
                    Vec3 targetVec = Vec3.createVectorHelper(
                        tmpPos.xCoord - centrePos.xCoord,
                        tmpPos.yCoord - centrePos.yCoord,
                        tmpPos.zCoord - centrePos.zCoord);
                    double targetLength = targetVec.lengthVector();
                    if (targetLength < distance) {
                        continue;
                    }
                    double degree = Math.toDegrees(Math.acos(targetVec.dotProduct(eyeVec) / (targetLength * distance)));
                    if (degree < (rangeAngle / 2)) {
                        // 上游：serverLevel.sendParticles(FLAME, pos, 1, 0,0,0, 0)
                        // 1.7.10：(particleName, x, y, z, count, xOffset, yOffset, zOffset, speed)
                        serverLevel.func_147487_a("flame", tmpPos.xCoord, tmpPos.yCoord, tmpPos.zCoord, 1, 0, 0, 0, 0);
                    }
                }
            }
        }
    }
}
