package com.tacz.guns.network.message.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.ClientSideHooks;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 上游 {@code .../network/message/event/} 下 7 个「射手 + 枪物品」事件包（GunFire / GunFirePost /
 * GunFireSelect / GunFinishReload / GunMelee / GunReload / GunShoot）的公共基类。
 *
 * <p>
 * 上游每个类都重复了同一段：
 * 
 * <pre>
 * ClientLevel level = Minecraft.getInstance().level;
 * if (level.getEntity(shooterId) instanceof LivingEntity shooter) { MinecraftForge.EVENT_BUS.post(new XxxEvent(...)); }
 * </pre>
 * 
 * 1.7.10 侧有两处必须改造：
 * <ol>
 * <li>{@code Minecraft.getInstance().level.getEntity(id)} 是客户端 API → 走
 * {@link com.tacz.guns.network.ClientSideHooks.Impl#get()}
 * （common 不能编译期依赖 client）；</li>
 * <li>1.7.10 客户端取实体是 {@code worldObj.getEntityByID(id)}，由 ClientProxy 注入实现。</li>
 * </ol>
 * 事件类本身（{@code api/event/common/*}）是 common 代码，直接 {@code MinecraftForge.EVENT_BUS.post(...)}。
 */
public abstract class AbstractShooterGunEventMessage extends MessageBase {

    protected int shooterId;
    protected ItemStack gunItemStack;

    protected AbstractShooterGunEventMessage() {}

    protected AbstractShooterGunEventMessage(int shooterId, ItemStack gunItemStack) {
        this.shooterId = shooterId;
        this.gunItemStack = gunItemStack;
    }

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeVarInt(shooterId);
        buf.writeItem(gunItemStack);
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.shooterId = buf.readVarInt();
        this.gunItemStack = buf.readItem();
    }

    /**
     * 子类实现：向 Forge 事件总线投递具体事件（{@link Side#CLIENT}）。
     */
    protected abstract void postEvent(EntityLivingBase shooter);

    /**
     * 上游 {@code handle(...)} 的客户端分支。
     */
    protected final void doClientEvent() {
        Entity entity = ClientSideHooks.get()
            .getEntityById(shooterId);
        if (entity instanceof EntityLivingBase) {
            postEvent((EntityLivingBase) entity);
        }
    }

    /**
     * 上游 {@code handle} 的公共骨架：仅客户端受理，并用 {@link NetworkTaskQueue} 代替
     * {@code context.enqueueWork}（1.7.10 的包体已由主线程分发，这里只是延后到本站 tick 末尾）。
     */
    protected static void handleOnClient(Runnable clientTask, MessageContext ctx) {
        if (ctx.side != Side.CLIENT) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, clientTask);
    }
}
