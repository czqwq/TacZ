package com.tacz.guns.event;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.config.common.GunConfig;
import com.tacz.guns.item.ModernKineticGunScriptAPI;
import com.tacz.guns.resource.pojo.data.gun.FeedType;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/**
 * 玩家重生后自动给背包里的枪装满弹（可选，{@code GunConfig.AUTO_RELOAD_WHEN_RESPAWN}）。
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/event/PlayerRespawnEvent.java}
 * <p>
 * 1.7.10 差异：
 * <ul>
 * <li>{@code @Mod.EventBusSubscriber} → {@link #register()}，且注册到 <b>FML 事件总线</b>：
 * {@code net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent} →
 * {@link PlayerEvent.PlayerRespawnEvent}（{@code cpw.mods.fml.common.gameevent.PlayerEvent}），
 * 它在 1.7.10 由 {@code FMLCommonHandler.instance().bus()} 发出；</li>
 * <li>{@code event.getEntity()} → 字段 {@code event.player}（FML PlayerEvent 只有 {@code player} 字段）；</li>
 * <li>{@code player.getInventory().items}（{@code NonNullList}，含 EMPTY）→
 * {@code player.inventory.mainInventory}（{@code ItemStack[]}，空位为 {@code null}）；</li>
 * <li>{@code player.isCreative()} → {@code player.capabilities.isCreativeMode}；</li>
 * <li>{@code ItemStack.EMPTY} → {@code null} 判空。</li>
 * </ul>
 */
public class PlayerRespawnEvent {

    public static void register() {
        FMLCommonHandler.instance()
            .bus()
            .register(new PlayerRespawnEvent());
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // 重生自动换弹
        if (!GunConfig.AUTO_RELOAD_WHEN_RESPAWN.get()) {
            return;
        }
        EntityPlayer player = event.player;
        for (ItemStack itemStack : player.inventory.mainInventory) {
            if (itemStack == null || itemStack.getItem() == null || !(itemStack.getItem() instanceof IGun)) {
                continue;
            }

            ModernKineticGunScriptAPI api = new ModernKineticGunScriptAPI();
            api.setItemStack(itemStack);
            api.setShooter(player);

            // 1.7.10 硬崩溃防护：ModernKineticGunScriptAPI#getGunIndex() 在枪械数据未加载/未知 gunId 时返回
            // null（见该类 initGunItem：TimelessAPI.getCommonGunIndex(gunId).orElse(null)），
            // 紧接着的 .getGunData() 会 NPE。上游 1.20.1 同处代码同样未判空（PlayerRespawnEvent.java:25-29），
            // 但上游 IGun 物品必然有索引；本移植在枪包加载失败/自定义枪包缺失时会出现「有物品、无索引」，
            // 因此这里跳过该物品（不改变索引存在时的行为）。
            if (api.getGunIndex() == null) {
                continue;
            }

            // 针对背包直读特殊处理
            boolean useInventoryAmmo = api.getGunIndex()
                .getGunData()
                .getReloadData()
                .getType() == FeedType.INVENTORY;
            // 如果为背包直读则不进行换弹
            if (useInventoryAmmo) {
                continue;
            }

            // 针对燃料类型特殊处理
            boolean isFuel = api.getGunIndex()
                .getGunData()
                .getReloadData()
                .getType() == FeedType.FUEL;
            int needAmmoCount = api.getNeededAmmoAmount();

            if (player.capabilities.isCreativeMode) {
                api.putAmmoInMagazine(needAmmoCount);
            } else {
                int consumedAmount = api.consumeAmmoFromPlayer(isFuel ? 1 : needAmmoCount);
                api.putAmmoInMagazine(isFuel ? (needAmmoCount * consumedAmount) : consumedAmount);
            }
        }
    }
}
