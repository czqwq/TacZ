package com.tacz.guns.entity.shooter;

import net.minecraft.entity.EntityLivingBase;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;

/**
 * 服务端冲刺状态裁决：瞄准中 / 换弹中不允许冲刺。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/entity/shooter/LivingEntitySprint.java}。
 * 构造器 {@code (EntityLivingBase, ShooterDataHolder)} 与 {@code getProcessedSprintStatus(boolean)} 被
 * {@code LivingEntityMixin} 直接调用，其判定必须与客户端 {@code LocalPlayerSprint} 严格一致（见 IGunOperator 注释）。
 *
 * <p>
 * 平台替换（相对上游）：{@code LivingEntity → EntityLivingBase}。
 */
public class LivingEntitySprint {

    private final EntityLivingBase shooter;
    private final ShooterDataHolder data;

    public LivingEntitySprint(EntityLivingBase shooter, ShooterDataHolder data) {
        this.shooter = shooter;
        this.data = data;
    }

    public boolean getProcessedSprintStatus(boolean sprint) {
        IGunOperator gunOperator = IGunOperator.fromLivingEntity(shooter);
        boolean isAiming = gunOperator.getSynIsAiming();
        ReloadState.StateType reloadStateType = gunOperator.getSynReloadState()
            .getStateType();
        if (isAiming || (reloadStateType.isReloading() && !reloadStateType.isReloadFinishing())) {
            return false;
        } else {
            return sprint;
        }
    }
}
