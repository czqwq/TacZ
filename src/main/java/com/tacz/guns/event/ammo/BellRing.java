package com.tacz.guns.event.ammo;

import net.minecraftforge.common.MinecraftForge;

import com.tacz.guns.api.event.server.AmmoHitBlockEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * 子弹击中钟方块时敲响它。
 * <p>
 * 上游：{@code tmp/TACZ-1.20.1/src/main/java/com/tacz/guns/event/ammo/BellRing.java}
 * <p>
 * 1.7.10 差异（<b>降级为空实现</b>）：{@code net.minecraft.world.level.block.BellBlock}（钟）是 1.14 才加入
 * 原版的方块，1.7.10 完全没有，因此没有「敲钟」可做：
 * <ul>
 * <li>保留同名监听器并注册，保证事件分发点一致、后续有等价方块时可接回；</li>
 * <li>{@code event.getHitResult()} 在 1.7.10 返回 {@code net.minecraft.util.MovingObjectPosition}
 * （= {@code BlockHitResult} 的等价物，含命中面/坐标/精确命中点）；{@code getState()} 返回 {@code Block}
 * 而非 {@code BlockState}（metadata 由 {@code getMetadata()} 平台附加 getter 提供）。</li>
 * </ul>
 * <p>
 * TODO(port): 上游 BellRing.java:18-20 通过 {@code BellBlock#attemptToRing(level, pos, direction)} 敲钟。
 * 1.7.10 无钟方块；若将来有模组提供等价方块，应在此按方块类型判定并调用其语义（例如
 * {@code world.playSoundEffect(x,y,z, "random.anvil_land", ...)} 之类的近似）。
 */
public class BellRing {

    public static void register() {
        MinecraftForge.EVENT_BUS.register(new BellRing());
    }

    @SubscribeEvent
    public void onAmmoHitBlock(AmmoHitBlockEvent event) {
        // 1.7.10 无 BellBlock（1.14+ 方块），此处降级为无操作（见类注释 TODO）。
    }
}
