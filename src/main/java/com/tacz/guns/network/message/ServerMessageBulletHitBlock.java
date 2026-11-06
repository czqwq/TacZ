package com.tacz.guns.network.message;

import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.Nullable;

import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.ClientSideHooks;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 服务端 → 客户端：子弹命中方块的效果（弹孔粒子、命中音效）。
 * 上游：{@code .../network/message/ServerMessageBulletHitBlock.java}
 *
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>{@code net.minecraft.world.phys.Vec3} → {@link Vec3}（{@code xCoord/yCoord/zCoord}；
 * 提供同签名 {@code (double,double,double,...)} 构造器，另加一个 {@link Vec3} 重载）</li>
 * <li>{@code net.minecraft.core.BlockPos} → 三个 int；{@code buf.writeBlockPos(pos)} →
 * {@code writeBlockPos(x,y,z)}，读侧 {@code readBlockPos()} 返回 {@code int[3]}</li>
 * <li>{@code net.minecraft.core.Direction} → {@link ForgeDirection}（1.7.10 的方块朝向枚举，
 * 与上游 {@code Direction} 的 6 个面一一对应）</li>
 * <li>客户端效果 {@code HitBlockEffectHandler.handle(level, ...)} 属 client 包 →
 * 改走 {@link com.tacz.guns.network.ClientSideHooks.Impl#bulletHitBlock}</li>
 * </ul>
 */
public class ServerMessageBulletHitBlock extends MessageBase {

    private double hitX;
    private double hitY;
    private double hitZ;
    private ForgeDirection direction;
    private int blockX;
    private int blockY;
    private int blockZ;
    private String ammoId;
    private String gunId;
    private String gunDisplayId;
    private boolean incendiary;

    public ServerMessageBulletHitBlock(Vec3 hitPos, ForgeDirection direction, int blockX, int blockY, int blockZ,
        String ammoId, String gunId, String gunDisplayId, boolean incendiary) {
        this(
            hitPos.xCoord,
            hitPos.yCoord,
            hitPos.zCoord,
            direction,
            blockX,
            blockY,
            blockZ,
            ammoId,
            gunId,
            gunDisplayId,
            incendiary);
    }

    public ServerMessageBulletHitBlock(double hitX, double hitY, double hitZ, ForgeDirection direction, int blockX,
        int blockY, int blockZ, String ammoId, String gunId, String gunDisplayId, boolean incendiary) {
        this.hitX = hitX;
        this.hitY = hitY;
        this.hitZ = hitZ;
        this.direction = direction;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
        this.ammoId = ammoId;
        this.gunId = gunId;
        this.gunDisplayId = gunDisplayId;
        this.incendiary = incendiary;
    }

    public ServerMessageBulletHitBlock() {}

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeDouble(hitX);
        buf.writeDouble(hitY);
        buf.writeDouble(hitZ);
        buf.writeEnum(direction);
        buf.writeBlockPos(blockX, blockY, blockZ);
        buf.writeUtf(ammoId == null ? "" : ammoId);
        buf.writeUtf(gunId == null ? "" : gunId);
        buf.writeUtf(gunDisplayId == null ? "" : gunDisplayId);
        buf.writeBoolean(incendiary);
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.hitX = buf.readDouble();
        this.hitY = buf.readDouble();
        this.hitZ = buf.readDouble();
        this.direction = buf.readEnum(ForgeDirection.class);
        int[] pos = buf.readBlockPos();
        this.blockX = pos[0];
        this.blockY = pos[1];
        this.blockZ = pos[2];
        this.ammoId = buf.readUtf();
        this.gunId = buf.readUtf();
        this.gunDisplayId = buf.readUtf();
        this.incendiary = buf.readBoolean();
    }

    public double getHitX() {
        return hitX;
    }

    public double getHitY() {
        return hitY;
    }

    public double getHitZ() {
        return hitZ;
    }

    @Nullable
    public ForgeDirection getDirection() {
        return direction;
    }

    public int getBlockX() {
        return blockX;
    }

    public int getBlockY() {
        return blockY;
    }

    public int getBlockZ() {
        return blockZ;
    }

    public String getAmmoId() {
        return ammoId;
    }

    public String getGunId() {
        return gunId;
    }

    public String getGunDisplayId() {
        return gunDisplayId;
    }

    public boolean isIncendiary() {
        return incendiary;
    }

    public static void handle(ServerMessageBulletHitBlock message, MessageContext ctx) {
        if (ctx.side != Side.CLIENT) {
            return;
        }
        // readEnum 越界抛 IAE（1.20 语义）；此处 null 检查为双保险（HitBlockEffectHandler 用方向定贴面）。
        if (message.direction == null) {
            return;
        }
        NetworkTaskQueue.enqueue(
            ctx.side,
            () -> ClientSideHooks.get()
                .bulletHitBlock(message));
    }
}
