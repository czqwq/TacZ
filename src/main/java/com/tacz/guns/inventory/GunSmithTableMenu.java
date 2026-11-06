package com.tacz.guns.inventory;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.crafting.GunSmithTableIngredient;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageCraft;
import com.tacz.guns.resource.filter.RecipeFilter;
import com.tacz.guns.resource.index.CommonBlockIndex;
import com.tacz.guns.resource.pojo.data.block.TabConfig;

/**
 * 枪械工作台界面容器。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/inventory/GunSmithTableMenu.java:30}
 * 
 * <pre>
 * {@code public class GunSmithTableMenu extends AbstractContainerMenu}
 * </pre>
 *
 * <p>
 * <b>1.7.10 移植形态</b>（{@code AbstractContainerMenu} → {@link Container}）：
 * <table border="1">
 * <tr>
 * <th>上游 1.20.1</th>
 * <th>这里 1.7.10</th>
 * </tr>
 * <tr>
 * <td>{@code MenuType<GunSmithTableMenu> TYPE = IForgeMenuType.create(...)}</td>
 * <td>删除：
 * 1.7.10 由 {@link GuiHandler}（{@code IGuiHandler}）+ {@link GuiId#GUN_SMITH_TABLE} 按方块坐标构造</td>
 * </tr>
 * <tr>
 * <td>构造器 {@code (int id, Inventory, ResourceLocation)}</td>
 * <td>{@code (InventoryPlayer, ResourceLocation)}：
 * windowId 由 {@code FMLNetworkHandler.openGui} 分配</td>
 * </tr>
 * <tr>
 * <td>{@code quickMoveStack(Player,int)}</td>
 * <td>{@link #transferStackInSlot(EntityPlayer, int)}
 * （上游本方法直接返回 {@code ItemStack.EMPTY}，这里返回 {@code null}）</td>
 * </tr>
 * <tr>
 * <td>{@code stillValid(Player)}</td>
 * <td>{@link #canInteractWith(EntityPlayer)}</td>
 * </tr>
 * <tr>
 * <td>{@code player.level().getRecipeManager().byKey(id)}</td>
 * <td>1.7.10 无 {@code RecipeManager}：
 * 配方登记在 {@link GunSmithTableRecipe} 的静态表里（见该类注释），用
 * {@link GunSmithTableRecipe#getById(ResourceLocation)} 查</td>
 * </tr>
 * <tr>
 * <td>{@code player.getCapability(ITEM_HANDLER)}</td>
 * <td>{@code player.inventory}（{@link InventoryPlayer}
 * 就是 1.7.10 的 36 格物品栏 {@link IInventory}）</td>
 * </tr>
 * <tr>
 * <td>{@code player.isCreative()}</td>
 * <td>{@link EntityPlayer#capabilities}.{@code isCreativeMode}</td>
 * </tr>
 * <tr>
 * <td>{@code level.addFreshEntity(ItemEntity)}</td>
 * <td>{@code worldObj.spawnEntityInWorld(EntityItem)}</td>
 * </tr>
 * <tr>
 * <td>{@code player.inventoryMenu.broadcastFullState()}</td>
 * <td>{@code inventoryContainer.detectAndSendChanges()}</td>
 * </tr>
 * <tr>
 * <td>{@code recipe.getResultItem(registryAccess)}</td>
 * <td>{@code recipe.getOutput()}（可能为 {@code null}）</td>
 * </tr>
 * </table>
 *
 * <p>
 * <b>跨包契约</b>（{@code network/message/ClientMessageCraft} 依赖）：
 * 保留类名、继承 {@link Container}，并提供
 * {@link #doCraft(ResourceLocation, EntityPlayer)}。
 *
 * <p>
 * 本界面没有槽位（上游同样只注册 0 个 {@link Slot}：材料直接从玩家物品栏扣除），
 * 因此不需要 {@code addSlotToContainer}，也没有需要同步的槽位内容。
 */
public class GunSmithTableMenu extends Container {

    private final InventoryPlayer playerInventory;
    @Nullable
    private final ResourceLocation blockId;
    @Nullable
    private final RecipeFilter filter;
    @Nullable
    private ResourceLocation requestedRecipeId;

    public GunSmithTableMenu(InventoryPlayer inventory, @Nullable ResourceLocation blockId) {
        this.playerInventory = inventory;
        this.blockId = blockId;
        // 上游：TimelessAPI.getCommonBlockIndex(getBlockId()).map(CommonBlockIndex::getFilter).orElse(null)
        this.filter = blockId == null ? null
            : TimelessAPI.getCommonBlockIndex(blockId)
                .map(CommonBlockIndex::getFilter)
                .orElse(null);
    }

    @Nullable
    public ResourceLocation getBlockId() {
        return blockId;
    }

    /** 上游 {@code canSelectRecipe(ResourceLocation, RecipeManager)}；1.7.10 无 {@code RecipeManager}。 */
    public boolean canSelectRecipe(ResourceLocation recipeId) {
        if (this.blockId == null || TimelessAPI.getCommonBlockIndex(this.blockId)
            .isEmpty()) {
            return false;
        }
        GunSmithTableRecipe recipe = this.getRecipe(recipeId);
        if (recipe == null) {
            return false;
        }
        if (DefaultAssets.DEFAULT_BLOCK_ID.equals(this.blockId) && !SyncConfig.ENABLE_TABLE_FILTER.get()) {
            return TabConfig.DEFAULT_TABS.stream()
                .anyMatch(
                    tab -> tab.id()
                        .equals(recipe.getTab()));
        }
        return true;
    }

