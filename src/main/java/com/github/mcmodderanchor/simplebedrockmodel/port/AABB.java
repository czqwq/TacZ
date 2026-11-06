// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: (new file - 1.7.10 port support, no upstream counterpart)
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.port;

import net.minecraft.util.AxisAlignedBB;

/**
 * 1.20.1 {@code net.minecraft.world.phys.AABB} 的等价实现（不可变、min/max 语义）。
 *
 * <p>
 * TODO(L2): 1.7.10 的 {@code net.minecraft.util.AxisAlignedBB} 是<b>可变</b>对象，
 * 方法名也不同（1.20 {@code inflate} → 1.7.10 {@code expand}、1.20 {@code move} →
 * 1.7.10 {@code getOffsetBoundingBox}、1.20 {@code contains} → 1.7.10 {@code isVecInside}），
 * 而 SBM 的 v2 模型烘焙/网格渲染
 * 大量用它做「渲染包围盒」（只读、可跨帧保存）。直接换成 {@code AxisAlignedBB} 会让
 * 保存下来的对象被别处意外改动，因此这里逐字复刻 1.20 的 {@code AABB}（不可变），
 * 需要与 1.7.10 原版交互时用 {@link #toAxisAlignedBB()} 转换。
 */
public class AABB {

    public final double minX;
    public final double minY;
    public final double minZ;
    public final double maxX;
    public final double maxY;
    public final double maxZ;

    public AABB(double x1, double y1, double z1, double x2, double y2, double z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    /** 对应 1.20 {@code AABB#move(double, double, double)}。 */
    public AABB move(double x, double y, double z) {
        return new AABB(this.minX + x, this.minY + y, this.minZ + z, this.maxX + x, this.maxY + y, this.maxZ + z);
    }

    /** 对应 1.20 {@code AABB#move(Vec3)}。 */
    public AABB move(Vec3 vec) {
        return this.move(vec.x, vec.y, vec.z);
    }

    /** 对应 1.20 {@code AABB#inflate(double)}。 */
    public AABB inflate(double amount) {
        return this.inflate(amount, amount, amount);
    }

    /** 对应 1.20 {@code AABB#inflate(double, double, double)}。 */
    public AABB inflate(double x, double y, double z) {
        double d0 = this.minX - x;
        double d1 = this.minY - y;
        double d2 = this.minZ - z;
        double d3 = this.maxX + x;
        double d4 = this.maxY + y;
        double d5 = this.maxZ + z;
        return new AABB(d0, d1, d2, d3, d4, d5);
    }

    /** 对应 1.20 {@code AABB#getCenter()}。 */
    public Vec3 getCenter() {
        return new Vec3((this.minX + this.maxX) / 2.0, (this.minY + this.maxY) / 2.0, (this.minZ + this.maxZ) / 2.0);
    }

    /** 对应 1.20 {@code AABB#getSize()}。 */
    public Vec3 getSize() {
        return new Vec3(this.maxX - this.minX, this.maxY - this.minY, this.maxZ - this.minZ);
    }

    /** 对应 1.20 {@code AABB#contains(double, double, double)}。 */
    public boolean contains(double x, double y, double z) {
        return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ;
    }

    /** 对应 1.20 {@code AABB#intersects(AABB)}。 */
    public boolean intersects(AABB other) {
        return this.maxX >= other.minX && this.minX <= other.maxX
            && this.maxY >= other.minY
            && this.minY <= other.maxY
            && this.maxZ >= other.minZ
            && this.minZ <= other.maxZ;
    }

    /** 对应 1.20 {@code AABB#distanceToSqr(Vec3)}。 */
    public double distanceToSqr(Vec3 point) {
        double dx = Math.max(Math.max(this.minX - point.x, 0.0), point.x - this.maxX);
        double dy = Math.max(Math.max(this.minY - point.y, 0.0), point.y - this.maxY);
        double dz = Math.max(Math.max(this.minZ - point.z, 0.0), point.z - this.maxZ);
        return dx * dx + dy * dy + dz * dz;
    }

    /** 1.7.10 互操作：转成原版 {@code AxisAlignedBB}（用于视锥剔除等原版 API）。 */
    public AxisAlignedBB toAxisAlignedBB() {
        return AxisAlignedBB.getBoundingBox(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
    }

    @Override
    public String toString() {
        return "AABB[" + this.minX
            + ", "
            + this.minY
            + ", "
            + this.minZ
            + "] -> ["
            + this.maxX
            + ", "
            + this.maxY
            + ", "
            + this.maxZ
            + "]";
    }
}
