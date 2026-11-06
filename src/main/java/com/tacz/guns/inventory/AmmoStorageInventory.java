package com.tacz.guns.inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/**
 * 弹药库的特殊库存：按「弹药类型」聚合，一种弹药只占一格，数量用 {@code long} 记（可远超 1 组）。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/inventory/AmmoStorageInventory.java:20}
 * 
 * <pre>
 * {@code public class AmmoStorageInventory implements IItemHandler}
 * </pre>
 * 
 * 上游注释：<i>「不作为标准的容器提供，避免被按格意外操作，仍可被作为 IItemHandler 使用」</i>。
 *
 * <p>
 * <b>1.7.10 移植形态</b>：{@code net.minecraftforge.items.IItemHandler} 不存在，1.7.10 唯一的库存抽象是
 * {@link IInventory}，所以本类改成 {@code implements IInventory}。语义映射（逐条对照上游方法）：
 * <table border="1">
 * <tr>
 * <th>上游 IItemHandler</th>
 * <th>这里 IInventory</th>
 * </tr>
 * <tr>
 * <td>{@code getSlots()}</td>
 * <td>{@link #getSizeInventory()}（{@link #getSlots()} 保留为同名别名）</td>
 * </tr>
 * <tr>
 * <td>{@code getStackInSlot(slot)}</td>
 * <td>{@link #getStackInSlot(int)}（数量截断到 {@code Integer.MAX_VALUE}）</td>
 * </tr>
 * <tr>
 * <td>{@code insertItem(slot, stack, simulate)}</td>
 * <td>{@link #insertItem(int, ItemStack, boolean)}（同名保留）/
 * {@link #setInventorySlotContents(int, ItemStack)}（无模拟）</td>
 * </tr>
 * <tr>
 * <td>{@code extractItem(slot, amount, simulate)}</td>
 * <td>{@link #extractItem(int, int, boolean)}（同名保留）/
 * {@link #decrStackSize(int, int)}（无模拟）</td>
 * </tr>
 * <tr>
 * <td>{@code getSlotLimit(slot)}</td>
 * <td>{@link #getSlotLimit(int)}（同名保留）+
 * {@link #getInventoryStackLimit()}（固定 64，见该方法注释）</td>
 * </tr>
 * <tr>
 * <td>{@code isItemValid(slot, stack)}</td>
 * <td>{@link #isItemValidForSlot(int, ItemStack)}（IInventory 强制）+
 * 保留同名 {@link #isItemValid(int, ItemStack)}</td>
 * </tr>
 * </table>
 *
 * <p>
 * <b>与上游的差异（1.7.10 限制，不是设计选择）</b>：
 * <ul>
 * <li>{@code IItemHandler} 的 {@code simulate} 语义在 {@code IInventory} 里没有对应物：逐槽的
 * {@code setInventorySlotContents}/{@code decrStackSize} <b>总是真写</b>；需要模拟时走同名保留的
 * {@link #insertItem(int, ItemStack, boolean)} / {@link #extractItem(int, int, boolean)} /
 * {@link #insert(ItemStack, boolean)} / {@link #extract(ItemStack, int, boolean)}。</li>
 * <li>{@code ItemStack.EMPTY} → {@code null}（1.7.10 的空栈表示），因此「放入失败」返回整栈、
 * 「取出为空」返回 {@code null}。</li>
 * <li>{@code IInventory} 强制要求「按格可写」，与上游「避免被按格意外操作」的意图冲突；
 * 这里仍实现它（否则漏斗/其它模组无法访问），但所有写入都过 {@code validator} 并维持
 * 「一种弹药一格」的不变式，越界/非法写入直接忽略。</li>
 * <li>{@code getStackInSlot} 可能返回 {@code stackSize > getMaxStackSize()} 的栈（整库聚合）；
 * 1.7.10 的自动化（漏斗等）会按原版逻辑搬运这种超大栈，属已知差异，正式接口是方块自己的 GUI。</li>
 * <li>{@code getStackInSlotOnClosing} 返回 {@code null}：上游没有「关界面时掉落内容物」的语义。</li>
 * <li>NBT 键（{@code Slot} / {@code Stack} / {@code Count}）与上游完全一致，存档可互读。</li>
 * </ul>
 */
