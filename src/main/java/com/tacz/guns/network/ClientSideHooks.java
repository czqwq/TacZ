package com.tacz.guns.network;

import net.minecraft.entity.Entity;

import com.tacz.guns.network.message.ServerMessageAmmoStorageContents;
import com.tacz.guns.network.message.ServerMessageBulletHitBlock;
import com.tacz.guns.network.message.ServerMessageCraft;
import com.tacz.guns.network.message.ServerMessageGunUnload;
import com.tacz.guns.network.message.ServerMessageRefreshRefitScreen;
import com.tacz.guns.network.message.ServerMessageSound;
import com.tacz.guns.network.message.ServerMessageSwapItem;
import com.tacz.guns.network.message.ServerMessageSyncBaseTimestamp;
import com.tacz.guns.network.message.ServerMessageSyncGunPack;

/**
 * 客户端钩子 —— 1.7.10 版「common 代码触发客户端行为」的统一出口。
 *
 * <p>
 * 为什么需要它（1.7.10 移植架构）：
 * <ul>
 * <li>上游（1.20.1）用 {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)} 或 {@code @OnlyIn(Dist.CLIENT)}
 * 直接在消息处理器里引用 {@code Minecraft}/{@code SoundPlayManager}/{@code GunRefitScreen} 等客户端类。</li>
 * <li>本工程有硬约束：<b>common 代码不允许编译期依赖 client 代码</b>（客户端包未完成时会被整体排除出编译）。</li>
 * <li>因此这里定义一个只有 common 类型（{@code Entity} 与 network 包自己的消息类）的钩子接口，
 * 由 {@code com.tacz.guns.client.ClientProxy} 在客户端 {@code preInit} 时通过
 * {@link #set(Impl)} 注入真实实现；专用服务器上保持默认空实现（所有方法直接返回）。</li>
 * </ul>
 *
 * <p>
 * 这等价于上游的 {@code DistExecutor} 分端分发，差异是「注入式」而不是「类加载期判定」：
 * 未注入时静默不做事（而不是抛异常），与上游在服务端不执行客户端分支的语义一致。
 */
public final class ClientSideHooks {

    /**
     * 客户端实现。所有方法都有空默认实现，客户端只需覆写自己关心的部分（Java 8 default 方法）。
     */
    public interface Impl {

        /**
         * 按实体 id 在<b>客户端世界</b>里查实体（上游 {@code ClientLevel#getEntity(int)}）。
         */
        default Entity getEntityById(int id) {
            return null;
        }

        default void playSound(ServerMessageSound message) {}

        default void bulletHitBlock(ServerMessageBulletHitBlock message) {}

        default void craft(ServerMessageCraft message) {}

        default void gunUnload(ServerMessageGunUnload message) {}

        default void refreshRefitScreen(ServerMessageRefreshRefitScreen message) {}

        default void syncBaseTimestamp(ServerMessageSyncBaseTimestamp message) {}

        /**
         * 客户端本地设置 base timestamp（上游 {@code LocalPlayerDataHolder#clientBaseTimestamp}）。
         */
        default void updateBaseTimestamp(long timestamp) {}

        /**
         * 更新弹药存储界面的内容（1.7.10 对应 {@code Minecraft.currentScreen} + {@code Container}）。
         */
        default void updateAmmoStorage(ServerMessageAmmoStorageContents message) {}

        default void syncGunPack(ServerMessageSyncGunPack message) {}

        default void swapItemWithOffHand(ServerMessageSwapItem message) {}
    }

    private static volatile Impl impl = new Impl() {};

    private ClientSideHooks() {}

    public static void set(Impl implementation) {
        impl = implementation == null ? new Impl() {} : implementation;
    }

    public static Impl get() {
        return impl;
    }
}
