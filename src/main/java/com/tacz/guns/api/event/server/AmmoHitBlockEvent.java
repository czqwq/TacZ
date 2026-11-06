package com.tacz.guns.api.event.server;

import net.minecraft.block.Block;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;

import com.tacz.guns.api.event.common.KubeJSGunEventPoster;
import com.tacz.guns.entity.EntityKineticBullet;

import cpw.mods.fml.common.eventhandler.Event;

/**
 * 子弹击中方块时触发的事件，目前仅在服务端触发。
 * <p>
 * 1.7.10 移植说明（保持上游形状，仅替换平台类型）：
 * <ul>
 * <li>上游 {@code Level} → {@link World}；</li>
 * <li>上游 {@code BlockHitResult} → {@link MovingObjectPosition}（同样携带精确命中点 {@code hitVec}、
 * 命中面 {@code sideHit} 与方块坐标 {@code blockX/Y/Z}）；</li>
 * <li>上游 {@code BlockState} → {@link #getState()}（{@link Block}）；{@link #getMetadata()} 是平台附加 getter，
 * 对应 {@code BlockState} 中的 metadata 部分（构造时快照）。</li>
 * </ul>
 */
public class AmmoHitBlockEvent extends Event implements KubeJSGunEventPoster<AmmoHitBlockEvent> {

    private final World level;
    private final MovingObjectPosition hitResult;
    private final Block state;
    private final int metadata;
    private final EntityKineticBullet ammo;

    public AmmoHitBlockEvent(World level, MovingObjectPosition hitResult, Block state, EntityKineticBullet ammo) {
        this.level = level;
        this.hitResult = hitResult;
        this.state = state;
        this.ammo = ammo;
        // BlockState → Block + metadata：在事件构造时快照，等价于上游拿到命中瞬间的方块状态。
        this.metadata = level == null || hitResult == null ? 0
            : level.getBlockMetadata(hitResult.blockX, hitResult.blockY, hitResult.blockZ);
        postServerEventToKubeJS(this);
    }

    @Override
    public boolean isCancelable() {
        return true;
    }

    public World getLevel() {
        return level;
    }

    /**
     * 上游 {@code BlockHitResult} 的 1.7.10 等价物。
     */
    public MovingObjectPosition getHitResult() {
        return hitResult;
    }

    public Block getState() {
        return state;
    }

    /**
     * 平台附加 getter：{@code BlockState} 的 metadata 部分在 1.7.10 由 {@link Block} + metadata 表达。
     */
    public int getMetadata() {
        return metadata;
    }

    public EntityKineticBullet getAmmo() {
        return ammo;
    }
}