public class AmmoStorageInventory implements IInventory {

    /** 1.7.10 IInventory 的界面标题 lang key（上游由 {@code MenuProvider#getDisplayName} 给出）。 */
    public static final String INVENTORY_NAME = "gui.tacz.ammo_storage.title";

    private final Entry[] entries;
    private final IntSupplier capacityStacks;
    private final Predicate<ItemStack> validator;
    private final ToIntFunction<ItemStack> stackSize;
    private final Runnable onChanged;
    private long revision;

    public AmmoStorageInventory(int types, IntSupplier capacityStacks, Predicate<ItemStack> validator,
        ToIntFunction<ItemStack> stackSize, Runnable onChanged) {
        this.entries = new Entry[types];
        this.capacityStacks = capacityStacks;
        this.validator = validator;
        this.stackSize = stackSize;
        this.onChanged = onChanged;
    }

    public long getRevision() {
        return revision;
    }

    public int getCapacityStacks() {
        return Math.max(0, capacityStacks.getAsInt());
    }

    public long getUsedStacks() {
        long used = 0;
        for (Entry entry : entries) {
            if (entry != null) {
                used = saturatedAdd(used, stacksFor(entry.count, entry.sample));
            }
        }
        return used;
    }

    public int getUsedTypes() {
        int used = 0;
        for (Entry entry : entries) {
            if (entry != null) {
                used++;
            }
        }
        return used;
    }

    /** Includes unused space in this variant's partially filled final stack. */
    public long getRemainingCapacity(@Nullable ItemStack sample) {
        if (sample == null || sample.getItem() == null || !validator.test(sample)) {
            return 0;
        }
        int slot = findMatchingSlot(sample);
        if (slot < 0 && findEmptySlot() < 0) {
            return 0;
        }
        long used = getUsedStacks();
        int capacity = getCapacityStacks();
        if (used > capacity) {
            // A data reload may reduce capacity or stack sizes. Keep stock, but block insertion.
            return 0;
        }
        long count = slot < 0 ? 0 : entries[slot].count;
        long availableStacks = capacity - used + stacksFor(count, sample);
        return Math.max(0, availableStacks * getStackSize(sample) - count);
    }

    public int insert(@Nullable ItemStack stack, boolean simulate) {
        if (stack == null || stack.getItem() == null || !validator.test(stack)) {
            return 0;
        }
        int slot = findMatchingSlot(stack);
        if (slot < 0) {
            slot = findEmptySlot();
        }
        int inserted = slot < 0 ? 0 : (int) insertIntoSlot(slot, stack, stack.stackSize, simulate);
        if (!simulate && inserted > 0) {
            changed();
        }
        return inserted;
    }

    /** Bulk API; unlike per-slot extraction, this may return more than one stack. */
    @Nullable
    public ItemStack extract(@Nullable ItemStack sample, int amount, boolean simulate) {
        int slot = sample == null || sample.getItem() == null ? -1 : findMatchingSlot(sample);
        return slot < 0 ? null : extractFromSlot(slot, amount, simulate);
    }

    /**
     * Moves only what fits, on the server thread, without rolling back by slot index.
     *
     * <p>
     * 1.7.10：{@code net.minecraft.world.Container} → {@link IInventory}，因此
     * {@code getContainerSize/getItem/canPlaceItem/setItem/setChanged/getMaxStackSize} 依次换成
     * {@code getSizeInventory/getStackInSlot/isItemValidForSlot/setInventorySlotContents/markDirty/getInventoryStackLimit}。
     */
    public int moveToInventory(@Nullable IInventory target, int targetSlots, @Nullable ItemStack sample, int amount) {
        if (sample == null || sample.getItem() == null || amount <= 0 || target == null) {
            return 0;
        }
        int remaining = amount;
        int end = Math.min(targetSlots, target.getSizeInventory());
        for (int pass = 0; pass < 2 && remaining > 0; pass++) {
            for (int slot = end - 1; slot >= 0 && remaining > 0; slot--) {
                ItemStack existing = target.getStackInSlot(slot);
                boolean existingEmpty = existing == null || existing.getItem() == null;
                if ((existingEmpty != (pass == 1)) || !target.isItemValidForSlot(slot, sample)
                    || (!existingEmpty && !isSameItemSameTags(existing, sample))) {
                    continue;
                }
                int limit = Math.min(target.getInventoryStackLimit(), sample.getMaxStackSize());
                int accepted = Math.min(remaining, Math.max(0, limit - (existingEmpty ? 0 : existing.stackSize)));
                ItemStack extracted = extract(sample, accepted, false);
                if (extracted != null && extracted.getItem() != null && extracted.stackSize > 0) {
                    ItemStack merged = extracted.copy();
                    merged.stackSize = (existingEmpty ? 0 : existing.stackSize) + extracted.stackSize;
                    target.setInventorySlotContents(slot, merged);
                    remaining -= extracted.stackSize;
                }
            }
        }
        int moved = amount - remaining;
        if (moved > 0) {
            target.markDirty();
        }
        return moved;
    }

