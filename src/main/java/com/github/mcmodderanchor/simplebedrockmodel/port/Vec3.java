// Vendored from simplebedrockmodel 2.6.1-forge-mc1.20.1 (com.github.mcmodderanchor:simplebedrockmodel).
// upstream: (new file - 1.7.10 port support, no upstream counterpart)
// 1.7.10 port: see class javadoc + TODO(L2) markers for degraded behaviour.

package com.github.mcmodderanchor.simplebedrockmodel.port;

/**
 * 1.20.1 {@code com.github.mcmodderanchor.simplebedrockmodel.port.Vec3} 的等价实现，供移植后的 SBM 代码使用。
 *
 * <p>
 * TODO(L2): 1.7.10 的 {@code net.minecraft.util.Vec3} 是<b>可变</b>对象，字段名是
 * {@code xCoord/yCoord/zCoord}，且没有 {@code ZERO} 常量、没有 {@code x()/y()/z()} 访问器。
 * 上游 SBM 大量以 {@code pos.x} / {@code camPos.x()} 两种方式读同一对象，直接替换成 1.7.10 的 Vec3
 * 会改动上百处调用点，因此这里逐字复刻 1.20 的 {@code Vec3}（不可变、{@code public final double x,y,z}、
 * 同名访问器与运算方法），把差异收敛在本类。运算语义（含 {@code normalize()} 的零向量保护）与
 * 1.20 源码一致。
 */
public class Vec3 {

    public static final Vec3 ZERO = new Vec3(0.0, 0.0, 0.0);

    public final double x;
    public final double y;
    public final double z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vec3(Vec3 other) {
        this(other.x, other.y, other.z);
    }

    /** 对应 1.20 {@code Vec3#scale(double)}。 */
    public Vec3 scale(double factor) {
        return new Vec3(this.x * factor, this.y * factor, this.z * factor);
    }

    /** 对应 1.20 {@code Vec3#reverse()}。 */
    public Vec3 reverse() {
        return this.scale(-1.0);
    }

    /** 对应 1.20 {@code Vec3#add(Vec3)}。 */
    public Vec3 add(Vec3 other) {
        return this.add(other.x, other.y, other.z);
    }

    /** 对应 1.20 {@code Vec3#add(double, double, double)}。 */
    public Vec3 add(double x, double y, double z) {
        return new Vec3(this.x + x, this.y + y, this.z + z);
    }

    /** 对应 1.20 {@code Vec3#subtract(Vec3)}。 */
    public Vec3 subtract(Vec3 other) {
        return this.subtract(other.x, other.y, other.z);
    }

    /** 对应 1.20 {@code Vec3#subtract(double, double, double)}。 */
    public Vec3 subtract(double x, double y, double z) {
        return new Vec3(this.x - x, this.y - y, this.z - z);
    }

    /** 对应 1.20 {@code Vec3#normalize()}（长度过小时返回 {@link #ZERO}，与 1.20 一致）。 */
    public Vec3 normalize() {
        double d = Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
        return d < 1.0E-4 ? ZERO : new Vec3(this.x / d, this.y / d, this.z / d);
    }

    /** 对应 1.20 {@code Vec3#dot(Vec3)}。 */
    public double dot(Vec3 other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    /** 对应 1.20 {@code Vec3#cross(Vec3)}。 */
    public Vec3 cross(Vec3 other) {
        return new Vec3(
            this.y * other.z - this.z * other.y,
            this.z * other.x - this.x * other.z,
            this.x * other.y - this.y * other.x);
    }

    /** 对应 1.20 {@code Vec3#length()}。 */
    public double length() {
        return Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
    }

    /** 对应 1.20 {@code Vec3#lengthSqr()}。 */
    public double lengthSqr() {
        return this.x * this.x + this.y * this.y + this.z * this.z;
    }

    /** 对应 1.20 {@code Vec3#horizontalDistanceSqr()}。 */
    public double horizontalDistanceSqr() {
        return this.x * this.x + this.z * this.z;
    }

    /** 对应 1.20 {@code Vec3#distanceTo(Vec3)}。 */
    public double distanceTo(Vec3 other) {
        return Math.sqrt(this.distanceToSqr(other));
    }

    /** 对应 1.20 {@code Vec3#distanceToSqr(Vec3)}。 */
    public double distanceToSqr(Vec3 other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** 对应 1.20 {@code Vec3#x()}。 */
    public double x() {
        return this.x;
    }

    /** 对应 1.20 {@code Vec3#y()}。 */
    public double y() {
        return this.y;
    }

    /** 对应 1.20 {@code Vec3#z()}。 */
    public double z() {
        return this.z;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Vec3)) {
            return false;
        }
        Vec3 v = (Vec3) other;
        return Double.compare(this.x, v.x) == 0 && Double.compare(this.y, v.y) == 0 && Double.compare(this.z, v.z) == 0;
    }

    @Override
    public int hashCode() {
        long l = Double.doubleToLongBits(this.x);
        int i = (int) (l ^ (l >>> 32));
        l = Double.doubleToLongBits(this.y);
        i = 31 * i + (int) (l ^ (l >>> 32));
        l = Double.doubleToLongBits(this.z);
        return 31 * i + (int) (l ^ (l >>> 32));
    }

    @Override
    public String toString() {
        return "[" + this.x + ", " + this.y + ", " + this.z + "]";
    }
}
