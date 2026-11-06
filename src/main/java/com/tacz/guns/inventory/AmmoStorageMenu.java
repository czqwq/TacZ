package com.tacz.guns.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.tacz.guns.block.entity.AmmoStorageBlockEntity;
import com.tacz.guns.util.AmmoStorageHelper;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 弹药库界面容器。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/inventory/AmmoStorageMenu.java:19}
 * 
 * <pre>
 * {@code public class AmmoStorageMenu extends AbstractContainerMenu}
 * </pre>
 *
 * <p>
 * <b>1.7.10 移植形态</b>（{@code AbstractContainerMenu} → {@link Container}，逐项对照）：
 * <table border="1">
 * <tr>
 * <th>上游 1.20.1</th>
 * <th>这里 1.7.10</th>
 * </tr>
 * <tr>
 * <td>构造器 {@code (int id, Inventory, ...)}</td>
 * <td>没有 id 参数：windowId 由
 * {@code FMLNetworkHandler.openGui} 分配并写 {@code openContainer.windowId}</td>
 * </tr>
 * <tr>
 * <td>{@code slots}</td>
 * <td>{@link #inventorySlots}（{@code List<Slot>}）</td>
 * </tr>
 * <tr>
 * <td>{@code addSlot(...)}</td>
 * <td>{@link #addSlotToContainer}</td>
 * </tr>
 * <tr>
 * <td>{@code broadcastChanges()}</td>
 * <td>{@link #detectAndSendChanges()}</td>
 * </tr>
 * <tr>
 * <td>{@code quickMoveStack(Player,int)}</td>
 * <td>{@link #transferStackInSlot(EntityPlayer, int)}</td>
 * </tr>
 * <tr>
 * <td>{@code stillValid(Player)}</td>
 * <td>{@link #canInteractWith(EntityPlayer)}</td>
 * </tr>
 * <tr>
 * <td>{@code moveItemStackTo(...)}</td>
 * <td>{@link #mergeItemStack}</td>
 * </tr>
 * <tr>
 * <td>{@code getCarried()/setCarried(stack)}</td>
 * <td>{@code player.inventory.getItemStack()/setItemStack(stack)}</td>
 * </tr>
 * <tr>
 * <td>{@code ItemStack.EMPTY}</td>
 * <td>{@code null}</td>
 * </tr>
 * </table>
 *
 * <p>
 * <b>额外数据同步（差异重点）</b>：上游 {@code broadcastChanges} 在槽位同步之外，还通过
 * {@code ServerMessageAmmoStorageContents} 网络包把「库容 / 已用 / 条目列表」推给客户端（上游 :113）。
 * 1.7.10 的 {@link Container} 只有两条通道：{@link ICrafting#sendSlotContents} 与
 * {@link ICrafting#sendProgressBarUpdate}（后者只传两个 {@code short} 截断的 int）。条目列表含
 * {@code ItemStack}，塞不进进度条，所以这里拆成两半：
 * <ul>
 * <li>标量（{@code capacityStacks} / {@code usedStacks}）：走原版 {@code sendProgressBarUpdate} →
 * 客户端 {@link #updateProgressBar}，<b>不需要任何网络代码</b>；</li>
 * <li>条目列表：走 {@link ContentSender} 钩子（common 侧静态钩子字段）。网络层在初始化时调
 * {@link #setContentSender} 注册一个「把 payload 编码成包发给玩家」的实现即可 ——
 * 它对应上游的 {@code ServerMessageAmmoStorageContents}，所以包里字段顺序、
 * {@link AmmoStorageEntry} 的构造参数都保持上游不变，
 * 客户端收到后调 {@link #setContents(int, long, List)} 落地。</li>
 * </ul>
 * 没有注册 {@link ContentSender} 时，界面仍能显示「已用/总量」，但列表为空（不会崩）。
 *
 * <p>
 * {@code player.isSpectator()} 在 1.7.10 没有对应物（旁观者模式 1.8+ 才有），已删除该判断。
 */
public class AmmoStorageMenu extends Container {

    public static final int STORAGE_ROWS = 3;
    public static final int STORAGE_COLUMNS = 9;
    public static final int STORAGE_SLOT_COUNT = STORAGE_ROWS * STORAGE_COLUMNS;
    public static final int STATUS_HEIGHT = 26;
    public static final int STORAGE_Y = 18 + STATUS_HEIGHT;
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 85 + STATUS_HEIGHT;
    public static final int HOTBAR_Y = 143 + STATUS_HEIGHT;

    /** 进度条 id（{@code sendProgressBarUpdate} 的第二个参数），对应客户端 {@link #updateProgressBar}。 */
    private static final int BAR_CAPACITY = 0;
    private static final int BAR_USED = 1;

    private final InventoryPlayer playerInventory;
    @Nullable
    private final AmmoStorageBlockEntity storage;
    private List<AmmoStorageEntry> entries = Collections.emptyList();
    private long sentRevision = -1;
    private int capacityStacks;
    private long usedStacks;
    private int nextEntryId;

    @Nullable
    private static ContentSender contentSender;

    /** Client menu: neither rendering nor vanilla slot sync requires a local block entity. */
    public AmmoStorageMenu(InventoryPlayer inventory) {
        this(inventory, null);
    }

    public AmmoStorageMenu(InventoryPlayer inventory, @Nullable AmmoStorageBlockEntity storage) {
        this.playerInventory = inventory;
        this.storage = storage;
        // Only real player slots belong to this menu. Sorters cannot reach bulk storage.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(
                    new Slot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(inventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }
    }

    public List<AmmoStorageEntry> getEntries() {
        return entries;
    }

    public int getCapacityStacks() {
        return capacityStacks;
    }

    public long getUsedStacks() {
        return usedStacks;
    }

    public void setContents(int capacityStacks, long usedStacks, List<AmmoStorageEntry> entries) {
        // 上游：player.level().isClientSide()；1.7.10 是 worldObj.isRemote
        if (this.playerInventory.player.worldObj.isRemote) {
            this.capacityStacks = capacityStacks;
            this.usedStacks = usedStacks;
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        }
    }

    /**
     * 1.7.10 的「向客户端推送自定义 Container 数据」通道。
     *
     * <p>
     * 对应上游 {@code NetworkHandler.sendToClientPlayer(new ServerMessageAmmoStorageContents(...), player)}。
     * 由 {@code network/**} 在初始化时实现并注册；不注册时列表不同步（不崩）。
     */
    public interface ContentSender {

        void send(EntityPlayer player, int windowId, int capacityStacks, long usedStacks,
            List<AmmoStorageEntry> entries);
    }

    public static void setContentSender(@Nullable ContentSender sender) {
        contentSender = sender;
    }

    @Override
    public void detectAndSendChanges() {
        // 上游 broadcastChanges()：先跑基类的槽位同步
        super.detectAndSendChanges();
        if (storage == null) {
            return;
        }
        int capacity = storage.getCapacityStacks();
        long used = storage.getUsedStacks();
        if (storage.getInventoryRevision() == sentRevision && capacity == capacityStacks && used == usedStacks) {
            // Stack sizes may change during a resource reload without changing item counts.
            boolean remainingChanged = false;
            for (AmmoStorageEntry entry : entries) {
                if (entry.remainingCapacity() != storage.getRemainingCapacity(entry.sample())) {
                    remainingChanged = true;
                    break;
                }
            }
            if (!remainingChanged) {
                return;
            }
        }
        List<AmmoStorageEntry> updated = new ArrayList<>();
        for (AmmoStorageInventory.Content content : storage.getContents()) {
            int id = -1;
            for (AmmoStorageEntry previous : entries) {
                if (AmmoStorageInventory.isSameItemSameTags(previous.sample(), content.sample())) {
                    id = previous.id();
                    break;
                }
            }
            // Never reuse a removed entry's ID: delayed clicks cannot target new ammo.
            updated.add(
                new AmmoStorageEntry(
                    id >= 0 ? id : nextEntryId++,
                    content.sample(),
                    content.count(),
                    storage.getRemainingCapacity(content.sample())));
        }
        updated.sort(
            Comparator.comparingInt((AmmoStorageEntry entry) -> AmmoStorageHelper.getSort(entry.sample()))
                .thenComparing(entry -> String.valueOf(AmmoStorageHelper.getAmmoId(entry.sample())))
                .thenComparing(
                    entry -> String.valueOf(
                        entry.sample() == null ? null
                            : entry.sample()
                                .getTagCompound())));
        entries = Collections.unmodifiableList(updated);
        capacityStacks = capacity;
        usedStacks = used;
        sentRevision = storage.getInventoryRevision();
        // 标量走原版进度条（1.7.10 会把两个 int 截断成 short，库容/已用远小于 32767，够用）
        for (ICrafting crafter : crafters) {
            crafter.sendProgressBarUpdate(this, BAR_CAPACITY, capacityStacks);
            crafter.sendProgressBarUpdate(this, BAR_USED, (int) usedStacks);
        }
        // 条目列表走网络层钩子（见类注释）
        if (contentSender != null) {
            contentSender.send(playerInventory.player, windowId, capacityStacks, usedStacks, entries);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void updateProgressBar(int id, int data) {
        if (id == BAR_CAPACITY) {
            this.capacityStacks = data;
        } else if (id == BAR_USED) {
            this.usedStacks = data;
        }
    }

    public void handleInteraction(EntityPlayer player, int entryId, Action action) {
        if (storage == null || player.openContainer != this || !player.isEntityAlive() || !canInteractWith(player)) {
            return;
        }
        AmmoStorageEntry entry = null;
        for (AmmoStorageEntry value : entries) {
            if (value.id() == entryId) {
                entry = value;
                break;
            }
        }
        if (action == Action.QUICK_MOVE) {
            if (entry != null) {
                storage.moveAmmoToInventory(playerInventory, 36, entry.sample(), getTransferStackSize(entry));
            }
        } else if (!isEmpty(getCarried())) {
            ItemStack held = getCarried();
            int requested = action == Action.PICKUP_HALF ? 1 : held.stackSize;
            int inserted = storage.insertAmmo(AmmoStorageInventory.withCount(held, requested), false);
            if (inserted > 0) {
                // 上游 held.copyWithCount(n) 在 n == 0 时得到的是「空栈」（1.20 isEmpty 判 count <= 0），
                // 1.7.10 的空栈是 null，必须显式置 null，否则手里会留下一个 stackSize = 0 的幽灵栈。
                int remaining = held.stackSize - inserted;
                setCarried(player, remaining > 0 ? AmmoStorageInventory.withCount(held, remaining) : null);
            }
        } else if (entry != null) {
            ItemStack preview = storage.extractAmmo(entry.sample(), getTransferStackSize(entry), true);
            int amount = preview == null ? 0 : preview.stackSize;
            if (action == Action.PICKUP_HALF) {
                amount = amount / 2 + amount % 2;
            }
            setCarried(player, storage.extractAmmo(entry.sample(), amount, false));
        }
        detectAndSendChanges();
    }

    private int getTransferStackSize(AmmoStorageEntry entry) {
        ItemStack sample = entry.sample();
        int sampleLimit = sample == null ? 64 : sample.getMaxStackSize();
        return Math.min(playerInventory.getInventoryStackLimit(), sampleLimit);
    }

    /** 上游 {@code Container#getCarried()}：1.7.10 把「鼠标上拿着的栈」存在 {@link InventoryPlayer} 里。 */
    @Nullable
    private ItemStack getCarried() {
        return playerInventory.getItemStack();
    }

    /**
     * 上游 {@code Container#setCarried(ItemStack)}。
     *
     * <p>
     * 1.7.10 的手持栈不在槽位同步通道里：原版改完手持栈后由
     * {@link EntityPlayerMP#updateHeldItem()} 单独下发 {@code S2FPacketSetSlot(-1, -1, stack)}
     * （{@code EntityPlayerMP.java:877-883}），{@code processClickWindow} 也是这么做的。
     * 本界面的「点条目取出/存入」走的是自定义网络包（{@link com.tacz.guns.network.message.ClientMessageAmmoStorageClick}），
     * 不经过 {@code slotClick}，所以必须在这里补同一次下发，否则客户端手里的物品与服务端不一致
     * （表现为鼠标上的弹药「看不到」，下一次点击按陈旧状态结算 ⇒ 丢件/复制）。
     */
    private void setCarried(EntityPlayer player, @Nullable ItemStack stack) {
        playerInventory.setItemStack(stack);
        if (player instanceof EntityPlayerMP serverPlayer) {
            serverPlayer.updateHeldItem();
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (storage == null || index < 0 || index >= inventorySlots.size() || !canInteractWith(player)) {
            return null;
        }
        Slot slot = inventorySlots.get(index);
        ItemStack stack = slot.getStack();
        if (isEmpty(stack)) {
            return null;
        }
        ItemStack original = stack.copy();
        if (AmmoStorageHelper.isValidAmmo(stack)) {
            int inserted = storage.insertAmmo(stack, false);
            if (inserted == 0) {
                return null;
            }
            stack.stackSize -= inserted;
        } else if (!mergeItemStack(stack, index < 27 ? 27 : 0, index < 27 ? 36 : 27, false)) {
            return null;
        }
        if (stack.stackSize <= 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        slot.onPickupFromSlot(player, stack);
        detectAndSendChanges();
        return original;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        // 上游：player.level().isClientSide() || storage != null && storage.stillValid(player)
        return player.worldObj.isRemote || storage != null && storage.stillValid(player);
    }

    private static boolean isEmpty(@Nullable ItemStack stack) {
        return stack == null || stack.getItem() == null;
    }

    public enum Action {
        PICKUP,
        PICKUP_HALF,
        QUICK_MOVE
    }
}
