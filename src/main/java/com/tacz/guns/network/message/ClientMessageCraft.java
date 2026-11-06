package com.tacz.guns.network.message;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 客户端 → 服务端：枪械工作台合成请求。
 * 上游：{@code .../network/message/ClientMessageCraft.java}
 *
 * <p>
 * 1.7.10 差异：{@code ServerPlayer} → {@link EntityPlayerMP}；{@code entity.containerMenu} →
 * {@link EntityPlayerMP#openContainer}（{@code Container#windowId} 对应上游 {@code containerId}）。
 * <p>
 * 依赖契约（common-game）：{@code inventory/GunSmithTableMenu} 保留类名、
 * 继承 {@code net.minecraft.inventory.Container}，并提供
 * {@code void doCraft(ResourceLocation recipeId, EntityPlayer player)}。
 */
public class ClientMessageCraft extends MessageBase {

    private ResourceLocation recipeId;
    private int menuId;

    public ClientMessageCraft(ResourceLocation recipeId, int menuId) {
        this.recipeId = recipeId;
        this.menuId = menuId;
    }

    public ClientMessageCraft() {}

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeResourceLocation(recipeId);
        buf.writeVarInt(menuId);
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.recipeId = buf.readResourceLocation();
        this.menuId = buf.readVarInt();
    }

    public static void handle(ClientMessageCraft message, MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, () -> {
            EntityPlayerMP entity = ctx.getServerHandler().playerEntity;
            if (entity == null) {
                return;
            }
            if (entity.openContainer != null && entity.openContainer.windowId == message.menuId
                && entity.openContainer instanceof GunSmithTableMenu) {
                ((GunSmithTableMenu) entity.openContainer).doCraft(message.recipeId, entity);
            }
        });
    }
}
