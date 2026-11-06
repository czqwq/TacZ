package com.tacz.guns.network.message;

import net.minecraft.entity.player.EntityPlayerMP;

import com.tacz.guns.inventory.AmmoStorageMenu;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 客户端 → 服务端：弹药存储界面的「虚拟条目」交互（不是原版槽位点击）。
 * 上游：{@code .../network/message/ClientMessageAmmoStorageClick.java}（上游是 {@code record}）
 *
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>上游 {@code record(int containerId, int entryId, AmmoStorageMenu.Action action)}；
 * 1.7.10 的 {@code IMessage} 必须有无参构造器 + 可写字段，因此改成普通类，
 * 但<b>保留同名访问器</b> {@link #containerId()} / {@link #entryId()} / {@link #action()}，
 * 调用点写法与上游一致</li>
 * <li>{@code player.containerMenu} → {@link EntityPlayerMP#openContainer}（{@code windowId} 即上游
 * {@code containerId}）</li>
 * </ul>
 * <p>
 * 依赖契约（common-game）：{@code inventory/AmmoStorageMenu} 保留类名、继承 {@code Container}、
 * 内部枚举 {@code AmmoStorageMenu.Action}，方法
 * {@code void handleInteraction(EntityPlayer player, int entryId, Action action)}。
 */
public final class ClientMessageAmmoStorageClick extends MessageBase {

    private int containerId;
    private int entryId;
    private AmmoStorageMenu.Action action;

    public ClientMessageAmmoStorageClick(int containerId, int entryId, AmmoStorageMenu.Action action) {
        this.containerId = containerId;
        this.entryId = entryId;
        this.action = action;
    }

    public ClientMessageAmmoStorageClick() {}

    public int containerId() {
        return containerId;
    }

    public int entryId() {
        return entryId;
    }

    public AmmoStorageMenu.Action action() {
        return action;
    }

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(entryId);
        buf.writeEnum(action);
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.containerId = buf.readVarInt();
        this.entryId = buf.readVarInt();
        this.action = buf.readEnum(AmmoStorageMenu.Action.class);
    }

    public static void handle(ClientMessageAmmoStorageClick message, MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, () -> {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            // readEnum 越界抛 IAE（1.20 语义）；此处 null 检查为双保险。
            if (message.action == null) {
                return;
            }
            if (player != null && player.openContainer instanceof AmmoStorageMenu
                && player.openContainer.windowId == message.containerId) {
                ((AmmoStorageMenu) player.openContainer).handleInteraction(player, message.entryId, message.action);
            }
        });
    }
}
