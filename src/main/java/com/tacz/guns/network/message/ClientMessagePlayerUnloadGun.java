package com.tacz.guns.network.message;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.NetworkTaskQueue;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 客户端 → 服务端：卸载枪内子弹（把子弹退回背包）。
 * 上游：{@code .../network/message/ClientMessagePlayerUnloadGun.java}
 *
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>{@code ServerPlayer#isSpectator()}：1.7.10 <b>没有旁观者模式</b>，该判断删除（注释保留）</li>
 * <li>{@code player.getMainHandItem()} → {@link EntityPlayerMP#getHeldItem()}</li>
 * <li>{@code player.displayClientMessage(component, true)}（1.20 的 action bar）→
 * {@link EntityPlayerMP#addChatMessage}（1.7.10 无 action bar，降级为聊天栏，记录差异）</li>
 * <li>{@code player.inventoryMenu.broadcastChanges()} → {@code player.inventoryContainer.detectAndSendChanges()}</li>
 * <li>上游 {@code var stack}/{@code var index} 的局部类型推断改为显式类型（jvmDowngrader 支持 var，
 * 这里保留上游写法亦可，但显式类型更利于排查跨包签名）</li>
 * </ul>
 */
public class ClientMessagePlayerUnloadGun extends MessageBase {

    private int slot;
    private ResourceLocation gunId;

    public ClientMessagePlayerUnloadGun(int slot, ResourceLocation gunId) {
        this.slot = slot;
        this.gunId = gunId;
    }

    public ClientMessagePlayerUnloadGun() {}

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeVarInt(slot);
        buf.writeResourceLocation(gunId);
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.slot = buf.readVarInt();
        this.gunId = buf.readResourceLocation();
    }

    public static void handle(ClientMessagePlayerUnloadGun message, MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, () -> {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) {
                return;
            }
            // 1.7.10 无旁观者模式，因此只有存活 + 槽位校验
            if (!player.isEntityAlive() || message.slot < 0
                || message.slot >= 9
                || player.inventory.currentItem != message.slot) {
                showCannotUnload(player);
                return;
            }
            ItemStack stack = player.getHeldItem();
            IGun gun = IGun.getIGunOrNull(stack);
            if (gun == null || !message.gunId.equals(gun.getGunId(stack))) {
                showCannotUnload(player);
                return;
            }
            // 上游 CommonGunIndex（Optional）→ 这里用 orElse(null)
            Object indexObject = TimelessAPI.getCommonGunIndex(message.gunId)
                .orElse(null);
            if (indexObject == null) {
                showCannotUnload(player);
                return;
            }
            CommonGunIndex index = (CommonGunIndex) indexObject;
            if (!index.canUnloadAmmo() || gun.useInventoryAmmo(stack)) {
                player.addChatMessage(new ChatComponentTranslation("message.tacz.unload.unsupported"));
                return;
            }
            Bolt bolt = index.getGunData()
                .getBolt();
            long ammoBefore = getLoadedAmmoCount(gun, stack, bolt);
            if (ammoBefore == 0) {
                player.addChatMessage(new ChatComponentTranslation("message.tacz.unload.no_ammo"));
                return;
            }
            ResourceLocation gunDisplayId = gun.getGunDisplayId(stack);
            IGunOperator.fromLivingEntity(player)
                .unloadAmmo();
            player.inventoryContainer.detectAndSendChanges();
            if (getLoadedAmmoCount(gun, stack, bolt) < ammoBefore) {
                NetworkHandler.sendToClientPlayer(new ServerMessageGunUnload(message.gunId, gunDisplayId), player);
            } else {
                showCannotUnload(player);
            }
        });
    }

    private static long getLoadedAmmoCount(IGun gun, ItemStack stack, Bolt bolt) {
        return Math.max(0L, gun.getCurrentAmmoCount(stack))
            + (bolt != Bolt.OPEN_BOLT && gun.hasBulletInBarrel(stack) ? 1L : 0L);
    }

    private static void showCannotUnload(EntityPlayerMP player) {
        player.addChatMessage(new ChatComponentTranslation("message.tacz.unload.cannot"));
    }
}
