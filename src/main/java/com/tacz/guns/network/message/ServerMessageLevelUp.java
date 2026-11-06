package com.tacz.guns.network.message;

import net.minecraft.item.ItemStack;

import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.MessageBase;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 服务端 → 客户端：枪械升级提示（Toast）。
 * 上游：{@code .../network/message/ServerMessageLevelUp.java}
 *
 * <p>
 * 1.7.10 差异：1.7.10 <b>没有 toast 系统</b>（1.13+ 才有 {@code Minecraft#getToasts()}），
 * 而且上游自己的展示代码也处于注释状态（TODO 枪械升级逻辑完成后解封）。
 * 因此这里保留完整的字段与 getter（供未来客户端实现），客户端分支留空并记录差异。
 */
public class ServerMessageLevelUp extends MessageBase {

    private ItemStack gun;
    private int level;

    public ServerMessageLevelUp(ItemStack gun, int level) {
        this.gun = gun;
        this.level = level;
    }

    public ServerMessageLevelUp() {}

    @Override
    protected void write(PacketBuffer buf) {
        // 上游 writeItemStack(message.gun, true)（第二个参数是「写入完整 NBT」）
        buf.writeItemStack(gun);
        buf.writeInt(level);
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.gun = buf.readItemStack();
        this.level = buf.readInt();
    }

    public ItemStack getGun() {
        return this.gun;
    }

    public int getLevel() {
        return this.level;
    }

    public static void handle(ServerMessageLevelUp message, MessageContext ctx) {
        if (ctx.side != Side.CLIENT) {
            return;
        }
        // TODO(port): 上游在这里给玩家弹 toast（GunLevelUpToast）；1.7.10 无 toast API，
        // 升级逻辑移植完成后可改为聊天栏提示（ChatComponentTranslation("toast.tacz.level_up")）
    }
}