    public List<Content> getContents() {
        List<Content> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry != null) {
                result.add(new Content(entry.sample.copy(), entry.count));
            }
        }
        return Collections.unmodifiableList(result);
    }

    public int getComparatorSignal() {
        long used = getUsedStacks();
        int capacity = Math.max(1, getCapacityStacks());
        return used == 0 ? 0 : (int) (14L * Math.min(used, capacity) / capacity + 1);
    }

    public NBTTagList save() {
        NBTTagList result = new NBTTagList();
        for (int slot = 0; slot < entries.length; slot++) {
            Entry entry = entries[slot];
            if (entry != null) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setByte("Slot", (byte) slot);
                // 上游 tag.put("Stack", entry.sample.save(new CompoundTag()))；1.7.10 对应 writeToNBT
                tag.setTag("Stack", entry.sample.writeToNBT(new NBTTagCompound()));
                tag.setLong("Count", entry.count);
                result.appendTag(tag);
            }
        }
        return result;
    }

    /** Restores saved stock, including excess capacity, without invoking world updates. */
    public void load(@Nullable NBTTagList list) {
        Arrays.fill(entries, null);
        if (list == null) {
            revision++;
            return;
        }
        BitSet seenSlots = new BitSet(entries.length);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int oldSlot = tag.getInteger("Slot");
            if (!hasNumericKey(tag, "Slot") || oldSlot < 0
                || oldSlot >= entries.length
                || seenSlots.get(oldSlot)
                || !tag.hasKey("Stack", Constants.NBT.TAG_COMPOUND)) {
                continue;
            }
            try {
                ItemStack sample = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("Stack"));
                long count = tag.getLong("Count");
                if (sample == null || sample.getItem() == null || count <= 0 || !validator.test(sample)) {
                    continue;
                }
                seenSlots.set(oldSlot);
                int slot = findMatchingSlot(sample);
                if (slot < 0) {
                    slot = entries[oldSlot] == null ? oldSlot : findEmptySlot();
                }
                if (slot >= 0) {
                    // Capacity limits apply to new insertions, never to existing saved items.
                    Entry entry = entries[slot];
                    if (entry == null) {
                        entries[slot] = new Entry(single(sample), count);
                    } else {
                        entry.count = saturatedAdd(entry.count, count);
                    }
                }
            } catch (RuntimeException ignored) {
                // Malformed legacy items must not prevent the world from loading.
            }
        }
        revision++;
    }

    // ================================================================================================
    // 上游 IItemHandler 的同名 API（1.7.10 无该接口，保留方法名供自动化/漏斗与上层调用）
    // ================================================================================================

    /** 上游 {@code IItemHandler#getSlots()}。 */
    public int getSlots() {
        return entries.length;
    }

    /**
     * 上游 {@code IItemHandler#insertItem(int, ItemStack, boolean)}。
     *
     * @return 未放入的剩余（1.7.10 用 {@code null} 表示「已全部放入」，即上游的 {@code ItemStack.EMPTY}）
     */
    @Nullable
    public ItemStack insertItem(int slot, @Nullable ItemStack stack, boolean simulate) {
        if (!isItemValid(slot, stack)) {
            return stack;
        }
        int inserted = (int) insertIntoSlot(slot, stack, stack.stackSize, simulate);
        if (!simulate && inserted > 0) {
            changed();
        }
        return inserted == stack.stackSize ? null : withCount(stack, stack.stackSize - inserted);
    }

    /**
     * 上游 {@code IItemHandler#extractItem(int, int, boolean)}。
     *
     * @return 取出的栈（{@code null} 表示没有取出任何东西，即上游的 {@code ItemStack.EMPTY}）
     */
    @Nullable
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        Entry entry = getEntry(slot);
        if (entry == null || amount <= 0) {
            return null;
        }
        return extractFromSlot(slot, Math.min(amount, Math.max(0, entry.sample.getMaxStackSize())), simulate);
    }

    /** 上游 {@code IItemHandler#getSlotLimit(int)}。 */
    public int getSlotLimit(int slot) {
        if (slot < 0 || slot >= entries.length) {
            return 0;
        }
        Entry entry = entries[slot];
        return entry == null ? Integer.MAX_VALUE
            : (int) Math.min(Integer.MAX_VALUE, (long) getCapacityStacks() * getStackSize(entry.sample));
    }

    // ================================================================================================
    // IInventory（1.7.10）
    // ================================================================================================

    @Override
    public int getSizeInventory() {
        return entries.length;
    }

    @Override
    @Nullable
    public ItemStack getStackInSlot(int slot) {
        Entry entry = getEntry(slot);
        // ItemStack can only express an int. Bulk contents and saves retain the full long count.
        return entry == null ? null : withCount(entry.sample, (int) Math.min(Integer.MAX_VALUE, entry.count));
    }

    @Override
    @Nullable
    public ItemStack decrStackSize(int index, int count) {
        Entry entry = getEntry(index);
        if (entry == null || count <= 0) {
            return null;
        }
        // 上游 extractItem 会把请求量再夹到「该弹药的单组大小」，这里保持一致
        return extractFromSlot(index, Math.min(count, Math.max(0, entry.sample.getMaxStackSize())), false);
    }

    @Override
    @Nullable
    public ItemStack getStackInSlotOnClosing(int index) {
        // 上游没有「关闭容器时掉落内容物」的语义（内容物保存在方块实体 NBT 里），因此不交出任何东西
        return null;
    }

    @Override
    public void setInventorySlotContents(int index, @Nullable ItemStack stack) {
        if (index < 0 || index >= entries.length) {
            return;
        }
        entries[index] = null;
        if (stack != null && stack.getItem() != null && stack.stackSize > 0 && validator.test(stack)) {
            // 维持「一种弹药只占一格」：已有同种样品的格子优先
            int matching = findMatchingSlot(stack);
            insertIntoSlot(matching >= 0 ? matching : index, stack, stack.stackSize, false);
        }
        changed();
    }

    @Override
    public String getInventoryName() {
        return INVENTORY_NAME;
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        // 上游 getSlotLimit(slot) = capacityStacks * 该弹药的单组大小；1.7.10 的 IInventory 没有「按槽动态上限」，
        // 这里给原版常用值 64（自动化按组搬运），整库容量仍由 capacityStacks / getRemainingCapacity 把关。
        return 64;
    }

    @Override
    public void markDirty() {
        changed();
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        // 距离校验由方块实体 / Container 负责（上游 AmmoStorageBlockEntity#stillValid）
        return true;
    }

    @Override
    public void openInventory() {
        // 上游没有「打开计数」，无对应逻辑
    }

    @Override
    public void closeInventory() {
        // 同上
    }

    @Override
    public boolean isItemValidForSlot(int index, @Nullable ItemStack stack) {
        return isItemValid(index, stack);
    }

    /** 上游 {@code IItemHandler#isItemValid(int, ItemStack)}（1.7.10 无该接口，保留方法供漏斗/自动化调用）。 */
    public boolean isItemValid(int slot, @Nullable ItemStack stack) {
        return slot >= 0 && slot < entries.length
            && stack != null
            && stack.getItem() != null
            && stack.stackSize > 0
            && validator.test(stack);
    }

    // ================================================================================================
    // 内部实现（与上游一一对应）
    // ================================================================================================

    private long insertIntoSlot(int slot, ItemStack sample, long amount, boolean simulate) {
        Entry entry = entries[slot];
        int matching = findMatchingSlot(sample);
        if (amount <= 0 || (entry != null && !isSameItemSameTags(entry.sample, sample))
            || (matching >= 0 && matching != slot)) {
            return 0;
        }
        long inserted = Math.min(amount, getRemainingCapacity(sample));
        if (!simulate && inserted > 0) {
            if (entry == null) {
                entries[slot] = new Entry(single(sample), inserted);
            } else {
                entry.count += inserted;
            }
        }
        return inserted;
    }

    @Nullable
    private ItemStack extractFromSlot(int slot, int amount, boolean simulate) {
        Entry entry = entries[slot];
        int extracted = (int) Math.min(Math.max(0, amount), entry.count);
        if (extracted == 0) {
            return null;
        }
        ItemStack result = withCount(entry.sample, extracted);
        if (!simulate) {
            entry.count -= extracted;
            if (entry.count == 0) {
                entries[slot] = null;
            }
            changed();
        }
        return result;
    }

    private int getStackSize(ItemStack sample) {
        return Math.max(1, stackSize.applyAsInt(sample));
    }

    private long stacksFor(long count, ItemStack sample) {
        return count == 0 ? 0 : 1 + (count - 1) / getStackSize(sample);
    }

    private static long saturatedAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    private int findMatchingSlot(ItemStack sample) {
        for (int slot = 0; slot < entries.length; slot++) {
            if (entries[slot] != null && isSameItemSameTags(entries[slot].sample, sample)) {
                return slot;
            }
        }
        return -1;
    }

    private int findEmptySlot() {
        for (int slot = 0; slot < entries.length; slot++) {
            if (entries[slot] == null) {
                return slot;
            }
        }
        return -1;
    }

    @Nullable
    private Entry getEntry(int slot) {
        return slot < 0 || slot >= entries.length ? null : entries[slot];
    }

    private void changed() {
        revision++;
        onChanged.run();
    }

    /**
     * 1.7.10 版 {@code ItemStack.isSameItemSameTags(ItemStack, ItemStack)}：
     * null 安全，比较 物品 + damage + NBT。
     */
    public static boolean isSameItemSameTags(@Nullable ItemStack left, @Nullable ItemStack right) {
        if (left == null || right == null || left.getItem() == null || right.getItem() == null) {
            return false;
        }
        return left.getItem() == right.getItem() && left.getItemDamage() == right.getItemDamage()
            && ItemStack.areItemStackTagsEqual(left, right);
    }

    /**
     * 1.7.10 版 {@code ItemStack#copyWithCount(int)}：null 安全，调用方保证 {@code count > 0}。
     */
    @Nullable
    public static ItemStack withCount(@Nullable ItemStack stack, int count) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        ItemStack copy = stack.copy();
        copy.stackSize = count;
        return copy;
    }

    /**
     * 上游 {@code tag.contains(key, Tag.TAG_ANY_NUMERIC)} 的 1.7.10 版。
     *
     * <p>
     * 1.20 的 {@code TAG_ANY_NUMERIC}（99）在 {@code contains} 里被特判成「任意数字类型」；
     * 1.7.10 的 {@link NBTTagCompound#hasKey(String, int)} 只是 {@code getId() == type} 的严格比较，
     * 传 99 永远为假，因此这里显式判断 BYTE/SHORT/INT/LONG 四种数字 tag。
     */
    private static boolean hasNumericKey(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key)) {
            return false;
        }
        int id = tag.getTag(key)
            .getId();
        return id == Constants.NBT.TAG_BYTE || id == Constants.NBT.TAG_SHORT
            || id == Constants.NBT.TAG_INT
            || id == Constants.NBT.TAG_LONG;
    }

    /** 只保留物品身份 + 1 个数量的「样品」。 */
    private static ItemStack single(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }

    public record Content(ItemStack sample, long count) {}

    private static final class Entry {

        private final ItemStack sample;
        private long count;

        private Entry(ItemStack sample, long count) {
            this.sample = sample;
            this.count = count;
        }
    }
}
