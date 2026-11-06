package com.tacz.guns.network.message;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;

import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 客户端 → 服务端：请求掏枪（draw）。
 * 上游：{@code .../network/message/ClientMessagePlayerDrawGun.java}（无字段）
 *
 * <p>
 * 1.7.10 差异：上游 {@code entity.getInventory().selected} → {@link InventoryPlayer#currentItem}；
 * {@code inventory.getItem(selected)} → {@link InventoryPlayer#getStackInSlot(int)}。
 */
public class ClientMessagePlayerDrawGun extends MessageBase {

    public ClientMessagePlayerDrawGun() {}

    public static void handle(ClientMessagePlayerDrawGun message, MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, () -> {
            EntityPlayerMP entity = ctx.getServerHandler().playerEntity;
            if (entity == null) {
                return;
            }
            InventoryPlayer inventory = entity.inventory;
            int selected = inventory.currentItem;
            IGunOperator.fromLivingEntity(entity)
                .draw(() -> inventory.getStackInSlot(selected));
        });
    }
}
