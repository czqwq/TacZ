package com.tacz.guns.network.message;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.block.entity.AmmoStorageBlockEntity;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.util.AmmoStorageHelper;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;

/**
 * 客户端 → 服务端：从弹药存储方块里取弹（一次一组）。
 * 上游：{@code .../network/message/ClientMessageAmmoStorageInteract.java}
 *
 * <p>
 * 1.7.10 差异（逐条）：
 * <ul>
 * <li>{@code BlockPos} → 三个 int（{@code x/y/z}）；{@code buf.writeBlockPos} → {@code writeBlockPos(int,int,int)}</li>
 * <li>{@code player.level()} → {@link EntityPlayerMP#worldObj}；
 * {@code player.level().getGameTime()} → {@code worldObj.getTotalWorldTime()}</li>
 * <li>{@code player.getPersistentData()} → {@link net.minecraft.entity.Entity#getEntityData()}（1.7.10 持久 NBT）</li>
 * <li>{@code distanceToSqr(x,y,z)} → {@link EntityPlayerMP#getDistanceSq(double, double, double)}</li>
 * <li>{@code player.level().getBlockEntity(pos)} → {@code worldObj.getTileEntity(x,y,z)}；
 * {@code BlockEntity} → {@link TileEntity}（类名沿用上游 {@code block/entity/AmmoStorageBlockEntity}）</li>
 * <li>{@code displayClientMessage(component, true)} → {@link EntityPlayerMP#addChatMessage}（1.7.10 无 action bar）</li>
 * <li>{@code stack.getHoverName()} → {@code stack.getDisplayName()}（返回 String）</li>
 * <li>{@code Inventory#add(stack)} → {@code player.inventory.addItemStackToInventory(stack)}；
 * {@code player.drop(stack, false)} → {@code player.dropItem(stack, false)}</li>
 * <li>声音：1.20 的 {@code SoundEvents.ARMOR_EQUIP_IRON} + {@code SoundSource} 在 1.7.10 是字符串音效名，
 * 这里用 {@code "random.pop"}（等价于「拾取」音效），差异记录于此</li>
 * </ul>
 * <p>
 * 依赖契约（common-game）：{@code block/entity/AmmoStorageBlockEntity} 保留类名（1.7.10 为 TileEntity），
 * 提供 {@code ItemStack extractAmmo(ResourceLocation ammoId, int amount, boolean simulate)}。
 */
public class ClientMessageAmmoStorageInteract extends MessageBase {

    private static final String LAST_PICKUP_TAG = "TacAmmoStorageLastPickup";
    private static final String LAST_ACCUMULATE_TAG = "TacAmmoStorageLastAccumulate";
    private static final String ACCUMULATE_AMMO_TAG = "TacAmmoStorageAccumulateAmmo";
    private static final String ACCUMULATE_COUNT_TAG = "TacAmmoStorageAccumulateCount";
    private static final long PICKUP_COOLDOWN_TICKS = 5L;
    private static final long ACCUMULATE_WINDOW_TICKS = 20L;

    private int x;
    private int y;
    private int z;

    public ClientMessageAmmoStorageInteract(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public ClientMessageAmmoStorageInteract() {}

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeBlockPos(x, y, z);
    }

    @Override
    protected void read(PacketBuffer buf) {
        int[] pos = buf.readBlockPos();
        this.x = pos[0];
        this.y = pos[1];
        this.z = pos[2];
    }

    public static void handle(ClientMessageAmmoStorageInteract message, MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return;
        }
        NetworkTaskQueue.enqueue(ctx.side, () -> {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null) {
                return;
            }
            long gameTime = player.worldObj.getTotalWorldTime();
            NBTTagCompound data = player.getEntityData();
            long lastPickup = data.getLong(LAST_PICKUP_TAG);
            if (lastPickup > 0L && gameTime - lastPickup < PICKUP_COOLDOWN_TICKS) {
                return;
            }
            if (player.getDistanceSq(message.x + 0.5D, message.y + 0.5D, message.z + 0.5D) > 36.0D) {
                return;
            }
            ItemStack gunStack = player.getHeldItem();
            if (gunStack == null || !(gunStack.getItem() instanceof IGun)) {
                return;
            }
            IGun gun = (IGun) gunStack.getItem();
            CommonGunIndex index = TimelessAPI.getCommonGunIndex(gun.getGunId(gunStack))
                .orElse(null);
            ResourceLocation ammoId = index == null ? null
                : index.getGunData()
                    .getAmmoId();
            if (ammoId == null) {
                return;
            }
            TileEntity tileEntity = player.worldObj.getTileEntity(message.x, message.y, message.z);
            if (!(tileEntity instanceof AmmoStorageBlockEntity)) {
                return;
            }
            AmmoStorageBlockEntity storage = (AmmoStorageBlockEntity) tileEntity;
            int amount = AmmoStorageHelper.getStackSize(AmmoStorageHelper.createAmmoStack(ammoId, 1));
            ItemStack extracted = storage.extractAmmo(ammoId, amount, false);
            if (extracted == null || extracted.stackSize <= 0) {
                data.setLong(LAST_PICKUP_TAG, gameTime);
                player.addChatMessage(new ChatComponentTranslation("message.tacz.ammo_storage.no_gun_stock"));
                return;
            }
            data.setLong(LAST_PICKUP_TAG, gameTime);
            int count = extracted.stackSize;
            if (!player.inventory.addItemStackToInventory(extracted)) {
                // 1.7.10：EntityPlayer#dropPlayerItemWithRandomChoice(ItemStack, boolean) ≡ 上游 player.drop(stack, false)
                player.dropPlayerItemWithRandomChoice(extracted, false);
            }
            int accumulatedCount = getAccumulatedCount(player, ammoId, count, gameTime);
            player.addChatMessage(
                new ChatComponentTranslation(
                    "message.tacz.ammo_storage.picked",
                    AmmoStorageHelper.createAmmoStack(ammoId, 1)
                        .getDisplayName(),
                    accumulatedCount));
            player.worldObj
                .playSoundEffect(message.x + 0.5D, message.y + 0.5D, message.z + 0.5D, "random.pop", 0.8F, 1.0F);
        });
    }

    private static int getAccumulatedCount(EntityPlayerMP player, ResourceLocation ammoId, int count, long gameTime) {
        String ammoKey = ammoId.toString();
        NBTTagCompound data = player.getEntityData();
        long lastAccumulate = data.getLong(LAST_ACCUMULATE_TAG);
        String lastAmmoKey = data.getString(ACCUMULATE_AMMO_TAG);
        int accumulated = count;
        if (lastAccumulate > 0L && gameTime - lastAccumulate <= ACCUMULATE_WINDOW_TICKS
            && ammoKey.equals(lastAmmoKey)) {
            accumulated += data.getInteger(ACCUMULATE_COUNT_TAG);
        }
        data.setLong(LAST_ACCUMULATE_TAG, gameTime);
        data.setString(ACCUMULATE_AMMO_TAG, ammoKey);
        data.setInteger(ACCUMULATE_COUNT_TAG, accumulated);
        return accumulated;
    }
}
