package com.tacz.guns.particles;

import net.minecraftforge.common.util.ForgeDirection;

/**
 * 弹孔的粒子参数（纯数据类）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/particles/BulletHoleOption.java}
 * 
 * <pre>
 * {@code public class BulletHoleOption implements ParticleOptions}
 * </pre>
 * 
 * 上游实现 {@code ParticleOptions}（{@code CODEC} / {@code DESERIALIZER} / {@code getType()} /
 * {@code writeToNetwork()} / {@code writeToString()}），由 {@code ModParticles} 注册的
 * {@code ParticleType<BulletHoleOption>} 承载，粒子由 {@code client/particle/BulletHoleParticle} 渲染。
 *
 * <p>
 * <b>1.7.10 差异（本类只保留数据）</b>：
 * <ul>
 * <li>1.7.10 没有 {@code ParticleOptions} / {@code ParticleType} / {@code Codec} / brigadier 命令语法
 * （{@code /particle} 是 1.8+ 才有的），也没有「粒子参数随包序列化」的管线。
 * 上游 {@code CODEC} / {@code DESERIALIZER} 一并删除。</li>
 * <li>1.20 的 {@code Direction} → {@link ForgeDirection}（1.7.10 的方向枚举/工具类）。</li>
 * <li>1.20 的 {@code BlockPos}（{@code BlockPos.of(long)} / {@code asLong()}）→
 * 三个 {@code int} 坐标 {@link #getX()} / {@link #getY()} / {@link #getZ()}。
 * 上游把坐标压进 {@code long} 只用于 codec/命令串；1.7.10 的粒子创建
 * （{@code World#spawnParticle}）本来就是 x/y/z 三个 double，因此拆开更直接，语义不变。</li>
 * <li>{@code getType()} / {@code writeToNetwork()} 属 L2 客户端渲染层（{@code client/particle/**}）
 * 与网络层，本包（common）不提供；L2 需要时按字段自行组包。</li>
 * </ul>
 */
public class BulletHoleOption {

    private final ForgeDirection direction;
    private final int x;
    private final int y;
    private final int z;
    private final String ammoId;
    private final String gunId;
    private final String gunDisplayId;
    private final boolean incendiary;

    public BulletHoleOption(ForgeDirection direction, int x, int y, int z, String ammoId, String gunId,
        String gunDisplayId) {
        this(direction, x, y, z, ammoId, gunId, gunDisplayId, false);
    }

    public BulletHoleOption(ForgeDirection direction, int x, int y, int z, String ammoId, String gunId,
        String gunDisplayId, boolean incendiary) {
        this.direction = direction == null ? ForgeDirection.UNKNOWN : direction;
        this.x = x;
        this.y = y;
        this.z = z;
        this.ammoId = ammoId;
        this.gunId = gunId;
        this.gunDisplayId = gunDisplayId;
        this.incendiary = incendiary;
    }

    /**
     * 1.7.10 替代上游 {@code BulletHoleOption(int dir, long pos, ...)}：
     * {@code dir} 是方向序号（上游 {@code Direction#ordinal()}），
     * 1.7.10 用 {@link ForgeDirection#getOrientation(int)} 还原。
     */
    public BulletHoleOption(int dir, int x, int y, int z, String ammoId, String gunId, String gunDisplayId,
        boolean incendiary) {
        this(ForgeDirection.getOrientation(dir), x, y, z, ammoId, gunId, gunDisplayId, incendiary);
    }

    public ForgeDirection getDirection() {
        return this.direction;
    }

    /** 上游 {@code Direction#ordinal()}，供 L2 网络包/存档编码使用。 */
    public int getDirectionOrdinal() {
        return this.direction.ordinal();
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getZ() {
        return this.z;
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
}