    /** Queues a client-side selection for when JEI restores the workbench screen. */
    public void requestRecipeSelection(ResourceLocation recipeId) {
        this.requestedRecipeId = recipeId;
    }

    @Nullable
    public ResourceLocation consumeRecipeSelection() {
        ResourceLocation recipeId = this.requestedRecipeId;
        this.requestedRecipeId = null;
        return recipeId;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        // 上游 quickMoveStack 直接返回 ItemStack.EMPTY
        return null;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return player.isEntityAlive();
    }

    @Nullable
    private GunSmithTableRecipe getRecipe(ResourceLocation recipeId) {
        if (!DefaultAssets.DEFAULT_BLOCK_ID.equals(getBlockId()) || SyncConfig.ENABLE_TABLE_FILTER.get()) {
            if (filter != null && !filter.contains(recipeId)) {
                return null;
            }
        }
        GunSmithTableRecipe gunSmithTableRecipe = GunSmithTableRecipe.getById(recipeId);
        if (gunSmithTableRecipe == null) {
            return null;
        }
        boolean flag = TimelessAPI.getCommonBlockIndex(getBlockId())
            .map(
                blockIndex -> blockIndex.getData()
                    .getTabs()
                    .stream()
                    .noneMatch(
                        tab -> tab.id()
                            .equals(gunSmithTableRecipe.getTab())))
            .orElse(true);
        if (DefaultAssets.DEFAULT_BLOCK_ID.equals(getBlockId()) && !SyncConfig.ENABLE_TABLE_FILTER.get()) {
            flag = false;
        }
        return flag ? null : gunSmithTableRecipe;
    }

    /**
     * 执行一次合成（服务端）。逐行对应上游同名方法：
     * 校验配方可见性 → 创造模式跳过扣材料 → 按 {@link GunSmithTableIngredient} 扫描 36 格物品栏并记账 →
     * 数量不足则整体放弃 → 扣材料 → 生成产物实体 → 刷新容器与客户端。
     */
    public void doCraft(ResourceLocation recipeId, EntityPlayer player) {
        GunSmithTableRecipe recipe = getRecipe(recipeId);
        if (recipe == null) {
            return;
        }
        InventoryPlayer inventory = player.inventory;
        // 是创造模式，就不扣材料
        if (!player.capabilities.isCreativeMode) {
            int[] recordCount = new int[inventory.getSizeInventory()];
            List<GunSmithTableIngredient> ingredients = recipe.getInputs();
            for (GunSmithTableIngredient ingredient : ingredients) {
                int count = 0;
                for (int slotIndex = 0; slotIndex < inventory.getSizeInventory(); slotIndex++) {
                    ItemStack stack = inventory.getStackInSlot(slotIndex);
                    if (stack == null || stack.getItem() == null) {
                        continue;
                    }
                    int stackCount = stack.stackSize;
                    if (ingredient.test(stack)) {
                        count = count + stackCount;
                        // 记录扣除的 slot 和数量
                        if (count <= ingredient.getCount()) {
                            // 如果数量不足，全扣
                            recordCount[slotIndex] = stackCount;
                        } else {
                            // 数量够了，只扣需要的数量
                            int remaining = count - ingredient.getCount();
                            recordCount[slotIndex] = stackCount - remaining;
                            break;
                        }
                    }
                }
                // 数量不够，不执行后续逻辑，合成失败
                if (count < ingredient.getCount()) {
                    return;
                }
            }
            // 开始扣材料
            for (int slotIndex = 0; slotIndex < recordCount.length; slotIndex++) {
                if (recordCount[slotIndex] > 0) {
                    inventory.decrStackSize(slotIndex, recordCount[slotIndex]);
                }
            }
        }

        // 给玩家对应的物品
        if (!player.worldObj.isRemote) {
            // getOutput() 已按上游 getResultItem 的语义返回副本（GunSmithTableRecipe:77），可以安全 splitStack
            ItemStack result = recipe.getOutput();
            if (result == null || result.getItem() == null) {
                return;
            }
            // Item counts in entity packets are bytes; also respect each stack's dynamic limit.
            int stackLimit = Math.max(1, Math.min(result.getMaxStackSize(), Byte.MAX_VALUE));
            while (result.stackSize > 0) {
                ItemStack stack = result.splitStack(Math.min(stackLimit, result.stackSize));
                EntityItem itemEntity = new EntityItem(
                    player.worldObj,
                    player.posX,
                    player.posY + 0.5,
                    player.posZ,
                    stack);
                itemEntity.delayBeforeCanPickup = 0;
                player.worldObj.spawnEntityInWorld(itemEntity);
            }
        }
        // 更新，否则客户端显示不正确
        player.inventory.markDirty();
        if (player.openContainer != null) {
            player.openContainer.detectAndSendChanges();
        }
        NetworkHandler.sendToClientPlayer(new ServerMessageCraft(this.windowId), player);
    }
}
