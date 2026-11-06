package com.tacz.guns.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;

import com.tacz.guns.api.client.event.SwapItemWithOffHand;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.item.IAnimationItem;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.resource.ClientIndexManager;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

/**
 * 快捷栏切换时触发「切枪（draw）」状态机，并周期预热枪械模型。
 *
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/client/event/InventoryEvent.java}
 * （{@code @Mod.EventBusSubscriber(value = Dist.CLIENT, modid = GunMod.MOD_ID)}）。
 *
 * <p>
 * 平台替换（相对上游）：
 * <ul>
 * <li>{@code LocalPlayer} → {@link EntityPlayerSP}，{@code Minecraft#player} → {@code thePlayer}；</li>
 * <li>{@code player.getInventory()} → 字段 {@code player.inventory}；{@code Inventory#selected} →
 * {@link InventoryPlayer#currentItem}；{@code Inventory#getItem(i)} →
 * {@link InventoryPlayer#getStackInSlot(int)}（{@code mainInventory[i]} 的 IInventory 视图，
 * 越界返回 null 的语义与 1.20 空栈一致）；</li>
 * <li>{@code ItemStack.EMPTY}（空栈常量）→ {@code null}（1.7.10 的约定，见 api 跨包契约）；
 * 因此 {@link #oldHotbarSelectItem} 初始值为 {@code null}；</li>
 * <li>{@code ItemStack.matches(a,b)}（1.20：count + item + NBT）→ 本类的私有
 * {@link #matches(ItemStack, ItemStack)}，语义逐条对齐（1.7.10 没有 {@code matches}，
 * {@code areItemStacksEqual} 只比 item+damage，不够）；</li>
 * <li>{@code player.tickCount} → {@code player.ticksExisted}；</li>
 * <li>{@code TickEvent.ClientTickEvent} 与上游同名同语义，1.7.10 注册在
 * {@code FMLCommonHandler.instance().bus()}；{@code ClientPlayerNetworkEvent.LoggingOut} →
 * {@link FMLNetworkEvent.ClientDisconnectionFromServerEvent}（同一个 FML 总线）；
 * {@code SwapItemWithOffHand} 是我们的 api 事件，post 在 {@code MinecraftForge.EVENT_BUS}
 * （见 {@code network/message/ServerMessageSwapItem}），所以本类<b>两条总线都要注册</b>。</li>
 * </ul>
 *
 * <p>
 * <b>1.7.10 特有（相对上游）</b>：{@code @SubscribeEvent} 的<b>处理方法必须是实例方法</b> —— FML 的
 * {@code ASMEventHandler}（{@code cpw/mods/fml/common/eventhandler/ASMEventHandler.java:115}）用
 * {@code INVOKEVIRTUAL} 回调目标方法，静态方法会在事件派发时抛 {@code IncompatibleClassChangeError}。
 * 因此上游的 {@code public static void 处理方法(...)} 在这里改为 {@code public void 处理方法(...)}
 * （类名 / 方法名 / 参数 / 语义一律不变；非处理方法的静态工具方法与字段仍保持 {@code static}）。
 * 本类也因为「{@code EventBus#register(Class)} 在 1.7.10 无效」而提供 public 无参构造，
 * 由 {@code client/ClientProxy} 反射 {@code new} 后注册到正确的总线。
 */
public class InventoryEvent {

    private static final int HOTBAR_WARM_UP_INTERVAL_TICKS = 7;
    private static final int BACKPACK_WARM_UP_INTERVAL_TICKS = 41;

    // 用于切枪逻辑
    private static int oldHotbarSelected = -1;
    /** 1.7.10 用 null 表示空栈（等价上游的 {@code ItemStack.EMPTY}）。 */
    private static ItemStack oldHotbarSelectItem = null;

    @SubscribeEvent
    public void onPlayerChangeSelect(TickEvent.ClientTickEvent event) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        InventoryPlayer inventory = player.inventory;
        // 玩家切换选中框的情况
        if (oldHotbarSelected != inventory.currentItem) {
            ClientIndexManager.warmUpItem(inventory.getStackInSlot(inventory.currentItem));
            if (oldHotbarSelected == -1) {
                IClientPlayerGunOperator.fromLocalPlayer(player)
                    .draw(null);
            } else {
                IClientPlayerGunOperator.fromLocalPlayer(player)
                    .draw(inventory.getStackInSlot(oldHotbarSelected));
            }
            oldHotbarSelected = inventory.currentItem;
            ItemStack selected = inventory.getStackInSlot(inventory.currentItem);
            oldHotbarSelectItem = selected == null ? null : selected.copy();
            return;
        }
        // 玩家选中的物品改变的情况
        ItemStack currentItem = inventory.getStackInSlot(inventory.currentItem);
        if (currentItem != null && currentItem.getItem() instanceof IAnimationItem item) {
            if (!item.isSame(oldHotbarSelectItem, currentItem)) {
                IClientPlayerGunOperator.fromLocalPlayer(player)
                    .draw(oldHotbarSelectItem);
            }
        } else {
            if (!matches(oldHotbarSelectItem, currentItem)) {
                IClientPlayerGunOperator.fromLocalPlayer(player)
                    .draw(oldHotbarSelectItem);
            }
        }

        if (!matches(oldHotbarSelectItem, currentItem)) {
            oldHotbarSelectItem = currentItem == null ? null : currentItem.copy();
        }
        if (event.phase == TickEvent.Phase.END) {
            if (player.ticksExisted % HOTBAR_WARM_UP_INTERVAL_TICKS == 0) {
                ClientIndexManager.warmUpEquippedAndHotbarModels();
            }
            if (player.ticksExisted % BACKPACK_WARM_UP_INTERVAL_TICKS == 0) {
                ClientIndexManager.warmUpBackpackModels();
            }
        }
    }

    @SubscribeEvent
    public void onPlayerSwapMainHand(SwapItemWithOffHand event) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (player == null) {
            return;
        }
        IClientPlayerGunOperator.fromLocalPlayer(player)
            .draw(player.getHeldItem());
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        // 离开游戏时重置客户端 draw 状态
        oldHotbarSelected = -1;
        oldHotbarSelectItem = null;
    }

    private static boolean isSame(ItemStack i, ItemStack j) {
        IGun iGun1 = IGun.getIGunOrNull(i);
        IGun iGun2 = IGun.getIGunOrNull(j);
        if (iGun1 != null && iGun2 != null) {
            return iGun1.getGunId(i)
                .equals(iGun2.getGunId(j));
        }
        boolean iEmpty = i == null;
        boolean jEmpty = j == null;
        if (iEmpty || jEmpty) {
            return iEmpty && jEmpty;
        }
        return matches(i, j);
    }

    /**
     * 1.20 {@code ItemStack.matches(a, b)} 的 1.7.10 等价实现：
     * 同一引用 / 双方都是空栈 → true；数量相同 + 物品与 metadata 相同 + NBT 相同 → true。
     */
    private static boolean matches(ItemStack a, ItemStack b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.stackSize == b.stackSize && ItemStack.areItemStacksEqual(a, b)
            && ItemStack.areItemStackTagsEqual(a, b);
    }
}
