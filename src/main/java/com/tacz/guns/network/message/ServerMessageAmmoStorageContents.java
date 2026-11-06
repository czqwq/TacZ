package com.tacz.guns.network.message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.tacz.guns.inventory.AmmoStorageEntry;
import com.tacz.guns.inventory.AmmoStorageMenu;
import com.tacz.guns.mcshim.PacketBuffer;
import com.tacz.guns.network.ClientSideHooks;
import com.tacz.guns.network.MessageBase;
import com.tacz.guns.network.NetworkTaskQueue;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import io.netty.handler.codec.DecoderException;

/**
 * 服务端 → 客户端：弹药存储界面的内容快照。
 * 上游：{@code .../network/message/ServerMessageAmmoStorageContents.java}（上游是 {@code record}）
 *
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>record → 普通类，但保留同名访问器 {@link #containerId()} / {@link #capacityStacks()} /
 * {@link #usedStacks()} / {@link #entries()}</li>
 * <li>{@code List.copyOf} → {@code Collections.unmodifiableList(new ArrayList<>(...))}（Java 8）</li>
 * <li>{@code ItemStack.isEmpty()} → {@code sample == null}（1.7.10 空物品为 null）</li>
 * <li>客户端刷新走 {@link com.tacz.guns.network.ClientSideHooks.Impl#updateAmmoStorage}（{@code Minecraft} 是 client 类，
 * common 不许编译期依赖；{@code AmmoStorageMenu} 本身是 common 类）</li>
 * </ul>
 * <p>
 * 依赖契约（common-game）：{@code inventory/AmmoStorageEntry} 保留 {@code id()/sample()/count()/remainingCapacity()}；
 * {@code AmmoStorageMenu} 保留 {@code STORAGE_SLOT_COUNT} 与
 * {@code setContents(int, long, List<AmmoStorageEntry>)}。
 */
public class ServerMessageAmmoStorageContents extends MessageBase {

    private int containerId;
    private int capacityStacks;
    private long usedStacks;
    private List<AmmoStorageEntry> entries;

    public ServerMessageAmmoStorageContents(int containerId, int capacityStacks, long usedStacks,
        List<AmmoStorageEntry> entries) {
        this.containerId = containerId;
        this.capacityStacks = capacityStacks;
        this.usedStacks = usedStacks;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public ServerMessageAmmoStorageContents() {
        this.entries = Collections.emptyList();
    }

    public int containerId() {
        return containerId;
    }

    public int capacityStacks() {
        return capacityStacks;
    }

    public long usedStacks() {
        return usedStacks;
    }

    public List<AmmoStorageEntry> entries() {
        return entries;
    }

    @Override
    protected void write(PacketBuffer buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(capacityStacks);
        buf.writeVarLong(usedStacks);
        buf.writeVarInt(entries.size());
        for (AmmoStorageEntry entry : entries) {
            buf.writeVarInt(entry.id());
            buf.writeItem(entry.sample());
            buf.writeVarLong(entry.count());
            buf.writeVarLong(entry.remainingCapacity());
        }
    }

    @Override
    protected void read(PacketBuffer buf) {
        this.containerId = buf.readVarInt();
        this.capacityStacks = buf.readVarInt();
        this.usedStacks = buf.readVarLong();
        int size = buf.readVarInt();
        if (capacityStacks < 0 || usedStacks < 0 || size < 0 || size > AmmoStorageMenu.STORAGE_SLOT_COUNT) {
            throw new DecoderException("Invalid ammo storage entry count: " + size);
        }
        List<AmmoStorageEntry> read = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int id = buf.readVarInt();
            ItemStack sample = buf.readItem();
            long count = buf.readVarLong();
            long remaining = buf.readVarLong();
            if (id < 0 || sample == null || count <= 0 || remaining < 0) {
                throw new DecoderException("Invalid ammo storage entry");
            }
            read.add(new AmmoStorageEntry(id, sample, count, remaining));
        }
        this.entries = Collections.unmodifiableList(read);
    }

    public static void handle(ServerMessageAmmoStorageContents message, MessageContext ctx) {
        if (ctx.side != Side.CLIENT) {
            return;
        }
        NetworkTaskQueue.enqueue(
            ctx.side,
            () -> ClientSideHooks.get()
                .updateAmmoStorage(message));
    }
}
