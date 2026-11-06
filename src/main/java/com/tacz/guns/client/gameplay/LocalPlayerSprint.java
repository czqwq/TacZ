package com.tacz.guns.client.gameplay;

import net.minecraft.client.entity.EntityPlayerSP;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.entity.ReloadState;

/**
 * 客户端冲刺状态判定。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/gameplay/LocalPlayerSprint.java}。
 * 必须与 {@code entity/shooter/LivingEntitySprint#getProcessedSprintStatus} 严格一致，
 * 否则会出现「客户端看见在冲刺、服务端其实没冲刺」的错位。
 *
 * <p>
 * 平台替换（相对上游）：{@code LocalPlayer} → {@link EntityPlayerSP}。无降级点。
 */
public class LocalPlayerSprint {

    private final LocalPlayerDataHolder data;
    private final EntityPlayerSP player;

    public static boolean stopSprint = false;

    public LocalPlayerSprint(LocalPlayerDataHolder data, EntityPlayerSP player) {
        this.data = data;
        this.player = player;
    }

    /**
     * 根据情况返回玩家应当处于的冲刺状态，在玩家切换冲刺状态的时候调用。
     * 这里的逻辑应该严格与服务端对应，如果不对应，会出现客户端表现和服务端不符的情况。
     * （例如客户端的视觉效果是玩家在冲刺，而服务端玩家实际上没有冲刺）
     *
     * @see com.tacz.guns.entity.shooter.LivingEntitySprint#getProcessedSprintStatus
     */
    public boolean getProcessedSprintStatus(boolean sprinting) {
        // 这里的逻辑应该严格与服务端对应，如果不对应，会出现客户端表现和服务端不符的情况。
        // （例如客户端的视觉效果是玩家在冲刺，而服务端玩家实际上没有冲刺）
        IGunOperator gunOperator = IGunOperator.fromLivingEntity(player);
        ReloadState.StateType reloadStateType = gunOperator.getSynReloadState()
            .getStateType();
        if (gunOperator.getSynIsAiming() || (reloadStateType.isReloading() && !reloadStateType.isReloadFinishing())
            || stopSprint) {
            return false;
        } else {
            return sprinting;
        }
    }
}
